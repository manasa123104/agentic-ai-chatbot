package com.agentic.chatbot.agent;

import com.agentic.chatbot.service.BotModelTrainingService;
import com.agentic.chatbot.service.SystemPromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Bot 5 — Developer Tester: verifies user stories + generated Java website code, then trains BOT.
 */
@Component
public class ModelTrainerBot implements AgentBot {

    private final BotModelTrainingService trainingService;
    private final SystemPromptService systemPromptService;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public ModelTrainerBot(BotModelTrainingService trainingService, SystemPromptService systemPromptService) {
        this.trainingService = trainingService;
        this.systemPromptService = systemPromptService;
    }

    @Override
    public String name() {
        return "Bot 5 - Developer Tester";
    }

    @Override
    public AgentResult run(AgentContext context) {
        try {
            String story = context.getRefinedStory() != null && !context.getRefinedStory().isBlank()
                    ? context.getRefinedStory()
                    : context.getUserStory();
            String dynamicSystem = systemPromptService.build(context.getSystemPrompt(), context.getUserStory());
            context.setResolvedSystemPrompt(dynamicSystem);

            Path project = context.getProjectDir();
            Path userStoryFile = project.resolve("USER_STORY.md");
            List<String> javaFiles = listJavaFiles(project);
            List<String> templateFiles = listTemplates(project);

            if (!Files.exists(userStoryFile)) {
                Files.writeString(userStoryFile,
                        "# User story\n\n" + story + "\n",
                        StandardCharsets.UTF_8);
                context.addGeneratedFile(userStoryFile);
            }
            if (javaFiles.isEmpty() && templateFiles.isEmpty()) {
                return AgentResult.fail("Developer Tester: no Java website code found to verify.");
            }

            Map<String, String> status = trainingService.record(
                    context.getUserStory(), story, context.getSystemPrompt());

            Path modelFile = project.resolve("BOT_MODEL.json");
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("name", "BOT");
            snapshot.put("state", status.get("state"));
            snapshot.put("promptCount", Integer.parseInt(status.get("promptCount")));
            snapshot.put("label", status.get("label"));
            snapshot.put("systemPrompt", dynamicSystem);
            snapshot.put("userStoryIncluded", true);
            snapshot.put("javaFiles", javaFiles);
            snapshot.put("templateFiles", templateFiles);
            snapshot.put("pipeline", List.of(
                    "Business Analyst",
                    "Java Developer",
                    "Java Unit Tester",
                    "Functional Testing",
                    "Developer Tester"));
            Files.writeString(modelFile, mapper.writeValueAsString(snapshot), StandardCharsets.UTF_8);
            context.addGeneratedFile(modelFile);

            String summary = "Verified user story + generated code ("
                    + javaFiles.size() + " Java, "
                    + templateFiles.size() + " templates). "
                    + status.get("label") + ".";
            List<Path> outs = new ArrayList<>();
            outs.add(userStoryFile);
            outs.add(modelFile);
            return AgentResult.ok(summary, outs);
        } catch (Exception e) {
            return AgentResult.fail("Developer Tester failed: " + e.getMessage());
        }
    }

    private static List<String> listJavaFiles(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(p -> !root.relativize(p).toString().contains("target"))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> listTemplates(Path root) throws Exception {
        Path templates = root.resolve("src/main/resources/templates");
        if (!Files.isDirectory(templates)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.list(templates)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".html"))
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }
}
