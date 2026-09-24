package com.agentic.chatbot.agent;

import com.agentic.chatbot.generator.StoryAnalyzer;
import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    private final BusinessAnalystBot businessAnalystBot;
    private final UiGeneratorBot uiGeneratorBot;
    private final UnitTestBot unitTestBot;
    private final FunctionalTestBot functionalTestBot;
    private final ModelTrainerBot modelTrainerBot;
    private final Path outputDir;
    private final String llmApiKey;

    public PipelineOrchestrator(
            BusinessAnalystBot businessAnalystBot,
            UiGeneratorBot uiGeneratorBot,
            UnitTestBot unitTestBot,
            FunctionalTestBot functionalTestBot,
            ModelTrainerBot modelTrainerBot,
            @Value("${agentic.output-dir:generated-app}") String outputDir,
            @Value("${agentic.llm.api-key:}") String llmApiKey) {
        this.businessAnalystBot = businessAnalystBot;
        this.uiGeneratorBot = uiGeneratorBot;
        this.unitTestBot = unitTestBot;
        this.functionalTestBot = functionalTestBot;
        this.modelTrainerBot = modelTrainerBot;
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
        this.llmApiKey = llmApiKey;
    }

    public PipelineResponse run(PipelineRequest request) {
        PipelineResponse response = new PipelineResponse();
        List<BotStepLog> steps = new ArrayList<>();

        if (request.getUserStory() == null || request.getUserStory().isBlank()) {
            response.setSuccess(false);
            response.setMessage("Please provide a user story prompt.");
            return response;
        }

        try {
            Files.createDirectories(outputDir);
            AgentContext context = new AgentContext(
                    request.getUserStory().trim(),
                    outputDir,
                    llmApiKey,
                    request.getSystemPrompt());

            // 1) Business Analyst — refine prompts into user stories
            steps.add(execute(businessAnalystBot, context));
            if (!steps.get(steps.size() - 1).isSuccess()) {
                return fail(response, steps, "Pipeline stopped: Business Analyst bot failed.");
            }

            // 2) Java Developer — implement from the refined story
            steps.add(execute(uiGeneratorBot, context));
            if (!steps.get(steps.size() - 1).isSuccess()) {
                return fail(response, steps, "Pipeline stopped: Java Developer bot failed.");
            }

            // 3) Java Unit Tester
            if (request.isRunUnitTests()) {
                steps.add(execute(unitTestBot, context));
            }
            // 4) Functional Tester
            if (request.isRunFunctionalTests()) {
                steps.add(execute(functionalTestBot, context));
            }

            // 5) Developer Tester — verify user stories + code, train model
            steps.add(execute(modelTrainerBot, context));

            boolean allOk = steps.stream().allMatch(BotStepLog::isSuccess);
            StoryAnalyzer.Analysis analysis = StoryAnalyzer.analyze(request.getUserStory());
            String previewUrl = "/site" + analysis.pagePath();
            try {
                Path meta = outputDir.resolve("SITE_META.json");
                if (Files.exists(meta)) {
                    String json = Files.readString(meta);
                    int idx = json.indexOf("\"previewUrl\"");
                    if (idx >= 0) {
                        int q1 = json.indexOf('"', idx + 12);
                        int q2 = json.indexOf('"', q1 + 1);
                        if (q1 > 0 && q2 > q1) {
                            previewUrl = json.substring(q1 + 1, q2);
                        }
                    }
                }
            } catch (Exception ignored) {
            }

            String refined = context.getRefinedStory();
            try {
                Path storyPath = outputDir.resolve("USER_STORY.md");
                if (Files.exists(storyPath)) {
                    refined = Files.readString(storyPath);
                }
            } catch (Exception ignored) {
            }
            List<String> codeFiles = new ArrayList<>();
            for (BotStepLog step : steps) {
                if (step.getGeneratedFiles() == null) {
                    continue;
                }
                for (String f : step.getGeneratedFiles()) {
                    if (f == null) {
                        continue;
                    }
                    String lower = f.toLowerCase();
                    if (lower.endsWith(".java") || lower.endsWith(".html")
                            || lower.endsWith("user_story.md") || lower.endsWith("pom.xml")) {
                        if (!codeFiles.contains(f)) {
                            codeFiles.add(f);
                        }
                    }
                }
            }

            response.setSuccess(allOk);
            response.setPreviewUrl(previewUrl);
            response.setCodeUrl("/code");
            response.setRefinedUserStory(refined);
            response.setGeneratedCodeFiles(codeFiles);
            response.setRedirectPath(analysis.redirectPath());
            response.setMessage(allOk
                    ? "All 5 agents done. User story + Java website code ready — preview "
                    + previewUrl + " · code /code"
                    : "Pipeline finished with one or more failures.");
            response.setSteps(steps);
            response.setOutputDirectory(outputDir.toString());
            return response;
        } catch (Exception e) {
            log.error("Pipeline failed", e);
            response.setSuccess(false);
            response.setMessage("Pipeline error: " + e.getMessage());
            response.setSteps(steps);
            response.setOutputDirectory(outputDir.toString());
            return response;
        }
    }

    private PipelineResponse fail(PipelineResponse response, List<BotStepLog> steps, String message) {
        response.setSuccess(false);
        response.setMessage(message);
        response.setSteps(steps);
        response.setOutputDirectory(outputDir.toString());
        return response;
    }

    private BotStepLog execute(AgentBot bot, AgentContext context) {
        long start = System.currentTimeMillis();
        log.info("Running {}", bot.name());
        AgentResult result = bot.run(context);
        long duration = System.currentTimeMillis() - start;
        List<String> files = result.getGeneratedFiles().stream()
                .map(p -> context.getProjectDir().relativize(p).toString().replace('\\', '/'))
                .toList();
        return new BotStepLog(bot.name(), result.isSuccess(), result.getSummary(), files, duration);
    }
}
