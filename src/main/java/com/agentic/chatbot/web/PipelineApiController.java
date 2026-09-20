package com.agentic.chatbot.web;

import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.model.ChatHistoryEntry;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.service.ChatHistoryService;
import com.agentic.chatbot.service.SiteEditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PipelineApiController {

    private final PipelineOrchestrator orchestrator;
    private final ChatHistoryService historyService;
    private final SiteEditService siteEditService;

    public PipelineApiController(
            PipelineOrchestrator orchestrator,
            ChatHistoryService historyService,
            SiteEditService siteEditService) {
        this.orchestrator = orchestrator;
        this.historyService = historyService;
        this.siteEditService = siteEditService;
    }

    @PostMapping("/pipeline")
    public ResponseEntity<PipelineResponse> runPipeline(
            @RequestBody PipelineRequest request,
            HttpSession session) {
        request.setRunUnitTests(true);
        request.setRunFunctionalTests(true);

        String instruction = request.getUserStory() == null ? "" : request.getUserStory().trim();
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

        ChatHistoryEntry saved = historyService.save(instruction, response);
        response.setHistoryId(saved.getId());
        session.setAttribute("lastPipelineResponse", response);
        session.setAttribute("lastUserStory", instruction);
        session.setAttribute("lastHistoryId", saved.getId());
        return ResponseEntity.ok(response);
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
            if (entry.getUserStory() != null && !siteEditService.looksLikeEdit(entry.getUserStory())) {
                return entry.getUserStory();
            }
        }
        if (!history.isEmpty() && history.get(0).getUserStory() != null) {
            return history.get(0).getUserStory();
        }
        return "";
    }
}
