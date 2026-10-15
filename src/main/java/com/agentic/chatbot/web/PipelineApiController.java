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
import com.agentic.chatbot.service.SiteEditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class PipelineApiController {

    private final PipelineOrchestrator orchestrator;
    private final ChatHistoryService historyService;
    private final SiteEditService siteEditService;
    private final CodeConversionService conversionService;

    public PipelineApiController(
            PipelineOrchestrator orchestrator,
            ChatHistoryService historyService,
            SiteEditService siteEditService,
            CodeConversionService conversionService) {
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
            session.setAttribute("baseUserStory", instruction);
        }

        response.setDurationMs(System.currentTimeMillis() - startedMs);
        ChatHistoryEntry saved = historyService.save(instruction, response);
        response.setHistoryId(saved.getId());
        session.setAttribute("lastPipelineResponse", response);
        session.setAttribute("lastUserStory", instruction);
        session.setAttribute("lastHistoryId", saved.getId());
        return ResponseEntity.ok(response);
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
