package com.agentic.chatbot.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiLlmClientTest {

    @Test
    void localEndpointsWorkWithoutAnApiKey() {
        assertTrue(new OpenAiLlmClient("", "http://localhost:11434/v1", "llama3.1", true).isAvailable());
        assertTrue(new OpenAiLlmClient("", "http://127.0.0.1:1234/v1", "local", true).isAvailable());
    }

    @Test
    void hostedEndpointsNeedAnApiKey() {
        assertFalse(new OpenAiLlmClient("", "https://api.openai.com/v1", "gpt-4o-mini", true).isAvailable());
        assertTrue(new OpenAiLlmClient("sk-test", "https://api.openai.com/v1", "gpt-4o-mini", true).isAvailable());
        assertFalse(new OpenAiLlmClient("sk-test", "https://api.openai.com/v1", "gpt-4o-mini", false).isAvailable());
    }
}
