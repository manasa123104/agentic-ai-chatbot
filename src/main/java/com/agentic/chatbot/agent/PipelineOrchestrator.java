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

    private final UiGeneratorBot uiGeneratorBot;
    private final UnitTestBot unitTestBot;
    private final FunctionalTestBot functionalTestBot;
    private final Path outputDir;
    private final String llmApiKey;

    public PipelineOrchestrator(
            UiGeneratorBot uiGeneratorBot,
            UnitTestBot unitTestBot,
            FunctionalTestBot functionalTestBot,
            @Value("${agentic.output-dir:generated-app}") String outputDir,
            @Value("${agentic.llm.api-key:}") String llmApiKey) {
        this.uiGeneratorBot = uiGeneratorBot;
        this.unitTestBot = unitTestBot;
        this.functionalTestBot = functionalTestBot;
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
            AgentContext context = new AgentContext(request.getUserStory().trim(), outputDir, llmApiKey);

            steps.add(execute(uiGeneratorBot, context));
            if (!steps.get(steps.size() - 1).isSuccess()) {
                response.setSuccess(false);
                response.setMessage("Pipeline stopped: UI bot failed.");
                response.setSteps(steps);
                response.setOutputDirectory(outputDir.toString());
                return response;
            }

            if (request.isRunUnitTests()) {
                steps.add(execute(unitTestBot, context));
            }
            if (request.isRunFunctionalTests()) {
                steps.add(execute(functionalTestBot, context));
            }

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
            response.setSuccess(allOk);
            response.setPreviewUrl(previewUrl);
            response.setRedirectPath(analysis.redirectPath());
            response.setMessage(allOk
                    ? "All agents completed. Opening generated page at " + previewUrl
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
