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
    private String userStory;
    private boolean success;
    private String message;
    private String previewUrl;
    private String redirectPath;
    private String outputDirectory;
    private List<BotStepLog> steps = new ArrayList<>();

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
}
