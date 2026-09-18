package com.agentic.chatbot.llm;

public interface LlmClient {
    boolean isAvailable();

    String complete(String systemPrompt, String userPrompt);
}
