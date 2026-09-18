package com.agentic.chatbot.model;

import java.util.ArrayList;
import java.util.List;

public class BotStepLog {
    private String botName;
    private boolean success;
    private String summary;
    private List<String> generatedFiles = new ArrayList<>();
    private long durationMs;

    public BotStepLog() {
    }

    public BotStepLog(String botName, boolean success, String summary, List<String> generatedFiles, long durationMs) {
        this.botName = botName;
        this.success = success;
        this.summary = summary;
        this.generatedFiles = generatedFiles != null ? generatedFiles : new ArrayList<>();
        this.durationMs = durationMs;
    }

    public String getBotName() {
        return botName;
    }

    public void setBotName(String botName) {
        this.botName = botName;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getGeneratedFiles() {
        return generatedFiles;
    }

    public void setGeneratedFiles(List<String> generatedFiles) {
        this.generatedFiles = generatedFiles;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
