package com.agentic.chatbot.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoryAnalyzerTest {

    @Test
    void detectsLoginStoryAndRedirect() {
        var a = StoryAnalyzer.analyze("As a user, I want a login page so that I can access my dashboard.");
        assertEquals(StoryAnalyzer.ScreenType.LOGIN, a.type());
        assertEquals("/login", a.pagePath());
        assertEquals("/dashboard", a.redirectPath());
    }

    @Test
    void extractsCustomRedirectTarget() {
        var a = StoryAnalyzer.analyze("As a user, I want a login page that redirects to the profile page.");
        assertEquals("/profile", a.redirectPath());
    }

    @Test
    void appointmentStoryCreatesBookingPageNotDashboardOverwrite() {
        var a = StoryAnalyzer.analyze("As a patient, I want an appointment booking page so that I can schedule a visit.");
        assertEquals(StoryAnalyzer.Domain.APPOINTMENT, a.domain());
        assertEquals("/appointment-booking", a.pagePath());
        assertNull(a.redirectPath());
    }

    @Test
    void toPascalCaseWorks() {
        assertEquals("LoginPage", StoryAnalyzer.toPascalCase("login-page"));
    }
}
