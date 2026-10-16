package com.agentic.chatbot.agent;

import com.agentic.chatbot.model.BotStepLog;

/**
 * Hears about a pipeline run while it happens, so a chat can show "Business Analyst done, Developer working..."
 * instead of waiting for the whole run. All methods are optional.
 */
public interface PipelineListener {

    /** A bot is about to start. {@code index} counts from 1; {@code total} includes bots already finished earlier. */
    default void stepStarted(String botName, int index, int total) {
    }

    /** A bot finished (successfully or not). */
    default void stepFinished(BotStepLog step, int index, int total) {
    }

    /** Listener that ignores everything. */
    PipelineListener NONE = new PipelineListener() {
    };
}
