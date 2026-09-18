package com.agentic.chatbot.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final boolean enabled;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public OpenAiLlmClient(
            @Value("${agentic.llm.api-key:}") String apiKey,
            @Value("${agentic.llm.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${agentic.llm.model:gpt-4o-mini}") String model,
            @Value("${agentic.llm.enabled:true}") boolean enabled) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.model = model;
        this.enabled = enabled;
    }

    @Override
    public boolean isAvailable() {
        return enabled && !apiKey.isBlank();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!isAvailable()) {
            throw new IllegalStateException("LLM is not configured. Set OPENAI_API_KEY or use offline template mode.");
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

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.error("LLM HTTP {}: {}", response.statusCode(), response.body());
                throw new IllegalStateException("LLM request failed with HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            throw new IllegalStateException("LLM call failed: " + e.getMessage(), e);
        }
    }
}
