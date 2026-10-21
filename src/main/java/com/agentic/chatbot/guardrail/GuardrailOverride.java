package com.agentic.chatbot.guardrail;

/**
 * Per-request choice from the Stage bots On / Off control. Null means the visitor has not chosen, so
 * {@link GuardrailService} follows the server default (on).
 */
public final class GuardrailOverride {

    private static final ThreadLocal<Boolean> ON = new ThreadLocal<>();

    private GuardrailOverride() {
    }

    public static void set(boolean on) {
        ON.set(on);
    }

    public static void clear() {
        ON.remove();
    }

    /** {@code null} when this request did not set a choice. */
    public static Boolean current() {
        return ON.get();
    }
}
