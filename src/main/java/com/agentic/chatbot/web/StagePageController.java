package com.agentic.chatbot.web;

import com.agentic.chatbot.stage.StageAutomation;
import com.agentic.chatbot.stage.StageDomain;
import com.agentic.chatbot.stage.StageLanguage;
import com.agentic.chatbot.stage.StageService;
import com.agentic.chatbot.stage.StageType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class StagePageController {

    private final StageService stages;

    public StagePageController(StageService stages) {
        this.stages = stages;
    }

    @GetMapping("/stages")
    public String stages(Model model) {
        var llmName = stages.modelName();
        model.addAttribute("llmReady", llmName.isPresent());
        model.addAttribute("llmName", llmName.orElse(""));
        model.addAttribute("trainingStats", stages.trainingStats());
        List<Map<String, String>> stageList = Arrays.stream(StageType.values()).map(s -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", s.id());
            m.put("title", s.title());
            m.put("produces", s.produces());
            return m;
        }).toList();
        model.addAttribute("stageList", stageList);
        model.addAttribute("workStages", stageList.subList(0, stageList.size() - 1));
        List<Map<String, Object>> domainGroups = StageOptions.domainGroups();
        List<Map<String, String>> languages = StageOptions.languages();
        model.addAttribute("domainGroups", domainGroups);
        model.addAttribute("genericRequirement", com.agentic.chatbot.stage.DomainCatalog.genericRequirement("{domain}"));
        model.addAttribute("domainCount", StageDomain.SUGGESTIONS.size());
        model.addAttribute("sampleLanguages", languages.stream().filter(l -> l.get("group").equals("samples")).toList());
        model.addAttribute("moreLanguages", languages.stream().filter(l -> l.get("group").equals("more")).toList());
        model.addAttribute("languages", languages);
        model.addAttribute("otherLanguage", Map.of("unit", StageLanguage.OTHER.unitFramework(),
                "automation", StageLanguage.OTHER.defaultAutomation()));
        model.addAttribute("automations", Arrays.stream(StageAutomation.values())
                .map(a -> Map.of("id", a.id(), "label", a.label())).toList());
        return "stages";
    }
}
