package com.agentic.chatbot.model;

/**
 * @param sourceLanguage language id/name, or {@code auto} to detect it from the code
 * @param instructions   optional extra guidance for the converter (e.g. "use streams")
 */
public record ConversionRequest(
        String sourceLanguage,
        String targetLanguage,
        String code,
        String instructions) {
}
