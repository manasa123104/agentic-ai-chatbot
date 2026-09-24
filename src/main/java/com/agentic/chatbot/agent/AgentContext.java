package com.agentic.chatbot.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AgentContext {
    private final String userStory;
    private final Path projectDir;
    private final String llmApiKey;
    private final String systemPrompt;
    private String refinedStory;
    private String resolvedSystemPrompt;
    private final List<Path> generatedFiles = new ArrayList<>();

    public AgentContext(String userStory, Path projectDir, String llmApiKey) {
        this(userStory, projectDir, llmApiKey, null);
    }

    public AgentContext(String userStory, Path projectDir, String llmApiKey, String systemPrompt) {
        this.userStory = userStory;
        this.projectDir = projectDir;
        this.llmApiKey = llmApiKey;
        this.systemPrompt = systemPrompt;
        this.refinedStory = userStory;
    }

    public String getUserStory() {
        return userStory;
    }

    /** Prefer BA-refined story for code generation when available. */
    public String storyForDevelopment() {
        if (refinedStory != null && !refinedStory.isBlank()) {
            return refinedStory;
        }
        return userStory;
    }

    public Path getProjectDir() {
        return projectDir;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public String getRefinedStory() {
        return refinedStory;
    }

    public void setRefinedStory(String refinedStory) {
        this.refinedStory = refinedStory;
    }

    public String getResolvedSystemPrompt() {
        return resolvedSystemPrompt;
    }

    public void setResolvedSystemPrompt(String resolvedSystemPrompt) {
        this.resolvedSystemPrompt = resolvedSystemPrompt;
    }

    public List<Path> getGeneratedFiles() {
        return generatedFiles;
    }

    public void addGeneratedFile(Path path) {
        generatedFiles.add(path);
    }
}
