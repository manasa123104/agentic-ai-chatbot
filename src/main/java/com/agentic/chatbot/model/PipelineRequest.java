package com.agentic.chatbot.model;

public class PipelineRequest {
    private String userStory;
    /** Optional widened system prompt from the end user (dynamic BOT model context). */
    private String systemPrompt;
    private boolean runUnitTests = true;
    private boolean runFunctionalTests = true;

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
