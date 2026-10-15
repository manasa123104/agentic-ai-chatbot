package com.agentic.chatbot.web;

import com.agentic.chatbot.model.CodeSample;
import com.agentic.chatbot.model.ConversionRequest;
import com.agentic.chatbot.model.ConversionResponse;
import com.agentic.chatbot.model.ProgrammingLanguage;
import com.agentic.chatbot.service.CodeConversionService;
import com.agentic.chatbot.service.CodeSampleLibrary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/convert")
public class CodeConversionApiController {

    private final CodeConversionService conversionService;
    private final CodeSampleLibrary sampleLibrary;

    public CodeConversionApiController(CodeConversionService conversionService, CodeSampleLibrary sampleLibrary) {
        this.conversionService = conversionService;
        this.sampleLibrary = sampleLibrary;
    }

    @PostMapping
    public ResponseEntity<ConversionResponse> convert(@RequestBody ConversionRequest request) {
        ConversionResponse response = conversionService.convert(request);
        return response.success() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    /** Every selectable language: the sample languages first, then the AI-only ones. */
    @GetMapping("/languages")
    public List<ProgrammingLanguage> languages() {
        return sampleLibrary.allLanguages();
    }

    /** The languages grouped as in the dropdowns: with offline samples, and AI model only. */
    @GetMapping("/languages/groups")
    public Map<String, List<ProgrammingLanguage>> languageGroups() {
        return Map.of("samples", sampleLibrary.languages(), "more", sampleLibrary.moreLanguages());
    }

    @GetMapping("/samples")
    public List<CodeSample> samples() {
        return sampleLibrary.samples();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        var model = conversionService.llmName();
        return Map.of(
                "llmAvailable", model.isPresent(),
                "model", model.orElse(""),
                "languages", sampleLibrary.allLanguages().size(),
                "sampleLanguages", sampleLibrary.languages().size(),
                "samples", sampleLibrary.samples().size());
    }
}
