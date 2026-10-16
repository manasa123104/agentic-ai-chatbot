package com.agentic.chatbot.web;

import com.agentic.chatbot.generator.SiteAgent;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.SiteStyleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backend of the chatbot widget on generated websites (only added when the prompt asked for a chatbot / agent).
 * Uses the connected LLM when there is one, grounded in {@code SITE_AGENT.json}; otherwise the built-in rules.
 */
@RestController
public class SiteAgentApiController {

    private static final Logger log = LoggerFactory.getLogger(SiteAgentApiController.class);
    private static final int MAX_MESSAGE = 400;
    private static final int PER_MINUTE = 30;

    private final SiteStyleService styleService;
    private final LlmClient llm;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    public SiteAgentApiController(SiteStyleService styleService, LlmClient llm) {
        this.styleService = styleService;
        this.llm = llm;
    }

    @PostMapping("/api/site-agent")
    public ResponseEntity<Map<String, Object>> ask(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        String message = String.valueOf(body.getOrDefault("message", "")).trim();
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE);
        }
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
                String answer = llm.complete(SiteAgent.systemPrompt(knowledge), message, Duration.ofSeconds(20), 220);
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
        out.put("actions", rules.actions());
        out.put("suggestions", rules.suggestions());
        out.put("agent", knowledge.agentName());
        out.put("mode", mode);
        return ResponseEntity.ok(out);
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
            return new SiteAgent.Knowledge(
                    n.path("brand").asText("this business"),
                    n.path("agentName").asText("Assistant"),
                    n.path("kind").asText("business"),
                    n.path("lead").asText(""),
                    titles, blurbs, links);
        } catch (Exception e) {
            log.debug("Could not read SITE_AGENT.json: {}", e.toString());
            return null;
        }
    }
}
