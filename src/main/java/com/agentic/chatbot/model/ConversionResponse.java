package com.agentic.chatbot.model;

/**
 * @param mode          {@code sample-library} when a built-in sample was matched offline,
 *                      {@code llm} when the model translated the code, {@code passthrough} when
 *                      source and target are the same language
 * @param businessLogic plain-English description of what the code does for the business, or
 *                      {@code null} when it was not requested
 */
public record ConversionResponse(
        boolean success,
        String message,
        String mode,
        String sourceLanguageId,
        String sourceLanguage,
        String targetLanguageId,
        String targetLanguage,
        String convertedCode,
        String explanation,
        String runCommand,
        String outputFile,
        String businessLogic) {

    public static ConversionResponse failure(String message) {
        return new ConversionResponse(false, message, null, null, null, null, null, null, null, null, null, null);
    }

    public ConversionResponse withBusinessLogic(String text) {
        return new ConversionResponse(success, message, mode, sourceLanguageId, sourceLanguage, targetLanguageId,
                targetLanguage, convertedCode, explanation, runCommand, outputFile, text);
    }
}
