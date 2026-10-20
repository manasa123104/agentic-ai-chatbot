package com.agentic.chatbot.service;

import com.agentic.chatbot.model.ConversionJob;
import com.agentic.chatbot.model.ConversionRequest;
import com.agentic.chatbot.model.ConversionResponse;
import com.agentic.chatbot.stage.StageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatHistoryServiceTest {

    @TempDir
    Path tmp;

    @Test
    void converterAndStageRequestsShareOneSidebar() {
        ChatHistoryService history = new ChatHistoryService(tmp.resolve("history.json").toString());
        history.saveConversion(
                new ConversionRequest("java", "python", "class Hello {}", "", false, false),
                new ConversionResponse(true, "Converted", "llm", "java", "Java", "python", "Python",
                        "print('hi')", "", "", "Hello.py", null));

        ConversionJob job = new ConversionJob();
        job.setId("20261020-180000-abc123");
        job.setSourceLabel("https://github.com/acme/ledger");
        job.setTargetLanguage("Python");
        job.setStatus(ConversionJob.Status.QUEUED);
        job.setMessage("Waiting to start");
        history.saveConversionJob(job);
        job.setStatus(ConversionJob.Status.DONE);
        job.setMessage("Converted 3 of 3 files to Python.");
        history.saveConversionJob(job);

        history.saveStage(new StageResponse(true, "ba", "Business Analyst", "banking", "java",
                "20261020-181500-def456", "template", "", "stories", List.of(), List.of(),
                "developer", "next", 10L), "Fund transfer between accounts");
        history.saveStage(new StageResponse(true, "developer", "Developer", "banking", "java",
                "20261020-181500-def456", "template", "", "code", List.of(), List.of(),
                "tester", "next", 20L), "stories");

        history.saveStageRun(Map.of(
                "runId", "20261019-090000-old001",
                "domainLabel", "Healthcare",
                "language", "python",
                "stages", Map.of("ba", Map.of("input", "Patient appointment booking"))));

        assertEquals(4, history.list().size());
        assertEquals(1, history.list().stream().filter(e -> "20261020-180000-abc123".equals(e.getJobId())).count());
        assertEquals(1, history.list().stream().filter(e -> "20261020-181500-def456".equals(e.getRunId())).count());
        assertTrue(history.findByJobId("20261020-180000-abc123").orElseThrow().isSuccess());
        assertEquals("/convert?job=20261020-180000-abc123",
                history.findByJobId("20261020-180000-abc123").orElseThrow().getRedirectPath());
        assertEquals("/stages?run=20261020-181500-def456",
                history.findByRunId("20261020-181500-def456").orElseThrow().getRedirectPath());
        assertEquals("Code converter", history.list().stream()
                .filter(e -> e.getConversion() != null).findFirst().orElseThrow().getSourceLabel());
        assertEquals("Stage bots", history.findByRunId("20261020-181500-def456").orElseThrow().getSourceLabel());
        assertTrue(history.findByRunId("20261019-090000-old001").orElseThrow().getUserStory()
                .contains("Patient appointment booking"));
    }
}
