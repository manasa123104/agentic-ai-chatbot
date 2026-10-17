package com.agentic.chatbot.guardrail;

/**
 * Outcome of a guardrail check on an MCP tool call or a sensitive BOT action.
 *
 * @param allowed whether the action may proceed (always true in observe mode, even when {@code wouldDeny})
 * @param wouldDeny true when the rule would deny under enforce mode
 * @param reason human-readable explanation (empty when clean)
 * @param ruleId short id for logs / receipts, e.g. {@code secrets} or {@code repo-host}
 */
public record GuardDecision(boolean allowed, boolean wouldDeny, String reason, String ruleId) {

    public static GuardDecision allow() {
        return new GuardDecision(true, false, "", "");
    }

    public static GuardDecision deny(String ruleId, String reason) {
        return new GuardDecision(false, true, reason == null ? "" : reason, ruleId == null ? "" : ruleId);
    }

    /** Observe mode: keep the denial reason but still allow the call. */
    public GuardDecision asObserved() {
        return wouldDeny ? new GuardDecision(true, true, reason, ruleId) : this;
    }
}
