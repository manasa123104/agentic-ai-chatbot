package com.agentic.chatbot.stage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Result of one stage bot.
 *
 * @param domain    domain id, e.g. "banking" or "healthcare"
 * @param language  target language id, e.g. "java" or "python"
 * @param mode      "llm" (AI model), "template" (offline template), "trained" (recalled approved example) or
 *                  "trainer" (Trainer report)
 * @param output    everything the stage produced as text; code stages use {@code === FILE: path ===} separators
 * @param files     files written under generated-app/stages/{runId}/
 * @param nextInput suggested input for the next stage, empty for the last stage
 */
public record StageResponse(
        boolean success,
        String stage,
        String stageTitle,
        String domain,
        String language,
        String runId,
        String mode,
        String model,
        String output,
        List<StageFile> files,
        List<String> notes,
        String nextStage,
        String nextInput,
        long elapsedMs) {

    /** Same value as {@link #elapsedMs()}, exposed as durationMs in the JSON. */
    @JsonProperty("durationMs")
    public long durationMs() {
        return elapsedMs;
    }

    /** A generated file, path relative to the run folder with forward slashes. */
    public record StageFile(String path, String content) {
    }
}
