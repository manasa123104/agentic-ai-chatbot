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
    /** Total server-side run time in milliseconds (null when unknown). */
    private Long durationMs;
    private String codeUrl = "/code";
    /** BA-refined user story text included with the generated website code. */
    private String refinedUserStory;
    /** Relative paths of generated Java / key source files. */
    private List<String> generatedCodeFiles = new ArrayList<>();
    /** Backend language of the generated site: id ("python") and label ("Python"); null when unknown. */
    private String language;
    private String languageLabel;
    /** Downloads of the generated site (user stories, code in the language, whole site). */
    private String storiesDownloadUrl;
    private String codeDownloadUrl;
    private String siteDownloadUrl;
    private List<BotStepLog> steps = new ArrayList<>();
    /** Set when the chat message was a code-conversion request instead of a website story. */
    private ConversionResponse conversion;
    /** Id of the run (echoed from the request); use it to resume. */
    private String runId;
    /** True when the user pressed Stop and the run ended early with its progress saved. */
    private boolean stopped;
    /** True when the run can continue from its last finished bot. */
    private boolean resumable;
    /** Bots that finished and will not run again on resume. */
    private int completedSteps;

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public boolean isStopped() { return stopped; }
    public void setStopped(boolean stopped) { this.stopped = stopped; }
    public boolean isResumable() { return resumable; }
    public void setResumable(boolean resumable) { this.resumable = resumable; }
    public int getCompletedSteps() { return completedSteps; }
    public void setCompletedSteps(int completedSteps) { this.completedSteps = completedSteps; }

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

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getHistoryId() {
        return historyId;
    }

    public void setHistoryId(String historyId) {
        this.historyId = historyId;
    }

    public String getCodeUrl() {
        return codeUrl;
    }

    public void setCodeUrl(String codeUrl) {
        this.codeUrl = codeUrl;
    }

    public String getRefinedUserStory() {
        return refinedUserStory;
    }

    public void setRefinedUserStory(String refinedUserStory) {
        this.refinedUserStory = refinedUserStory;
    }

    public List<String> getGeneratedCodeFiles() {
        return generatedCodeFiles;
    }

    public void setGeneratedCodeFiles(List<String> generatedCodeFiles) {
        this.generatedCodeFiles = generatedCodeFiles != null ? generatedCodeFiles : new ArrayList<>();
    }

    public List<BotStepLog> getSteps() {
        return steps;
    }

    public void setSteps(List<BotStepLog> steps) {
        this.steps = steps;
    }

    public ConversionResponse getConversion() {
        return conversion;
    }

    public void setConversion(ConversionResponse conversion) {
        this.conversion = conversion;
    }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getLanguageLabel() { return languageLabel; }
    public void setLanguageLabel(String languageLabel) { this.languageLabel = languageLabel; }
    public String getStoriesDownloadUrl() { return storiesDownloadUrl; }
    public void setStoriesDownloadUrl(String storiesDownloadUrl) { this.storiesDownloadUrl = storiesDownloadUrl; }
    public String getCodeDownloadUrl() { return codeDownloadUrl; }
    public void setCodeDownloadUrl(String codeDownloadUrl) { this.codeDownloadUrl = codeDownloadUrl; }
    public String getSiteDownloadUrl() { return siteDownloadUrl; }
    public void setSiteDownloadUrl(String siteDownloadUrl) { this.siteDownloadUrl = siteDownloadUrl; }
}