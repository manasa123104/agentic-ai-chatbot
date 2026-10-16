package com.agentic.chatbot.web;

import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.ChatHistoryEntry;
import com.agentic.chatbot.model.ConversionRequest;
import com.agentic.chatbot.model.ConversionResponse;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.service.ChatHistoryService;
import com.agentic.chatbot.service.CodeConversionService;
import com.agentic.chatbot.service.RunControl;
import com.agentic.chatbot.service.SiteEditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class PipelineApiController {

    private final PipelineOrchestrator orchestrator;
    private final ChatHistoryService historyService;
    private final SiteEditService siteEditService;
    private final CodeConversionService conversionService;
    private final RunControl control;

    public PipelineApiController(
            PipelineOrchestrator orchestrator,
            ChatHistoryService historyService,
            SiteEditService siteEditService,
            CodeConversionService conversionService,
            RunControl control) {
        this.control = control;
        this.orchestrator = orchestrator;
        this.historyService = historyService;
        this.siteEditService = siteEditService;
        this.conversionService = conversionService;
    }

    @PostMapping("/pipeline")
    public ResponseEntity<PipelineResponse> runPipeline(
            @RequestBody PipelineRequest request,
            HttpSession session) {
        request.setRunUnitTests(true);
        request.setRunFunctionalTests(true);
        long startedMs = System.currentTimeMillis();

        String instruction = request.getUserStory() == null ? "" : request.getUserStory().trim();
        if (request.isResume() && orchestrator.hasCheckpoint(request.getRunId())) {
            // Resume: continue the stopped run from its next unfinished bot
            PipelineResponse resumed = orchestrator.run(request);
            resumed.setDurationMs(System.currentTimeMillis() - startedMs);
            if (!resumed.isStopped()) {
                finish(resumed, instruction, session);
            }
            return ResponseEntity.ok(resumed);
        }
        Optional<ConversionRequest> conversionRequest = conversionService.fromChatMessage(instruction);
        if (conversionRequest.isPresent()) {
            PipelineResponse response = convert(conversionRequest.get());
            response.setDurationMs(System.currentTimeMillis() - startedMs);
            response.setHistoryId(historyService.save(instruction, response).getId());
            return ResponseEntity.ok(response);
        }
        String baseStory = resolveBaseStory(session);

        PipelineResponse response;
        if (siteEditService.looksLikeEdit(instruction) && siteEditService.hasGeneratedSite()) {
            response = siteEditService.apply(instruction, baseStory);
            // Keep original site story as context for the next edit
            if (baseStory != null && !baseStory.isBlank()) {
                session.setAttribute("baseUserStory", baseStory);
            }
            session.setAttribute("lastEditInstruction", instruction);
        } else {
            response = orchestrator.run(request);
            if (!response.isStopped()) {
                session.setAttribute("baseUserStory", instruction);
            }
        }

        response.setDurationMs(System.currentTimeMillis() - startedMs);
        if (!response.isStopped()) {
            finish(response, instruction, session);
        }
        return ResponseEntity.ok(response);
    }

    /** Saves a finished run to the history and remembers it for the next edit; stopped runs are not saved yet. */
    private void finish(PipelineResponse response, String instruction, HttpSession session) {
        if (response.getRunId() != null && response.isSuccess()) {
            session.setAttribute("baseUserStory", instruction);
        }
        ChatHistoryEntry saved = historyService.save(instruction, response);
        response.setHistoryId(saved.getId());
        session.setAttribute("lastPipelineResponse", response);
        session.setAttribute("lastUserStory", instruction);
        session.setAttribute("lastHistoryId", saved.getId());
    }

    /** The Stop button: the run ends after the bot that is running now, and keeps what it has finished. */
    @PostMapping("/pipeline/stop")
    public ResponseEntity<Map<String, Object>> stop(@RequestBody Map<String, String> body) {
        String runId = body == null ? null : body.get("runId");
        if (!RunControl.validId(runId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "runId is required"));
        }
        control.requestStop(runId);
        return ResponseEntity.ok(orchestrator.status(runId));
    }

    /** What a run has finished so far, and whether it can be resumed. */
    @GetMapping("/pipeline/status")
    public ResponseEntity<Map<String, Object>> status(@RequestParam String runId) {
        if (!RunControl.validId(runId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid runId"));
        }
        return ResponseEntity.ok(orchestrator.status(runId));
    }

    private PipelineResponse convert(ConversionRequest request) {
        long start = System.currentTimeMillis();
        ConversionResponse result = conversionService.convert(request);
        PipelineResponse response = new PipelineResponse();
        response.setSuccess(result.success());
        response.setMessage(result.message());
        response.setCodeUrl("/convert");
        response.setConversion(result);
        List<String> files = result.outputFile() == null ? List.of() : List.of(result.outputFile());
        response.setSteps(new ArrayList<>(List.of(new BotStepLog("Code Converter", result.success(),
                result.message(), files, System.currentTimeMillis() - start))));
        return response;
    }

    private String resolveBaseStory(HttpSession session) {
        Object base = session.getAttribute("baseUserStory");
        if (base instanceof String s && !s.isBlank()) {
            return s;
        }
        Object last = session.getAttribute("lastUserStory");
        if (last instanceof String s && !s.isBlank() && !siteEditService.looksLikeEdit(s)) {
            return s;
        }
        var history = historyService.list();
        for (ChatHistoryEntry entry : history) {
            if (entry.getConversion() == null && entry.getUserStory() != null
                    && !siteEditService.looksLikeEdit(entry.getUserStory())) {
                return entry.getUserStory();
            }
        }
        for (ChatHistoryEntry entry : history) {
            if (entry.getConversion() == null && entry.getUserStory() != null) {
                return entry.getUserStory();
            }
        }
        return "";
    }
}
