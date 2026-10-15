package com.agentic.chatbot.stage;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.llm.LocalLlmDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Picks the model for the stage bots: Gemini when GEMINI_API_KEY is set, then OpenAI (OPENAI_API_KEY), otherwise a local Ollama model.
 */
@Component
public class StageLlm {

    private final LlmClient llmClient;
    private final LocalLlmDetector localLlm;

    @Autowired
    public StageLlm(LlmClient llmClient, LocalLlmDetector localLlm) {
        this.llmClient = llmClient;
        this.localLlm = localLlm;
    }

    /** For tests: a fixed client, or none for offline. */
    public StageLlm(LlmClient fixed) {
        this(fixed, null);
    }

    public Optional<LlmClient> active() {
        if (llmClient != null && llmClient.isAvailable()) {
            return Optional.of(llmClient);
        }
        return localLlm == null ? Optional.empty() : localLlm.client();
    }
}
