package com.agentic.chatbot.model;

import java.util.Map;

/** One reference program written in every supported language, keyed by language id. */
public record CodeSample(
        String id,
        String title,
        String description,
        Map<String, String> code) {
}
