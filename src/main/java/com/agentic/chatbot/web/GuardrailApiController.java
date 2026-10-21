package com.agentic.chatbot.web;

import com.agentic.chatbot.guardrail.GuardrailOverride;
import com.agentic.chatbot.guardrail.GuardrailOverrideFilter;
import com.agentic.chatbot.guardrail.GuardrailService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Guardrail report card. Visitors can turn checks Off for their own session. */
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

    @PostMapping
    public Map<String, Object> setEnabled(@RequestBody(required = false) Map<String, Object> body, HttpSession session) {
        boolean on = parseEnabled(body == null ? null : body.get("enabled"));
        session.setAttribute(GuardrailOverrideFilter.SESSION_KEY, on);
        GuardrailOverride.set(on);
        return guardrails.status();
    }

    static boolean parseEnabled(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        Boolean parsed = GuardrailOverrideFilter.parse(value == null ? null : String.valueOf(value));
        return parsed == null || parsed;
    }
}
