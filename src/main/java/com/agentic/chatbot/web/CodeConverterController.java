package com.agentic.chatbot.web;

import com.agentic.chatbot.service.CodeConversionService;
import com.agentic.chatbot.service.CodeSampleLibrary;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CodeConverterController {

    private final CodeConversionService conversionService;
    private final CodeSampleLibrary sampleLibrary;
    private final boolean allowLocalPaths;

    public CodeConverterController(CodeConversionService conversionService, CodeSampleLibrary sampleLibrary,
                                   @Value("${agentic.converter.allow-local-paths:true}") boolean allowLocalPaths) {
        this.conversionService = conversionService;
        this.sampleLibrary = sampleLibrary;
        this.allowLocalPaths = allowLocalPaths;
    }

    @GetMapping("/convert")
    public String convert(Model model, HttpServletRequest request) {
        boolean host = RequestOrigin.isHost(request);
        var llmName = conversionService.llmName();
        model.addAttribute("allowLocalPaths", allowLocalPaths && host);
        model.addAttribute("isHost", host);
        model.addAttribute("publicMode", RequestOrigin.isPublicMode());
        model.addAttribute("publicModeNotice", RequestOrigin.PUBLIC_MODE_NOTICE);
        model.addAttribute("llmReady", llmName.isPresent());
        model.addAttribute("llmName", llmName.orElse(""));
        model.addAttribute("languages", sampleLibrary.languages());
        model.addAttribute("moreLanguages", sampleLibrary.moreLanguages());
        model.addAttribute("allLanguages", sampleLibrary.allLanguages());
        model.addAttribute("samples", sampleLibrary.samples());
        return "convert";
    }
}
