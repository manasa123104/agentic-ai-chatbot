package com.agentic.chatbot.model;

public class PipelineRequest {
    private String userStory;
    private boolean runUnitTests = true;
    private boolean runFunctionalTests = true;

    public String getUserStory() {
        return userStory;
    }

    public void setUserStory(String userStory) {
        this.userStory = userStory;
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
