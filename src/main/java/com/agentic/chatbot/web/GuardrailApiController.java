package com.agentic.chatbot.web;

import com.agentic.chatbot.guardrail.GuardrailService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Always-on Guardrail report card. */
@RestController
@RequestMapping("/api/guardrail")
public class GuardrailApiController {

    private final GuardrailService guardrails;

    public GuardrailApiController(GuardrailService guardrails) {
        this.guardrails = guardrails;
    }

    @GetMapping
    public Map<String, Object> status() {
        return guardrails.status();
    }
}
