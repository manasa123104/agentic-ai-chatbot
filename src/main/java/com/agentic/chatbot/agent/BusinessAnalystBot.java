package com.agentic.chatbot.agent;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.SystemPromptService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Bot 1 — Business Analyst: turns end-user prompts into structured user stories.
 */
@Component
public class BusinessAnalystBot implements AgentBot {

    private final LlmClient llmClient;
    private final SystemPromptService systemPromptService;

    public BusinessAnalystBot(LlmClient llmClient, SystemPromptService systemPromptService) {
        this.llmClient = llmClient;
        this.systemPromptService = systemPromptService;
    }

    @Override
    public String name() {
        return "Bot 1 - Business Analyst";
    }

    @Override
    public AgentResult run(AgentContext context) {
        try {
            String prompt = context.getUserStory();
            String refined = refineOffline(prompt);

            if (llmClient.isAvailable()) {
                try {
                    String system = systemPromptService.build(context.getSystemPrompt(), prompt);
                    String llmOut = llmClient.complete(
                            system + "\n\nRespond only with a refined user story and 3–6 acceptance criteria. No markdown fences.",
                            "As a Business Analyst, refine this into a development-ready user story:\n\n" + prompt);
                    if (llmOut != null && !llmOut.isBlank() && llmOut.length() > 40) {
                        refined = llmOut.trim();
                    }
                } catch (Exception ignored) {
                    // Keep offline refinement
                }
            }

            context.setRefinedStory(refined);
            Path out = context.getProjectDir().resolve("USER_STORY.md");
            Files.createDirectories(out.getParent());
            String body = "# User story (Business Analyst)\n\n"
                    + "## Original prompt\n\n" + prompt + "\n\n"
                    + "## Refined story\n\n" + refined + "\n";
            Files.writeString(out, body, StandardCharsets.UTF_8);
            context.addGeneratedFile(out);
            return AgentResult.ok(
                    "Refined the end-user prompt into a development-ready user story.",
                    List.of(out));
        } catch (Exception e) {
            return AgentResult.fail("Business Analyst bot failed: " + e.getMessage());
        }
    }

    private static String refineOffline(String prompt) {
        String p = prompt == null ? "" : prompt.trim();
        String lower = p.toLowerCase(Locale.ROOT);
        String role = "end user";
        if (lower.contains("restaurant") || lower.contains("cafe") || lower.contains("café")) {
            role = "restaurant owner";
        } else if (lower.contains("travel") || lower.contains("hotel") || lower.contains("tour")) {
            role = "traveler";
        } else if (lower.contains("music") || lower.contains("piano") || lower.contains("guitar")) {
            role = "music student / academy manager";
        } else if (lower.contains("wedding")) {
            role = "wedding couple";
        }

        return """
                As a %s, I want BOT to generate Java website code (Spring Boot + Thymeleaf) so developers can run and test it.

                ### Goal
                %s

                ### Acceptance criteria
                1. Generate Java website code: Spring Boot controllers, templates, and multi-page HTML (Home, About, Services, Pricing, Blog, Careers, Contact when applicable).
                2. Topic-relevant images and styling applied from the story keywords.
                3. Brand name shown on pages (not the raw prompt dumped as page copy).
                4. Java unit tests generated for controllers/services.
                5. Functional (MockMvc) tests cover the main happy path.
                """.formatted(role, p);
    }
}
