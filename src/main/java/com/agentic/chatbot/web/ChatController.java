package com.agentic.chatbot.web;

import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.ChatHistoryEntry;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.service.BotModelTrainingService;
import com.agentic.chatbot.service.ChatHistoryService;
import com.agentic.chatbot.service.SiteEditService;
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
    private final ChatHistoryService historyService;
    private final SiteEditService siteEditService;
    private final BotModelTrainingService trainingService;

    public ChatController(
            PipelineOrchestrator orchestrator,
            LlmClient llmClient,
            ChatHistoryService historyService,
            SiteEditService siteEditService,
            BotModelTrainingService trainingService) {
        this.orchestrator = orchestrator;
        this.llmClient = llmClient;
        this.historyService = historyService;
        this.siteEditService = siteEditService;
        this.trainingService = trainingService;
    }

    private static final String DEFAULT_SYSTEM_PROMPT =
            "Include both Business Analyst user stories and Java website code "
                    + "(Spring Boot controllers + Thymeleaf templates). "
                    + "Build a multi-page site from the user story. Match topic images. Keep the brand name clean. "
                    + "Also produce unit tests and functional testing.";

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("request")) {
            PipelineRequest request = new PipelineRequest();
            request.setSystemPrompt(DEFAULT_SYSTEM_PROMPT);
            model.addAttribute("request", request);
        }
        enrich(model);
        return "index";
    }

    @PostMapping("/run")
    public String run(
            @ModelAttribute("request") PipelineRequest request,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        request.setRunUnitTests(true);
        request.setRunFunctionalTests(true);

        String instruction = request.getUserStory() == null ? "" : request.getUserStory().trim();
        String baseStory = resolveBaseStory(session);

        PipelineResponse response;
        if (siteEditService.looksLikeEdit(instruction) && siteEditService.hasGeneratedSite()) {
            response = siteEditService.apply(instruction, baseStory);
            if (baseStory != null && !baseStory.isBlank()) {
                session.setAttribute("baseUserStory", baseStory);
            }
        } else {
            response = orchestrator.run(request);
            session.setAttribute("baseUserStory", instruction);
        }

        ChatHistoryEntry saved = historyService.save(instruction, response);
        response.setHistoryId(saved.getId());
        session.setAttribute("lastPipelineResponse", response);
        session.setAttribute("lastUserStory", instruction);
        session.setAttribute("lastHistoryId", saved.getId());

        if (response.isSuccess() && response.getPreviewUrl() != null) {
            return "redirect:" + response.getPreviewUrl();
        }

        model.addAttribute("request", request);
        model.addAttribute("response", response);
        enrich(model);
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
        enrich(model);
        if (response instanceof PipelineResponse pr && pr.getPreviewUrl() != null) {
            model.addAttribute("previewUrl", pr.getPreviewUrl());
        }
        return "index";
    }

    private void enrich(Model model) {
        model.addAttribute("llmReady", llmClient.isAvailable());
        model.addAttribute("history", historyService.list());
        model.addAttribute("modelStatus", trainingService.status());
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
        for (ChatHistoryEntry entry : historyService.list()) {
            if (entry.getUserStory() != null && !siteEditService.looksLikeEdit(entry.getUserStory())) {
                return entry.getUserStory();
            }
        }
        return "";
    }
}
