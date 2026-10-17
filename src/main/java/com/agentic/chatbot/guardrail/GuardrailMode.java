package com.agentic.chatbot.guardrail;

/** How BOT applies MCP / action guardrails. */
public enum GuardrailMode {
    /** Log decisions but never block. */
    OBSERVE,
    /** Block denied actions before they run. */
    ENFORCE
}
