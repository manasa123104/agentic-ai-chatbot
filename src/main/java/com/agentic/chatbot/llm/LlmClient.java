package com.agentic.chatbot.llm;

public interface LlmClient {
    boolean isAvailable();

    String complete(String systemPrompt, String userPrompt);

    /** Short human-readable name of the model behind this client. */
    default String describe() {
        return "the LLM";
    }
}
