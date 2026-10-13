package com.agentic.chatbot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds a running Ollama server and picks an installed model for code conversion, so the converter
 * works with a local model without changing how the website agents run. Re-checked every 30 seconds,
 * so starting Ollama or pulling a model does not require restarting BOT.
 */
@Component
public class LocalLlmDetector {

    private static final Logger log = LoggerFactory.getLogger(LocalLlmDetector.class);
    private static final Duration RECHECK_AFTER = Duration.ofSeconds(30);
    /** Larger models usually do not fit in laptop memory or answer far too slowly on CPU. */
    private static final long MAX_AUTO_MODEL_BYTES = 10L * 1024 * 1024 * 1024;

    record InstalledModel(String name, long sizeBytes) {
    }

    private final boolean enabled;
    private final String baseUrl;
    private final String preferredModel;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private Optional<LlmClient> cached = Optional.empty();
    private Instant checkedAt = Instant.EPOCH;
    private String lastLogged = "";

    public LocalLlmDetector(
            @Value("${agentic.converter.ollama.enabled:true}") boolean enabled,
            @Value("${agentic.converter.ollama.url:http://localhost:11434}") String baseUrl,
            @Value("${agentic.converter.ollama.model:}") String preferredModel,
            @Value("${agentic.converter.ollama.timeout-minutes:15}") long timeoutMinutes) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.preferredModel = preferredModel == null ? "" : preferredModel.trim();
        this.timeout = Duration.ofMinutes(Math.max(1, timeoutMinutes));
    }

    public synchronized Optional<LlmClient> client() {
        if (!enabled) {
            return Optional.empty();
        }
        if (Duration.between(checkedAt, Instant.now()).compareTo(RECHECK_AFTER) < 0) {
            return cached;
        }
        checkedAt = Instant.now();
        Optional<String> model = chooseModel(installedModels(), preferredModel);
        cached = model.map(name -> new OllamaLlmClient(baseUrl, name, timeout));
        String state = model.map(name -> "Code converter will use local Ollama model " + name)
                .orElse("No local Ollama model found at " + baseUrl + "; code converter is limited to built-in samples");
        if (!state.equals(lastLogged)) {
            log.info(state);
            lastLogged = state;
        }
        return cached;
    }

    private List<InstalledModel> installedModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return List.of();
            }
            List<InstalledModel> models = new ArrayList<>();
            for (JsonNode node : mapper.readTree(response.body()).path("models")) {
                String name = node.path("name").asText("");
                if (!name.isBlank()) {
                    models.add(new InstalledModel(name, node.path("size").asLong(0)));
                }
            }
            return models;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Uses the configured model when it is installed; otherwise prefers the largest coding model
     * (name contains "code") that fits in memory, then the largest general model that fits.
     */
    static Optional<String> chooseModel(List<InstalledModel> installed, String preferred) {
        if (installed.isEmpty()) {
            return Optional.empty();
        }
        if (preferred != null && !preferred.isBlank()) {
            String wanted = preferred.toLowerCase(Locale.ROOT);
            Optional<String> exact = installed.stream()
                    .map(InstalledModel::name)
                    .filter(name -> name.equalsIgnoreCase(wanted) || name.equalsIgnoreCase(wanted + ":latest"))
                    .findFirst();
            if (exact.isPresent()) {
                return exact;
            }
        }
        Comparator<InstalledModel> bySize = Comparator.comparingLong(InstalledModel::sizeBytes);
        List<InstalledModel> fitting = installed.stream()
                .filter(m -> m.sizeBytes() <= MAX_AUTO_MODEL_BYTES)
                .filter(m -> !m.name().toLowerCase(Locale.ROOT).contains("embed"))
                .toList();
        Optional<InstalledModel> coder = fitting.stream()
                .filter(m -> m.name().toLowerCase(Locale.ROOT).contains("code"))
                .max(bySize);
        if (coder.isPresent()) {
            return coder.map(InstalledModel::name);
        }
        return fitting.stream().max(bySize).map(InstalledModel::name);
    }
}
