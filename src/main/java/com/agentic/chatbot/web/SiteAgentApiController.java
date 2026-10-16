package com.agentic.chatbot.web;

import com.agentic.chatbot.generator.SiteAgent;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.SiteStyleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backend of the chat widget on generated websites (only added when the prompt asked for a chatbot / agent).
 *
 * <p>It behaves like ChatGPT for that business: the visitor's earlier messages are sent along, the connected model
 * (Gemini / Ollama / OpenAI) answers with a system prompt grounded in {@code SITE_AGENT.json}, and the answer is
 * streamed back piece by piece ({@code /api/site-agent/stream}, newline-delimited JSON). Without a model the
 * built-in rules answer. {@code /api/site-agent} is the same thing as one plain JSON reply.
 */
@RestController
public class SiteAgentApiController {

    private static final Logger log = LoggerFactory.getLogger(SiteAgentApiController.class);
    private static final int MAX_MESSAGE = 1000;
    private static final int PER_MINUTE = 30;
    private static final int MAX_ANSWER_TOKENS = 500;
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(60);

    private final SiteStyleService styleService;
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public SiteAgentApiController(SiteStyleService styleService, LlmClient llm) {
        this.styleService = styleService;
        this.llm = llm;
    }

    // ------------------------------------------------------------------ plain JSON reply

    @PostMapping("/api/site-agent")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String message = message(body);
        if (tooMany(request.getRemoteAddr())) {
            return ResponseEntity.status(429).body(Map.of("reply", "Easy there - give me a moment and ask again."));
        }
        SiteAgent.Knowledge knowledge = load();
        if (knowledge == null) {
            return ResponseEntity.ok(Map.of(
                    "reply", "No website agent has been generated yet. Ask BOT for a site with a chatbot first.",
                    "actions", List.of(), "suggestions", List.of(), "mode", "none"));
        }
        SiteAgent.Reply rules = SiteAgent.reply(knowledge, message);
        String text = rules.text();
        String mode = "rules";
        if (!message.isBlank() && llm.isAvailable()) {
            try {
                String answer = llm.chat(SiteAgent.systemPrompt(knowledge), turns(body, message),
                        ANSWER_TIMEOUT, MAX_ANSWER_TOKENS, null);
                if (answer != null && answer.strip().length() > 3) {
                    text = answer.strip();
                    mode = "llm";
                }
            } catch (Exception e) {
                log.debug("Site agent LLM call failed, using rules: {}", e.toString());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reply", text);
        out.put("actions", "llm".equals(mode) ? mentionedPages(knowledge, text) : rules.actions());
        out.put("suggestions", rules.suggestions());
        out.put("agent", knowledge.agentName());
        out.put("mode", mode);
        return ResponseEntity.ok(out);
    }

    // ------------------------------------------------------------------ streamed reply (ChatGPT-style)

    /**
     * Newline-delimited JSON events: {@code {"type":"meta","suggestions":[..]}}, then {@code {"type":"delta","text":".."}}
     * for each piece of the answer, then {@code {"type":"done","mode":"llm|rules","actions":[..]}}.
     */
    @PostMapping(value = "/api/site-agent/stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String message = message(body);
        boolean limited = tooMany(request.getRemoteAddr());
        SiteAgent.Knowledge knowledge = limited ? null : load();
        List<LlmClient.ChatTurn> turns = turns(body, message);

        StreamingResponseBody out = os -> {
            Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            if (limited) {
                say(w, "Easy there - give me a moment and ask again.", "none", List.of());
                return;
            }
            if (knowledge == null) {
                say(w, "No website agent has been generated yet. Ask BOT for a site with a chatbot first.", "none", List.of());
                return;
            }
            SiteAgent.Reply rules = SiteAgent.reply(knowledge, message);
            emit(w, Map.of("type", "meta", "suggestions", rules.suggestions(), "agent", knowledge.agentName()));
            if (message.isBlank() || !llm.isAvailable()) {
                say(w, rules.text(), "rules", rules.actions());
                return;
            }
            StringBuilder sent = new StringBuilder();
            try {
                llm.chat(SiteAgent.systemPrompt(knowledge), turns, ANSWER_TIMEOUT, MAX_ANSWER_TOKENS, piece -> {
                    sent.append(piece);
                    try {
                        emit(w, Map.of("type", "delta", "text", piece));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e); // the visitor closed the chat: stop generating
                    }
                });
            } catch (UncheckedIOException e) {
                return;
            } catch (Exception e) {
                log.debug("Site agent LLM stream failed: {}", e.toString());
            }
            if (sent.toString().strip().length() < 2) {
                say(w, rules.text(), "rules", rules.actions());
                return;
            }
            emit(w, Map.of("type", "done", "mode", "llm", "actions", mentionedPages(knowledge, sent.toString())));
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/x-ndjson")).body(out);
    }

    // ------------------------------------------------------------------ helpers

    private static void say(Writer w, String text, String mode, List<SiteAgent.Action> actions) throws IOException {
        emit(w, Map.of("type", "delta", "text", text));
        emit(w, Map.of("type", "done", "mode", mode, "actions", actions));
    }

    private static final ObjectMapper LINES = new ObjectMapper();

    private static void emit(Writer w, Map<String, Object> event) throws IOException {
        w.write(LINES.writeValueAsString(event));
        w.write('\n');
        w.flush();
    }

    private static String message(Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", "")).trim();
        return message.length() > MAX_MESSAGE ? message.substring(0, MAX_MESSAGE) : message;
    }

    /** Earlier turns sent by the widget, plus the new message (see {@link SiteAgent#turns}). */
    private static List<LlmClient.ChatTurn> turns(Map<String, Object> body, String message) {
        List<LlmClient.ChatTurn> history = new ArrayList<>();
        if (body.get("history") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    Object role = m.get("role");
                    Object content = m.get("content");
                    if (role != null && content != null) {
                        history.add(new LlmClient.ChatTurn(String.valueOf(role), String.valueOf(content)));
                    }
                }
            }
        }
        return SiteAgent.turns(history, message);
    }

    /** Pages (with the site's own menu names) that the model's answer points to, so they become buttons. */
    private static List<SiteAgent.Action> mentionedPages(SiteAgent.Knowledge k, String answer) {
        String a = answer.toLowerCase(Locale.ROOT);
        List<SiteAgent.Action> out = new ArrayList<>();
        for (String slug : List.of("about", "services", "pricing", "blog", "careers", "contact")) {
            String label = k.pageLabel(slug);
            if (a.contains("/site/" + slug) || (label.length() >= 6 && a.contains(label.toLowerCase(Locale.ROOT)))) {
                out.add(new SiteAgent.Action(label, "/site/" + slug));
            }
            if (out.size() == 3) {
                break;
            }
        }
        return out;
    }

    private boolean tooMany(String ip) {
        long now = System.currentTimeMillis();
        long[] w = windows.computeIfAbsent(ip == null ? "?" : ip, k -> new long[]{now, 0});
        synchronized (w) {
            if (now - w[0] > 60_000) {
                w[0] = now;
                w[1] = 0;
            }
            return ++w[1] > PER_MINUTE;
        }
    }

    private SiteAgent.Knowledge load() {
        try {
            Path file = styleService.outputDir().resolve("SITE_AGENT.json");
            if (!Files.exists(file)) {
                return null;
            }
            JsonNode n = mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
            List<String> titles = new ArrayList<>();
            List<String> blurbs = new ArrayList<>();
            List<String> links = new ArrayList<>();
            for (JsonNode s : n.path("services")) {
                titles.add(s.path("title").asText());
                blurbs.add(s.path("blurb").asText());
                links.add(s.path("link").asText("/site/services"));
            }
            List<SiteAgent.Action> pages = new ArrayList<>();
            for (JsonNode p : n.path("pages")) {
                pages.add(new SiteAgent.Action(p.path("label").asText(), p.path("href").asText()));
            }
            return new SiteAgent.Knowledge(
                    n.path("brand").asText("this business"),
                    n.path("agentName").asText("Assistant"),
                    n.path("kind").asText("business"),
                    n.path("lead").asText(""),
                    titles, blurbs, links, pages);
        } catch (Exception e) {
            log.debug("Could not read SITE_AGENT.json: {}", e.toString());
            return null;
        }
    }
}
