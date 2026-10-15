package com.agentic.chatbot.stage;

import java.util.Map;

/**
 * Input to one stage bot.
 *
 * @param domain  any business domain, e.g. "Banking", "Logistics", "Healthcare"
 * @param input   requirement, user stories, code, or a run id (Trainer), depending on the stage
 * @param options optional settings: offline=true, language (java, python, go, ... or other + languageName),
 *                automation (java, ruby, both, python, javascript, csharp, playwright-js, playwright-ts, none),
 *                testerPart (unit, automation, both), baseUrl, package, keepModelCode, port, kubernetes, compose,
 *                buildLocally, save (Trainer), stages (Trainer), feedback (Trainer)
 * @param runId   existing run to add files to; blank starts a new run
 */
public record StageRequest(String domain, String input, Map<String, String> options, String runId) {

    public StageRequest {
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    public String option(String key, String fallback) {
        String value = options.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public boolean flag(String key) {
        return Boolean.parseBoolean(options.getOrDefault(key, "false"));
    }

    public StageRequest withInput(String newInput, String newRunId) {
        return new StageRequest(domain, newInput, options, newRunId);
    }

    public StageRequest withOption(String key, String value) {
        Map<String, String> copy = new java.util.LinkedHashMap<>(options);
        if (value == null) {
            copy.remove(key);
        } else {
            copy.put(key, value);
        }
        return new StageRequest(domain, input, copy, runId);
    }
}
