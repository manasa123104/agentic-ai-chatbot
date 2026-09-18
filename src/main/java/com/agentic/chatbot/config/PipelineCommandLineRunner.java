package com.agentic.chatbot.config;

import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Script mode: pass --story="..." (and optional --exit) to run the 3-bot pipeline from CLI.
 */
@Component
public class PipelineCommandLineRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PipelineCommandLineRunner.class);

    private final PipelineOrchestrator orchestrator;

    public PipelineCommandLineRunner(PipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!args.containsOption("story")) {
            return;
        }
        String story = String.join(" ", args.getOptionValues("story"));
        log.info("Script mode: running agent pipeline for user story");

        PipelineRequest request = new PipelineRequest();
        request.setUserStory(story);
        request.setRunUnitTests(!args.containsOption("skip-unit"));
        request.setRunFunctionalTests(!args.containsOption("skip-functional"));

        PipelineResponse response = orchestrator.run(request);
        response.getSteps().forEach(step ->
                log.info("[{}] success={} ({} ms) — {}",
                        step.getBotName(), step.isSuccess(), step.getDurationMs(), step.getSummary()));

        if (args.containsOption("exit")) {
            System.exit(response.isSuccess() ? 0 : 1);
        }
    }
}
