package com.agentic.chatbot.model;

/**
 * @param sourceLanguage language id/name, or {@code auto} to detect it from the code
 * @param instructions   optional extra guidance for the converter (e.g. "use streams")
 * @param addComments    ask the model to explain the converted code with comments
 * @param explainLogic   also describe the business logic of the code in plain English
 */
public record ConversionRequest(
        String sourceLanguage,
        String targetLanguage,
        String code,
        String instructions,
        boolean addComments,
        boolean explainLogic) {

    public ConversionRequest(String sourceLanguage, String targetLanguage, String code, String instructions) {
        this(sourceLanguage, targetLanguage, code, instructions, false, false);
    }
}
