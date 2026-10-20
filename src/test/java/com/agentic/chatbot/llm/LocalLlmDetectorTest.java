package com.agentic.chatbot.llm;

import com.agentic.chatbot.llm.LocalLlmDetector.InstalledModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalLlmDetectorTest {

    private static final long GB = 1024L * 1024 * 1024;

    @Test
    void prefersTheLargestCodingModelThatFits() {
        List<InstalledModel> installed = List.of(
                new InstalledModel("llama3.1:8b", 5 * GB),
                new InstalledModel("qwen2.5-coder:1.5b", GB),
                new InstalledModel("qwen2.5-coder:7b", 5 * GB),
                new InstalledModel("qwen2.5-coder:32b", 20 * GB),
                new InstalledModel("nomic-embed-text:latest", GB / 4));

        assertEquals(Optional.of("qwen2.5-coder:7b"), LocalLlmDetector.chooseModel(installed, ""));
    }

    @Test
    void fallsBackToTheLargestGeneralModelAndHonoursTheConfiguredOne() {
        List<InstalledModel> installed = List.of(
                new InstalledModel("phi3:mini", 2 * GB),
                new InstalledModel("llama3.1:latest", 5 * GB));

        assertEquals(Optional.of("llama3.1:latest"), LocalLlmDetector.chooseModel(installed, null));
        assertEquals(Optional.of("phi3:mini"), LocalLlmDetector.chooseModel(installed, "PHI3:mini"));
        assertEquals(Optional.of("llama3.1:latest"), LocalLlmDetector.chooseModel(installed, "llama3.1"));
        assertEquals(Optional.of("llama3.1:latest"), LocalLlmDetector.chooseModel(installed, "not-installed"));
        assertEquals(Optional.empty(), LocalLlmDetector.chooseModel(List.of(), "phi3:mini"));
    }

    @Test
    void sizesTheContextWindowToThePrompt() {
        assertEquals(1024, OllamaLlmClient.contextSize(500));
        assertEquals(4096, OllamaLlmClient.contextSize(6_000));
        assertEquals(32768, OllamaLlmClient.contextSize(200_000));
    }
}
