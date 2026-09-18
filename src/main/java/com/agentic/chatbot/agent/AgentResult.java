package com.agentic.chatbot.agent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AgentResult {
    private final boolean success;
    private final String summary;
    private final List<Path> generatedFiles;

    public AgentResult(boolean success, String summary, List<Path> generatedFiles) {
        this.success = success;
        this.summary = summary;
        this.generatedFiles = generatedFiles != null ? generatedFiles : new ArrayList<>();
    }

    public static AgentResult ok(String summary, List<Path> files) {
        return new AgentResult(true, summary, files);
    }

    public static AgentResult fail(String summary) {
        return new AgentResult(false, summary, List.of());
    }

    public boolean isSuccess() {
        return success;
    }

    public String getSummary() {
        return summary;
    }

    public List<Path> getGeneratedFiles() {
        return generatedFiles;
    }
}
