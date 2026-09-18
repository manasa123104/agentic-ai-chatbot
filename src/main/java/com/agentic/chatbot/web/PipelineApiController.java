package com.agentic.chatbot.web;

import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
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

    public PipelineApiController(PipelineOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/pipeline")
    public ResponseEntity<PipelineResponse> runPipeline(
            @RequestBody PipelineRequest request,
            HttpSession session) {
        request.setRunUnitTests(true);
        request.setRunFunctionalTests(true);
        PipelineResponse response = orchestrator.run(request);
        session.setAttribute("lastPipelineResponse", response);
        session.setAttribute("lastUserStory", request.getUserStory());
        return ResponseEntity.ok(response);
    }
}
