package com.agentic.chatbot.model;

import java.util.ArrayList;
import java.util.List;

public class PipelineResponse {
    private boolean success;
    private String message;
    private String outputDirectory;
    private String previewUrl;
    private String redirectPath;
    private String historyId;
    private List<BotStepLog> steps = new ArrayList<>();

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getPreviewUrl() {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl) {
        this.previewUrl = previewUrl;
    }

    public String getRedirectPath() {
        return redirectPath;
    }

    public void setRedirectPath(String redirectPath) {
        this.redirectPath = redirectPath;
    }

    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }

    public List<BotStepLog> getSteps() {
        return steps;
    }

    public void setSteps(List<BotStepLog> steps) {
        this.steps = steps;
    }
}
