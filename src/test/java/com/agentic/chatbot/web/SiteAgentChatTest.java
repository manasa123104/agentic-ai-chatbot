package com.agentic.chatbot.web;

import com.agentic.chatbot.generator.SiteAgent;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.SiteStyleService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteAgentChatTest {

    /** A model that streams its answer in two pieces and remembers what it was sent. */
    private static final class StreamingModel implements LlmClient {
        String system;
        List<ChatTurn> turns = new ArrayList<>();
        boolean available = true;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new IllegalStateException("chat() should be used");
        }

        @Override
        public String chat(String systemPrompt, List<ChatTurn> t, Duration timeout, int maxTokens, Consumer<String> onChunk) {
            this.system = systemPrompt;
            this.turns = t;
            onChunk.accept("Yes, we take ");
            onChunk.accept("bookings - see /site/contact.");
            return "Yes, we take bookings - see /site/contact.";
        }
    }

    private static SiteAgentApiController controller(Path dir, LlmClient llm) throws Exception {
        var k = new SiteAgent.Knowledge("OliveGrove", "OliveGrove Host", "restaurant", "Seasonal plates in Lisbon.",
                List.of("Seasonal menu", "Private dining"), List.of("Changes monthly.", "Up to 20 guests."),
                List.of("/site/services", "/site/services"),
                List.of(new SiteAgent.Action("Reservations", "/site/pricing"),
                        new SiteAgent.Action("Find Us", "/site/contact")));
        Files.writeString(dir.resolve("SITE_AGENT.json"), SiteAgent.toJson(k), StandardCharsets.UTF_8);
        return new SiteAgentApiController(new SiteStyleService(dir.toString()), llm);
    }

    private static List<String> lines(SiteAgentApiController c, Map<String, Object> body, String ip) throws Exception {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(ip);
        var response = c.stream(body, request);
        var out = new ByteArrayOutputStream();
        response.getBody().writeTo(out);
        return List.of(out.toString(StandardCharsets.UTF_8).split("\n"));
    }

    @Test
    void streamsTheModelAnswerAndSendsTheConversationSoFar(@TempDir Path dir) throws Exception {
        var model = new StreamingModel();
        var c = controller(dir, model);
        var body = Map.<String, Object>of(
                "message", "Can I book it for Friday?",
                "history", List.of(
                        Map.of("role", "user", "content", "Do you have private dining?"),
                        Map.of("role", "assistant", "content", "Yes, for up to 20 guests.")));
        List<String> events = lines(c, body, "10.0.0.1");

        assertTrue(events.get(0).contains("\"type\":\"meta\""), events.toString());
        assertEquals(2, events.stream().filter(l -> l.contains("\"type\":\"delta\"")).count(), events.toString());
        String done = events.get(events.size() - 1);
        assertTrue(done.contains("\"type\":\"done\"") && done.contains("\"mode\":\"llm\""), done);
        assertTrue(done.contains("/site/contact"), "page named in the answer becomes a button: " + done);

        // multi-turn: earlier messages first, the new one last
        assertEquals(3, model.turns.size());
        assertEquals("user", model.turns.get(0).role());
        assertEquals("assistant", model.turns.get(1).role());
        assertEquals("Can I book it for Friday?", model.turns.get(2).content());
        // grounded in the business and in the site's own page names
        assertTrue(model.system.contains("OliveGrove"));
        assertTrue(model.system.contains("Reservations (/site/pricing)"));
        assertTrue(model.system.contains("ChatGPT"));
    }

    @Test
    void fallsBackToTheRulesWhenThereIsNoModel(@TempDir Path dir) throws Exception {
        var model = new StreamingModel();
        model.available = false;
        var c = controller(dir, model);
        List<String> events = lines(c, Map.of("message", "how much does it cost?"), "10.0.0.2");
        String all = String.join("\n", events);
        assertTrue(all.contains("\"mode\":\"rules\""), all);
        assertTrue(all.contains("Reservations"), all);
    }

    @Test
    void defaultChatFlattensTheConversationForSinglePromptModels() {
        var seen = new String[1];
        LlmClient plain = new LlmClient() {
            public boolean isAvailable() {
                return true;
            }

            public String complete(String systemPrompt, String userPrompt) {
                seen[0] = userPrompt;
                return "Sure.";
            }
        };
        var chunks = new ArrayList<String>();
        String answer = plain.chat("sys", List.of(
                        new LlmClient.ChatTurn("user", "hi"),
                        new LlmClient.ChatTurn("assistant", "hello"),
                        new LlmClient.ChatTurn("user", "and prices?")),
                Duration.ofSeconds(5), 100, chunks::add);
        assertEquals("Sure.", answer);
        assertEquals(List.of("Sure."), chunks);
        assertTrue(seen[0].contains("Visitor: hi") && seen[0].contains("Assistant: hello")
                && seen[0].endsWith("Visitor: and prices?\nAssistant:"), seen[0]);
    }
}
