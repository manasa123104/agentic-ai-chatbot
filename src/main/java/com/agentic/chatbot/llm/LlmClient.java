package com.agentic.chatbot.llm;

public interface LlmClient {
    boolean isAvailable();

    String complete(String systemPrompt, String userPrompt);

    /**
     * Like {@link #complete(String, String)} but asks the client to give up after {@code timeout} and to stop after
     * about {@code maxTokens} answer tokens. Clients that cannot honour these limits just call the plain method.
     */
    default String complete(String systemPrompt, String userPrompt, java.time.Duration timeout, int maxTokens) {
        return complete(systemPrompt, userPrompt);
    }

    /** Short human-readable name of the model behind this client. */
    default String describe() {
        return "the LLM";
    }
}
