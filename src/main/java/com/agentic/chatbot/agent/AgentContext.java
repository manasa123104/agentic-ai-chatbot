package com.agentic.chatbot.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AgentContext {
    private final String userStory;
    private final Path projectDir;
    private final String llmApiKey;
    private final List<Path> generatedFiles = new ArrayList<>();

    public AgentContext(String userStory, Path projectDir, String llmApiKey) {
        this.userStory = userStory;
        this.projectDir = projectDir;
        this.llmApiKey = llmApiKey;
    }

    public String getUserStory() {
        return userStory;
    }

    public Path getProjectDir() {
        return projectDir;
    }

    public String getLlmApiKey() {
        return llmApiKey;
    }

    public List<Path> getGeneratedFiles() {
        return generatedFiles;
    }

    public void addGeneratedFile(Path path) {
        generatedFiles.add(path);
    }
}
