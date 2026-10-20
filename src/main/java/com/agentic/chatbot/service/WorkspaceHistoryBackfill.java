package com.agentic.chatbot.service;

import com.agentic.chatbot.model.ConversionJob;
import com.agentic.chatbot.stage.StageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Copies already-finished Code converter jobs and Stage bots runs into Previous results
 * so the home sidebar is not empty after a restart.
 */
@Component
public class WorkspaceHistoryBackfill {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceHistoryBackfill.class);

    private final ChatHistoryService history;
    private final RepositoryConversionService jobs;
    private final StageService stages;

    public WorkspaceHistoryBackfill(ChatHistoryService history, RepositoryConversionService jobs, StageService stages) {
        this.history = history;
        this.jobs = jobs;
        this.stages = stages;
    }

    @PostConstruct
    void importExisting() {
        int jobsImported = 0;
        for (ConversionJob job : jobs.list()) {
            if (history.findByJobId(job.getId()).isEmpty()) {
                history.saveConversionJob(job);
                jobsImported++;
            }
        }
        int runsImported = 0;
        for (Map<String, Object> run : stages.listRuns()) {
            Object id = run.get("runId");
            String runId = id == null ? "" : String.valueOf(id);
            if (!runId.isBlank() && history.findByRunId(runId).isEmpty()) {
                history.saveStageRun(run);
                runsImported++;
            }
        }
        if (jobsImported > 0 || runsImported > 0) {
            log.info("Imported {} conversion job(s) and {} stage run(s) into previous results", jobsImported, runsImported);
        }
    }
}
