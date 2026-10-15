package com.agentic.chatbot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Talks to a local Ollama server through its native chat API, which (unlike the OpenAI-compatible
 * endpoint) lets each request size the context window so long source files are not truncated.
 */
public class OllamaLlmClient implements LlmClient {

    private static final int MIN_CONTEXT = 4096;
    private static final int MAX_CONTEXT = 32768;

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public OllamaLlmClient(String baseUrl, String model, Duration timeout) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.timeout = timeout;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public String describe() {
        return model + " (local Ollama)";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        return send(systemPrompt, userPrompt, timeout, Integer.MAX_VALUE);
    }

    @Override
    public String complete(String systemPrompt, String userPrompt, Duration callTimeout, int maxTokens) {
        Duration limit = callTimeout.compareTo(timeout) < 0 ? callTimeout : timeout;
        return send(systemPrompt, userPrompt, limit, Math.max(64, maxTokens));
    }

    private String send(String systemPrompt, String userPrompt, Duration timeout, int maxTokens) {
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.put("stream", false);
            ArrayNode messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            ObjectNode options = body.putObject("options");
            options.put("temperature", 0.2);
            int promptChars = systemPrompt.length() + userPrompt.length();
            options.put("num_ctx", contextSize(promptChars));
            options.put("num_predict", Math.min(maxTokens, answerLimit(promptChars)));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/chat"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode root = mapper.readTree(response.body());
            return root.path("message").path("content").asText("");
        } catch (HttpTimeoutException e) {
            throw new IllegalStateException("the local model " + model + " did not answer within "
                    + timeout.toSeconds() + " seconds. Try a shorter snippet or a smaller model.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama call was interrupted", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Ollama call failed: " + e.getMessage(), e);
        }
    }

    /** Room for the prompt (about 3 characters per token) plus an answer of similar size. */
    static int contextSize(int promptChars) {
        int needed = (promptChars / 3) * 2 + 1024;
        int size = MIN_CONTEXT;
        while (size < needed && size < MAX_CONTEXT) {
            size *= 2;
        }
        return size;
    }

    /**
     * Upper bound on answer tokens: about the size of the prompt plus room for comments and notes. Without it a small
     * model that starts repeating itself keeps generating until the request times out.
     */
    static int answerLimit(int promptChars) {
        return Math.min(MAX_CONTEXT / 2, promptChars / 3 + 1024);
    }
}
