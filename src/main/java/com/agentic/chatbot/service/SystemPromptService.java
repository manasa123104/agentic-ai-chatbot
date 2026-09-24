package com.agentic.chatbot.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Builds a dynamic, widened system prompt for the BOT model from base rules,
 * optional end-user system prompt, and learned training examples.
 */
@Service
public class SystemPromptService {

    private static final String BASE = """
            You are BOT — a multi-agent Java website delivery assistant.
            Core output: include both (1) clear Business Analyst user stories and (2) real Java code for the website
            (Spring Boot controllers, DTOs, Thymeleaf HTML templates) under generated-app/.
            Pipeline roles (in order):
            1) Business Analyst — write / refine user stories and acceptance criteria.
            2) Java Developer — generate Java website code from those stories.
            3) Java Unit Tester — generate JUnit 5 unit tests.
            4) Functional Testing — generate MockMvc / end-to-end functional tests.
            5) Developer Tester — verify user stories + generated code are present, then train the BOT model.
            Always ship user stories AND Java website code together. Keep brand names clean. Match topic images.
            """.stripIndent().trim();

    private final BotModelTrainingService trainingService;

    public SystemPromptService(BotModelTrainingService trainingService) {
        this.trainingService = trainingService;
    }

    public String build(String userSystemPrompt, String userStory) {
        StringBuilder sb = new StringBuilder(BASE);
        sb.append("\n\n--- Dynamic context ---\n");
        Map<String, String> status = trainingService.status();
        sb.append("Model state: ").append(status.get("state"))
                .append(" (").append(status.get("promptCount")).append(" prompts).\n");

        if (userSystemPrompt != null && !userSystemPrompt.isBlank()) {
            sb.append("\n--- End-user system prompt (widened) ---\n");
            sb.append(userSystemPrompt.trim()).append('\n');
        }

        List<Map<String, String>> recent = trainingService.recentExamples(5);
        if (!recent.isEmpty()) {
            sb.append("\n--- Learned prompt patterns (recent training) ---\n");
            for (Map<String, String> ex : recent) {
                String p = ex.getOrDefault("userPrompt", "");
                if (p.length() > 220) {
                    p = p.substring(0, 217) + "...";
                }
                sb.append("- ").append(p.replace('\n', ' ')).append('\n');
            }
        }

        if (userStory != null && !userStory.isBlank()) {
            sb.append("\n--- Current end-user prompt ---\n");
            sb.append(userStory.trim()).append('\n');
        }
        return sb.toString();
    }
}
