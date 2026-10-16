package com.agentic.chatbot.llm;

import java.util.List;
import java.util.function.Consumer;

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

    /** One earlier message of a conversation: {@code role} is "user" or "assistant". */
    record ChatTurn(String role, String content) {
    }

    /**
     * Multi-turn, ChatGPT-style chat. Clients that can stream call {@code onChunk} with each piece of the answer as it
     * arrives; the default sends the whole conversation as one prompt and delivers the answer in a single chunk.
     * Returns the full answer.
     */
    default String chat(String systemPrompt, List<ChatTurn> turns, java.time.Duration timeout, int maxTokens,
                        Consumer<String> onChunk) {
        String answer = complete(systemPrompt, transcript(turns), timeout, maxTokens);
        if (answer != null && !answer.isEmpty() && onChunk != null) {
            onChunk.accept(answer);
        }
        return answer;
    }

    /** The conversation as plain text, for clients that only take a single prompt. */
    static String transcript(List<ChatTurn> turns) {
        StringBuilder sb = new StringBuilder("Conversation so far (reply only to the last visitor message):\n");
        for (ChatTurn t : turns) {
            sb.append("assistant".equals(t.role()) ? "Assistant: " : "Visitor: ").append(t.content()).append('\n');
        }
        return sb.append("Assistant:").toString();
    }

    /** An image sent along with a prompt: {@code mimeType} such as image/png, {@code base64} without a data: prefix. */
    record ImageInput(String mimeType, String base64) {
    }

    /** True when this client can look at pictures (Gemini and OpenAI vision models). */
    default boolean supportsVision() {
        return false;
    }

    /**
     * Sends the pictures together with the prompt to a vision model and returns its answer. Only call this when
     * {@link #supportsVision()} is true; other clients throw.
     */
    default String describeImages(String systemPrompt, String prompt, List<ImageInput> images,
                                  java.time.Duration timeout, int maxTokens) {
        throw new UnsupportedOperationException("this model cannot look at images");
    }

    /** True for hosted models (Gemini, OpenAI): they take much larger chunks than a small local model. */
    default boolean isCloud() {
        return false;
    }

    /** Short human-readable name of the model behind this client. */
    default String describe() {
        return "the LLM";
    }
}
