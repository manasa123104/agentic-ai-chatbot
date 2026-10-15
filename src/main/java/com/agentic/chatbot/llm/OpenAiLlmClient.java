package com.agentic.chatbot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    public static final String GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    public static final String QUOTA_MESSAGE = "Free AI quota reached – try again later";

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final String label;
    private final boolean cloud;
    private final boolean enabled;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * Picks the provider: GEMINI_API_KEY (Google's OpenAI-compatible endpoint) first, then OPENAI_API_KEY
     * (or any local OpenAI-compatible server). Ollama and the built-in samples are the fallbacks outside this class.
     */
    @Autowired
    public OpenAiLlmClient(
            @Value("${agentic.llm.api-key:}") String apiKey,
            @Value("${agentic.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agentic.llm.model:gpt-4o-mini}") String model,
            @Value("${agentic.llm.enabled:true}") boolean enabled,
            @Value("${agentic.llm.gemini.api-key:}") String geminiKey,
            @Value("${agentic.llm.gemini.base-url:" + GEMINI_BASE_URL + "}") String geminiBaseUrl,
            @Value("${agentic.llm.gemini.model:gemini-2.5-flash}") String geminiModel) {
        boolean gemini = geminiKey != null && !geminiKey.isBlank();
        this.apiKey = (gemini ? geminiKey : apiKey == null ? "" : apiKey).trim();
        String url = gemini ? geminiBaseUrl : baseUrl;
        this.baseUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        String chosen = gemini ? geminiModel : model;
        this.model = chosen == null || chosen.isBlank() ? (gemini ? "gemini-2.5-flash" : "gpt-4o-mini") : chosen.trim();
        this.label = gemini ? "Gemini (" + this.model + ")" : this.model;
        this.cloud = gemini || !isLocalEndpoint(this.baseUrl);
        this.enabled = enabled;
    }

    public OpenAiLlmClient(String apiKey, String baseUrl, String model, boolean enabled) {
        this(apiKey, baseUrl, model, enabled, "", GEMINI_BASE_URL, "gemini-2.5-flash");
    }

    @Override
    public boolean isAvailable() {
        return enabled && (!apiKey.isBlank() || isLocalEndpoint(baseUrl));
    }

    /** Local OpenAI-compatible servers (Ollama, LM Studio) do not need an API key. */
    private static boolean isLocalEndpoint(String baseUrl) {
        try {
            String host = URI.create(baseUrl).getHost();
            return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String describe() {
        return label;
    }

    @Override
    public boolean isCloud() {
        return cloud;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM is not configured. Set GEMINI_API_KEY or OPENAI_API_KEY, or use offline template mode.");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            ArrayNode messages = body.putArray("messages");
            ObjectNode sys = messages.addObject();
            sys.put("role", "system");
            sys.put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            user.put("content", userPrompt);
            body.put("temperature", 0.2);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpRequest request = builder.build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                log.warn("LLM quota/rate limit (HTTP 429): {}", response.body());
                throw new QuotaExceededException();
            }
            if (response.statusCode() >= 400) {
                log.error("LLM HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("LLM request failed with HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (QuotaExceededException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("LLM call failed: " + e.getMessage(), e);
        }
    }

    /** Thrown on HTTP 429 so the UI can show a friendly message instead of raw API output. */
    public static class QuotaExceededException extends IllegalStateException {
        public QuotaExceededException() {
            super(QUOTA_MESSAGE);
        }
    }
}