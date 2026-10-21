package com.agentic.chatbot.guardrail;

/** Thrown when a guardrail blocks an MCP tool call or a sensitive action in enforce mode. */
public class GuardrailDeniedException extends IllegalArgumentException {

    public static final String CODE = "guardrail_denied";

    private final String ruleId;
    private final GuardDecision decision;

    public GuardrailDeniedException(GuardDecision decision) {
        super(decision.reason() == null || decision.reason().isBlank()
                ? "Blocked by BOT guardrail."
                : "Blocked by BOT guardrail (" + decision.ruleId() + "): " + decision.reason());
        this.ruleId = decision.ruleId() == null ? "" : decision.ruleId();
        this.decision = decision;
    }

    public String ruleId() {
        return ruleId;
    }

    public GuardDecision decision() {
        return decision;
    }

    public String code() {
        return CODE;
    }
}
