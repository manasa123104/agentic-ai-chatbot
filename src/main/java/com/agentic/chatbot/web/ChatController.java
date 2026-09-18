package com.agentic.chatbot.web;

import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChatController {

    private final PipelineOrchestrator orchestrator;
    private final LlmClient llmClient;

    public ChatController(PipelineOrchestrator orchestrator, LlmClient llmClient) {
        this.orchestrator = orchestrator;
        this.llmClient = llmClient;
    }

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("request")) {
            model.addAttribute("request", new PipelineRequest());
        }
        model.addAttribute("llmReady", llmClient.isAvailable());
        return "index";
    }

    @PostMapping("/run")
    public String run(
            @ModelAttribute("request") PipelineRequest request,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        // Always run the full pipeline — no form toggles on the home page
        request.setRunUnitTests(true);
        request.setRunFunctionalTests(true);
        PipelineResponse response = orchestrator.run(request);
        session.setAttribute("lastPipelineResponse", response);
        session.setAttribute("lastUserStory", request.getUserStory());

        if (response.isSuccess() && response.getPreviewUrl() != null) {
            // Redirect browser to the newly generated website page
            return "redirect:" + response.getPreviewUrl();
        }

        model.addAttribute("request", request);
        model.addAttribute("response", response);
        model.addAttribute("llmReady", llmClient.isAvailable());
        return "index";
    }

    @GetMapping("/results")
    public String results(Model model, HttpSession session) {
        Object response = session.getAttribute("lastPipelineResponse");
        PipelineRequest request = new PipelineRequest();
        Object story = session.getAttribute("lastUserStory");
        if (story instanceof String s) {
            request.setUserStory(s);
        }
        model.addAttribute("request", request);
        model.addAttribute("response", response);
        model.addAttribute("llmReady", llmClient.isAvailable());
        if (response instanceof PipelineResponse pr && pr.getPreviewUrl() != null) {
            model.addAttribute("previewUrl", pr.getPreviewUrl());
        }
        return "index";
    }
}
