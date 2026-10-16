package com.agentic.chatbot.agent;

import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.service.RunControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Stop ends a run between two bots and keeps progress; Resume continues with the next unfinished bot. */
class PipelineStopResumeTest {

    @TempDir
    Path dir;

    private static AgentBot bot(Class<? extends AgentBot> type, String name) {
        AgentBot bot = mock(type);
        when(bot.name()).thenReturn(name);
        when(bot.run(any())).thenReturn(AgentResult.ok(name + " done", List.of()));
        return bot;
    }

    @Test
    void stopKeepsProgressAndResumeContinuesFromTheNextBot() {
        RunControl control = new RunControl();
        BusinessAnalystBot ba = (BusinessAnalystBot) bot(BusinessAnalystBot.class, "Business Analyst");
        UiGeneratorBot ui = (UiGeneratorBot) bot(UiGeneratorBot.class, "Java Developer");
        UnitTestBot unit = (UnitTestBot) bot(UnitTestBot.class, "Unit Tester");
        FunctionalTestBot functional = (FunctionalTestBot) bot(FunctionalTestBot.class, "Functional Tester");
        ModelTrainerBot trainer = (ModelTrainerBot) bot(ModelTrainerBot.class, "Developer Tester");
        PipelineOrchestrator orchestrator = new PipelineOrchestrator(ba, ui, unit, functional, trainer, control,
                dir.toString(), "");

        String runId = "run-test-0001";
        // the user presses Stop while the Java Developer bot is running
        when(ui.run(any())).thenAnswer(inv -> {
            control.requestStop(runId);
            return AgentResult.ok("code written", List.of());
        });

        PipelineRequest first = new PipelineRequest();
        first.setUserStory("As a customer I want to order bread online");
        first.setRunId(runId);
        PipelineResponse stopped = orchestrator.run(first);

        assertTrue(stopped.isStopped());
        assertTrue(stopped.isResumable());
        assertFalse(stopped.isSuccess());
        assertEquals(2, stopped.getCompletedSteps());
        verify(unit, times(0)).run(any());
        assertTrue(orchestrator.hasCheckpoint(runId));
        assertEquals(List.of("Business Analyst", "Java Developer"), orchestrator.status(runId).get("completed"));

        PipelineRequest again = new PipelineRequest();
        again.setUserStory("As a customer I want to order bread online");
        again.setRunId(runId);
        again.setResume(true);
        PipelineResponse resumed = orchestrator.run(again);

        assertFalse(resumed.isStopped());
        assertTrue(resumed.isSuccess(), resumed.getMessage());
        assertEquals(5, resumed.getSteps().size());
        verify(ba, times(1)).run(any());   // not run again
        verify(ui, times(1)).run(any());   // not run again
        verify(unit, times(1)).run(any());
        verify(functional, times(1)).run(any());
        verify(trainer, times(1)).run(any());
        assertFalse(orchestrator.hasCheckpoint(runId), "a finished run leaves no checkpoint behind");
    }

    @Test
    void runIdsAreValidated() {
        assertTrue(RunControl.validId("run-abc12345"));
        assertFalse(RunControl.validId("../etc/passwd"));
        assertFalse(RunControl.validId("x"));
        assertFalse(RunControl.validId(null));
    }
}
