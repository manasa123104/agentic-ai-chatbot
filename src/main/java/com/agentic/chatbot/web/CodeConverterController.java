package com.agentic.chatbot.web;

import com.agentic.chatbot.service.CodeConversionService;
import com.agentic.chatbot.service.CodeSampleLibrary;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CodeConverterController {

    private final CodeConversionService conversionService;
    private final CodeSampleLibrary sampleLibrary;

    public CodeConverterController(CodeConversionService conversionService, CodeSampleLibrary sampleLibrary) {
        this.conversionService = conversionService;
        this.sampleLibrary = sampleLibrary;
    }

    @GetMapping("/convert")
    public String convert(Model model) {
        var llmName = conversionService.llmName();
        model.addAttribute("llmReady", llmName.isPresent());
        model.addAttribute("llmName", llmName.orElse(""));
        model.addAttribute("languages", sampleLibrary.languages());
        model.addAttribute("samples", sampleLibrary.samples());
        return "convert";
    }
}
