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
    /** Tried in order when Google answers 404 for the configured model (retired, or closed to new projects). */
    static final java.util.List<String> GEMINI_FALLBACK_MODELS = java.util.List.of(
            "gemini-3.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite");
    public static final String QUOTA_MESSAGE = "Free AI quota reached – try again later";

    private final String apiKey;
    private final String baseUrl;
    private volatile String model;
    private final boolean gemini;
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
        this.gemini = gemini;
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
        return gemini ? "Gemini (" + model + ")" : label;
    }

    @Override
    public boolean isCloud() {
        return cloud;
    }

    /** Gemini models and the OpenAI "omni" / vision families take pictures; small local models usually do not. */
    @Override
    public boolean supportsVision() {
        if (!isAvailable()) {
            return false;
        }
        if (gemini) {
            return true;
        }
        String m = model == null ? "" : model.toLowerCase(java.util.Locale.ROOT);
        return !isLocalEndpoint(baseUrl)
                && (m.contains("gpt-4o") || m.contains("gpt-4.1") || m.contains("gpt-5") || m.contains("vision")
                || m.contains("gemini") || m.contains("claude"));
    }

    /** One non-streaming call with the pictures as OpenAI-style {@code image_url} parts (data URIs). */
    @Override
    public String describeImages(String systemPrompt, String prompt, java.util.List<ImageInput> images,
                                 Duration callTimeout, int maxTokens) {
        if (!supportsVision()) {
            throw new UnsupportedOperationException("this model cannot look at images");
        }
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("temperature", 0.3);
            if (maxTokens > 0 && maxTokens < Integer.MAX_VALUE) {
                body.put("max_tokens", maxTokens);
            }
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            ObjectNode user = messages.addObject();
            user.put("role", "user");
            ArrayNode parts = user.putArray("content");
            parts.addObject().put("type", "text").put("text", prompt);
            for (ImageInput image : images) {
                ObjectNode part = parts.addObject();
                part.put("type", "image_url");
                part.putObject("image_url").put("url", "data:" + image.mimeType() + ";base64," + image.base64());
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(callTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new QuotaExceededException();
            }
            if (response.statusCode() >= 400) {
                log.warn("LLM vision HTTP {}: {}", response.statusCode(), shorten(response.body()));
                throw new IllegalStateException("LLM vision request failed with HTTP " + response.statusCode());
            }
            return mapper.readTree(response.body()).path("choices").path(0).path("message").path("content").asText("");
        } catch (QuotaExceededException | UnsupportedOperationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("LLM vision call failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM is not configured. Set GEMINI_API_KEY or OPENAI_API_KEY, or use offline template mode.");
        }
        try {
            HttpResponse<String> response = send(model, systemPrompt, userPrompt);
            if (response.statusCode() == 404 && gemini) {
                log.error("Gemini model {} returned 404: {}", model, response.body());
                for (String candidate : GEMINI_FALLBACK_MODELS) {
                    if (candidate.equals(model)) {
                        continue;
                    }
                    HttpResponse<String> retry = send(candidate, systemPrompt, userPrompt);
                    if (retry.statusCode() != 404) {
                        log.warn("Switching Gemini model {} -> {}", model, candidate);
                        model = candidate;
                        response = retry;
                        break;
                    }
                }
            }
            if (response.statusCode() == 429) {
                log.warn("LLM quota/rate limit (HTTP 429): {}", response.body());
                throw new QuotaExceededException();
            }
            if (response.statusCode() >= 400) {
                log.error("LLM HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("LLM request failed with HTTP " + response.statusCode() + ": "
                        + shorten(response.body()));
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


    /**
     * Multi-turn chat over the OpenAI-compatible streaming API (Gemini, OpenAI, LM Studio...). Pieces of the answer go
     * to {@code onChunk} as they arrive. If the provider will not stream (404 model fallback, odd errors) the
     * non-streaming path, which knows how to recover, answers instead.
     */
    @Override
    public String chat(String systemPrompt, java.util.List<ChatTurn> turns, Duration callTimeout, int maxTokens,
                       java.util.function.Consumer<String> onChunk) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM is not configured. Set GEMINI_API_KEY or OPENAI_API_KEY.");
        }
        long deadline = System.nanoTime() + callTimeout.toNanos();
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", true);
            body.put("temperature", 0.6);
            if (maxTokens > 0 && maxTokens < Integer.MAX_VALUE) {
                body.put("max_tokens", maxTokens);
            }
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            for (ChatTurn t : turns) {
                messages.addObject().put("role", "assistant".equals(t.role()) ? "assistant" : "user")
                        .put("content", t.content());
            }
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(callTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!apiKey.isBlank()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<java.util.stream.Stream<String>> response =
                    http.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() == 429) {
                response.body().close();
                throw new QuotaExceededException();
            }
            if (response.statusCode() >= 400) {
                String text = response.body().collect(java.util.stream.Collectors.joining(" "));
                log.warn("LLM streaming HTTP {}: {}", response.statusCode(), shorten(text));
                return LlmClient.super.chat(systemPrompt, turns, callTimeout, maxTokens, onChunk);
            }
            StringBuilder all = new StringBuilder();
            try (java.util.stream.Stream<String> lines = response.body()) {
                java.util.Iterator<String> it = lines.iterator();
                while (it.hasNext()) {
                    String line = it.next().trim();
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String payload = line.substring(5).trim();
                    if (payload.equals("[DONE]")) {
                        break;
                    }
                    JsonNode n = mapper.readTree(payload);
                    String piece = n.path("choices").path(0).path("delta").path("content").asText("");
                    if (!piece.isEmpty()) {
                        all.append(piece);
                        if (onChunk != null) {
                            onChunk.accept(piece);
                        }
                    }
                    if (System.nanoTime() > deadline) {
                        break;
                    }
                }
            }
            return all.toString();
        } catch (QuotaExceededException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("LLM call interrupted", e);
        } catch (Exception e) {
            throw new IllegalStateException("LLM call failed: " + e.getMessage(), e);
        }
    }

    private HttpResponse<String> send(String useModel, String systemPrompt, String userPrompt) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", useModel);
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
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String shorten(String s) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) : t;
    }

    /** Thrown on HTTP 429 so the UI can show a friendly message instead of raw API output. */
    public static class QuotaExceededException extends IllegalStateException {
        public QuotaExceededException() {
            super(QUOTA_MESSAGE);
        }
    }
}