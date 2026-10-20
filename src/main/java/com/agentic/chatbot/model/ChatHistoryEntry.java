package com.agentic.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One saved chatbot query + pipeline result.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatHistoryEntry {
    private static final DateTimeFormatter LABEL_FMT =
            DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault());

    private String id;
    private Instant createdAt;
    /** home, convert or stages — where the request was sent. */
    private String source;
    private String userStory;
    private boolean success;
    private String message;
    private String previewUrl;
    private String redirectPath;
    private String outputDirectory;
    private String jobId;
    private String runId;
    private List<BotStepLog> steps = new ArrayList<>();
    private ConversionResponse conversion;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /** Sidebar-friendly timestamp (not persisted). */
    @JsonIgnore
    public String getCreatedLabel() {
        return createdAt == null ? "" : LABEL_FMT.format(createdAt);
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    /** Sidebar label for the workspace that produced this result. */
    public String getSourceLabel() {
        if ("convert".equals(source)) {
            return "Code converter";
        }
        if ("stages".equals(source)) {
            return "Stage bots";
        }
        return "";
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getUserStory() {
        return userStory;
    }

    public void setUserStory(String userStory) {
        this.userStory = userStory;
    }

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

    public String getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(String outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public List<BotStepLog> getSteps() {
        return steps;
    }

    public void setSteps(List<BotStepLog> steps) {
        this.steps = steps != null ? steps : new ArrayList<>();
    }

    public ConversionResponse getConversion() {
        return conversion;
    }

    public void setConversion(ConversionResponse conversion) {
        this.conversion = conversion;
    }
}
