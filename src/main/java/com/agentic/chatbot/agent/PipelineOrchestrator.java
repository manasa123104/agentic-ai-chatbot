package com.agentic.chatbot.agent;

import com.agentic.chatbot.generator.StoryAnalyzer;
import com.agentic.chatbot.generator.UploadedStory;
import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.service.GitService;
import com.agentic.chatbot.service.RunControl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the bots one after another. Every finished bot is saved to a checkpoint (generated-app/.runs/&lt;runId&gt;.json), so
 * the Stop button can end a run cleanly between two bots and Resume can continue with the next unfinished bot instead of
 * starting over.
 */
@Service
public class PipelineOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PipelineOrchestrator.class);

    /** What is saved after each finished bot. */
    public static class Checkpoint {
        public String runId;
        public String userStory;
        public String systemPrompt;
        public boolean runUnitTests = true;
        public boolean runFunctionalTests = true;
        public String refinedStory;
        /** Names of the bots that finished successfully. */
        public List<String> completed = new ArrayList<>();
        /** The log of those bots, in order. */
        public List<BotStepLog> steps = new ArrayList<>();
    }

    private final BusinessAnalystBot businessAnalystBot;
    private final UiGeneratorBot uiGeneratorBot;
    private final UnitTestBot unitTestBot;
    private final FunctionalTestBot functionalTestBot;
    private final ModelTrainerBot modelTrainerBot;
    private final RunControl control;
    private final Path outputDir;
    private final String llmApiKey;
    private final ObjectMapper json = new ObjectMapper();

    public PipelineOrchestrator(
            BusinessAnalystBot businessAnalystBot,
            UiGeneratorBot uiGeneratorBot,
            UnitTestBot unitTestBot,
            FunctionalTestBot functionalTestBot,
            ModelTrainerBot modelTrainerBot,
            RunControl control,
            @Value("${agentic.output-dir:generated-app}") String outputDir,
            @Value("${agentic.llm.api-key:}") String llmApiKey) {
        this.businessAnalystBot = businessAnalystBot;
        this.uiGeneratorBot = uiGeneratorBot;
        this.unitTestBot = unitTestBot;
        this.functionalTestBot = functionalTestBot;
        this.modelTrainerBot = modelTrainerBot;
        this.control = control;
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
        this.llmApiKey = llmApiKey;
    }

    // ------------------------------------------------------------------ checkpoints

    private Path runsDir() {
        return outputDir.resolve(".runs");
    }

    private Path checkpointFile(String runId) {
        return runsDir().resolve(runId + ".json");
    }

    /** The saved progress of a run, if it has any. */
    public java.util.Optional<Checkpoint> checkpoint(String runId) {
        if (!RunControl.validId(runId)) {
            return java.util.Optional.empty();
        }
        Path file = checkpointFile(runId);
        if (!Files.isRegularFile(file)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(json.readValue(file.toFile(), Checkpoint.class));
        } catch (IOException e) {
            log.warn("Unreadable checkpoint {}: {}", file, e.getMessage());
            return java.util.Optional.empty();
        }
    }

    public boolean hasCheckpoint(String runId) {
        return checkpoint(runId).isPresent();
    }

    private void save(Checkpoint cp) {
        try {
            Files.createDirectories(runsDir());
            json.writerWithDefaultPrettyPrinter().writeValue(checkpointFile(cp.runId).toFile(), cp);
        } catch (IOException e) {
            log.warn("Could not save checkpoint {}: {}", cp.runId, e.getMessage());
        }
    }

    private void deleteCheckpoint(String runId) {
        try {
            Files.deleteIfExists(checkpointFile(runId));
        } catch (IOException ignored) {
            // harmless leftover
        }
    }

    /** Progress of a run for the Resume button: whether it can continue and which bots are done. */
    public Map<String, Object> status(String runId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("runId", runId);
        var cp = checkpoint(runId);
        out.put("resumable", cp.isPresent());
        out.put("stopRequested", control.stopRequested(runId));
        out.put("completed", cp.map(c -> c.completed).orElse(List.of()));
        return out;
    }

    // ------------------------------------------------------------------ running

    public PipelineResponse run(PipelineRequest request) {
        return run(request, PipelineListener.NONE);
    }

    /** Same as {@link #run(PipelineRequest)}, and tells {@code listener} when each bot starts and finishes. */
    public PipelineResponse run(PipelineRequest request, PipelineListener listener) {
        String runId = RunControl.validId(request.getRunId()) ? request.getRunId() : newRunId();
        PipelineListener hear = listener == null ? PipelineListener.NONE : listener;
        // a Resume that arrives while the stopped request is still finishing its current bot waits for it here
        synchronized (control.lock(runId)) {
            return runLocked(request, runId, hear);
        }
    }

    public static String newRunId() {
        return "run-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private PipelineResponse runLocked(PipelineRequest request, String runId, PipelineListener listener) {
        PipelineResponse response = new PipelineResponse();
        response.setRunId(runId);
        List<BotStepLog> steps = new ArrayList<>();

        Checkpoint cp = request.isResume() ? checkpoint(runId).orElse(null) : null;
        boolean resuming = cp != null;
        if (!resuming) {
            if (request.getUserStory() == null || request.getUserStory().isBlank()) {
                response.setSuccess(false);
                response.setMessage("Please provide a user story prompt.");
                return response;
            }
            cp = new Checkpoint();
            cp.runId = runId;
            cp.userStory = request.getUserStory().trim();
            cp.systemPrompt = request.getSystemPrompt();
            cp.runUnitTests = request.isRunUnitTests();
            cp.runFunctionalTests = request.isRunFunctionalTests();
        }
        control.clear(runId);

        try {
            Files.createDirectories(outputDir);
            AgentContext context = new AgentContext(cp.userStory, outputDir, llmApiKey, cp.systemPrompt);
            if (resuming) {
                if (cp.refinedStory != null) {
                    context.setRefinedStory(cp.refinedStory);
                }
                steps.addAll(cp.steps);
                for (BotStepLog done : cp.steps) {
                    done.getGeneratedFiles().forEach(f -> context.addGeneratedFile(outputDir.resolve(f)));
                }
                log.info("Resuming run {} after {} finished step(s)", runId, cp.completed.size());
            } else {
                clearPreviousSite();
            }

            List<AgentBot> plan = new ArrayList<>(List.of(businessAnalystBot, uiGeneratorBot));
            if (cp.runUnitTests) {
                plan.add(unitTestBot);
            }
            if (cp.runFunctionalTests) {
                plan.add(functionalTestBot);
            }
            plan.add(modelTrainerBot);

            for (AgentBot bot : plan) {
                if (cp.completed.contains(bot.name())) {
                    continue;
                }
                if (control.stopRequested(runId)) {
                    control.clear(runId);
                    return stopped(response, steps, cp, plan.size());
                }
                int position = plan.indexOf(bot) + 1;
                safely(() -> listener.stepStarted(bot.name(), position, plan.size()));
                BotStepLog step = execute(bot, context);
                steps.add(step);
                safely(() -> listener.stepFinished(step, position, plan.size()));
                if (step.isSuccess()) {
                    cp.completed.add(bot.name());
                    cp.steps.add(step);
                    cp.refinedStory = context.getRefinedStory();
                    save(cp);
                } else if (bot == businessAnalystBot || bot == uiGeneratorBot) {
                    String who = bot == businessAnalystBot ? "Business Analyst" : "Java Developer";
                    response.setResumable(!cp.completed.isEmpty());
                    return fail(response, steps, "Pipeline stopped: " + who + " bot failed.", cp);
                }
            }

            boolean allOk = steps.stream().allMatch(BotStepLog::isSuccess);
            StoryAnalyzer.Analysis analysis = StoryAnalyzer.analyze(UploadedStory.forGeneration(cp.userStory));
            String previewUrl = "/site" + analysis.pagePath();
            try {
                Path meta = outputDir.resolve("SITE_META.json");
                if (Files.exists(meta)) {
                    String text = Files.readString(meta);
                    int idx = text.indexOf("\"previewUrl\"");
                    if (idx >= 0) {
                        int q1 = text.indexOf('"', idx + 12);
                        int q2 = text.indexOf('"', q1 + 1);
                        if (q1 > 0 && q2 > q1) {
                            previewUrl = text.substring(q1 + 1, q2);
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
                    boolean serverFile = lower.startsWith("server/") && !lower.contains("/public/");
                    if (lower.endsWith(".java") || lower.endsWith(".html")
                            || lower.endsWith("user_story.md") || lower.endsWith("pom.xml") || serverFile) {
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
            response.setCompletedSteps(cp.completed.size());
            response.setOutputDirectory(outputDir.toString());
            if (allOk) {
                deleteCheckpoint(runId);
                control.forget(runId);
            } else {
                response.setResumable(true);
            }
            return response;
        } catch (Exception e) {
            log.error("Pipeline failed", e);
            response.setSuccess(false);
            response.setMessage("Pipeline error: " + e.getMessage());
            response.setSteps(steps);
            response.setCompletedSteps(cp.completed.size());
            response.setResumable(!cp.completed.isEmpty());
            response.setOutputDirectory(outputDir.toString());
            return response;
        }
    }

    private PipelineResponse stopped(PipelineResponse response, List<BotStepLog> steps, Checkpoint cp, int total) {
        String last = cp.completed.isEmpty() ? "nothing yet" : cp.completed.get(cp.completed.size() - 1);
        response.setSuccess(false);
        response.setStopped(true);
        response.setResumable(true);
        response.setCompletedSteps(cp.completed.size());
        response.setMessage("Stopped after " + cp.completed.size() + " of " + total + " steps (last finished: " + last
                + "). Press Resume to continue with the next step.");
        response.setSteps(steps);
        response.setOutputDirectory(outputDir.toString());
        return response;
    }

    /**
     * Every story gets a fresh site. Without this the pages, style and agent files of the previous story stayed in the
     * shared output folder, so /site/* kept showing old pages and the old colours on top of the new story.
     */
    private void clearPreviousSite() {
        GitService.deleteQuietly(outputDir.resolve("src"));
        GitService.deleteQuietly(outputDir.resolve("server"));
        for (String name : List.of("SITE_META.json", "SITE_AGENT.json", "SITE_STYLE.json", "USER_STORY.md", "pom.xml")) {
            try {
                Files.deleteIfExists(outputDir.resolve(name));
            } catch (IOException e) {
                log.warn("Could not remove old {}: {}", name, e.getMessage());
            }
        }
    }

    private PipelineResponse fail(PipelineResponse response, List<BotStepLog> steps, String message, Checkpoint cp) {
        response.setSuccess(false);
        response.setMessage(message);
        response.setSteps(steps);
        response.setCompletedSteps(cp.completed.size());
        response.setOutputDirectory(outputDir.toString());
        return response;
    }

    /** A broken listener (e.g. a closed browser tab) must never break the run itself. */
    private static void safely(Runnable notification) {
        try {
            notification.run();
        } catch (RuntimeException e) {
            log.debug("Pipeline listener failed: {}", e.toString());
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
