package com.agentic.chatbot.model;

public class PipelineRequest {
    private String userStory;
    /** Optional widened system prompt from the end user (dynamic BOT model context). */
    private String systemPrompt;
    private boolean runUnitTests = true;
    private boolean runFunctionalTests = true;
    /** Browser-chosen id of this run; lets the Stop button and a later Resume find it. */
    private String runId;
    /** True to continue the run with this id from its last finished bot instead of starting over. */
    private boolean resume;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public boolean isResume() {
        return resume;
    }

    public void setResume(boolean resume) {
        this.resume = resume;
    }

    public String getUserStory() {
        return userStory;
    }

    public void setUserStory(String userStory) {
        this.userStory = userStory;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public boolean isRunUnitTests() {
        return runUnitTests;
    }

    public void setRunUnitTests(boolean runUnitTests) {
        this.runUnitTests = runUnitTests;
    }

    public boolean isRunFunctionalTests() {
        return runFunctionalTests;
    }

    public void setRunFunctionalTests(boolean runFunctionalTests) {
        this.runFunctionalTests = runFunctionalTests;
    }
}
