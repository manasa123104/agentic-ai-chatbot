package com.agentic.chatbot.llm;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiLlmClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /** Starts a fake Gemini endpoint; returns its base URL. Nothing here calls the real API. */
    private String fakeGemini(int status, String responseBody, AtomicReference<String> path,
                              AtomicReference<String> auth, AtomicReference<String> body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1beta/openai/";
    }

    private static OpenAiLlmClient gemini(String key, String baseUrl) {
        return new OpenAiLlmClient("sk-openai", "https://api.openai.com/v1", "gpt-4o-mini", true,
                key, baseUrl, "gemini-2.5-flash");
    }

    @Test
    void geminiKeyWinsOverOpenAiAndShowsInBanner() {
        OpenAiLlmClient client = gemini("g-key", OpenAiLlmClient.GEMINI_BASE_URL + "/");
        assertTrue(client.isAvailable());
        assertTrue(client.isCloud());
        assertEquals("Gemini (gemini-2.5-flash)", client.describe());
    }

    @Test
    void withoutGeminiKeyOpenAiIsUsed() {
        OpenAiLlmClient client = gemini("", OpenAiLlmClient.GEMINI_BASE_URL);
        assertEquals("gpt-4o-mini", client.describe());
        assertTrue(client.isCloud());
        assertFalse(new OpenAiLlmClient("", "http://localhost:11434/v1", "llama3.1", true).isCloud());
    }

    @Test
    void geminiRequestUsesChatCompletionsPathBearerKeyAndModel() throws Exception {
        var path = new AtomicReference<String>();
        var auth = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        String url = fakeGemini(200, "{\"choices\":[{\"message\":{\"content\":\"hello from gemini\"}}]}",
                path, auth, body);
        String answer = gemini("g-key", url).complete("system", "user");
        assertEquals("hello from gemini", answer);
        assertEquals("/v1beta/openai/chat/completions", path.get());
        assertEquals("Bearer g-key", auth.get());
        assertTrue(body.get().contains("\"model\":\"gemini-2.5-flash\""));
    }

    @Test
    void quotaErrorGivesAClearMessage() throws Exception {
        String url = fakeGemini(429, "{\"error\":{\"message\":\"RESOURCE_EXHAUSTED\"}}",
                new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> gemini("g-key", url).complete("system", "user"));
        assertEquals("Free AI quota reached – try again later", e.getMessage());
    }

    @Test
    void otherHttpErrorsAreReported() throws Exception {
        String url = fakeGemini(500, "{}", new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> gemini("g-key", url).complete("system", "user"));
        assertTrue(e.getMessage().contains("HTTP 500"));
    }

    @Test
    void localEndpointsWorkWithoutAnApiKey() {
        assertTrue(new OpenAiLlmClient("", "http://localhost:11434/v1", "llama3.1", true).isAvailable());
        assertTrue(new OpenAiLlmClient("", "http://127.0.0.1:1234/v1", "local", true).isAvailable());
    }

    @Test
    void hostedEndpointsNeedAnApiKey() {
        assertFalse(new OpenAiLlmClient("", "https://api.openai.com/v1", "gpt-4o-mini", true).isAvailable());
        assertTrue(new OpenAiLlmClient("sk-test", "https://api.openai.com/v1", "gpt-4o-mini", true).isAvailable());
        assertFalse(new OpenAiLlmClient("sk-test", "https://api.openai.com/v1", "gpt-4o-mini", false).isAvailable());
    }
}
