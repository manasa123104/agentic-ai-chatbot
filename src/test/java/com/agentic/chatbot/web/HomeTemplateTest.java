package com.agentic.chatbot.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Home offers converter and Stage bots. The prompt bar and website chips stay off the start screen. */
class HomeTemplateTest {

    @Test
    void homeDoesNotOfferWebsiteDevelopment() throws Exception {
        String html = new String(new ClassPathResource("templates/index.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String markup = html.contains("<script") ? html.substring(0, html.indexOf("<script")) : html;
        assertTrue(markup.contains("Welcome to"));
        assertTrue(markup.contains("welcome-links"));
        assertTrue(markup.contains("/convert"));
        assertTrue(markup.contains("/stages"));
        assertTrue(markup.contains("class=\"chat-composer\" hidden"));
        assertFalse(markup.contains("start-convert"));
        assertFalse(markup.contains("start-stages"));
        assertFalse(markup.contains("Code conversion"));
        assertFalse(markup.contains("Python → Java"));
        assertFalse(markup.contains("FizzBuzz"));
        assertFalse(markup.contains("Edit generated site"));
        assertFalse(markup.contains("Websites in your language"));
        assertFalse(markup.contains("on-page chatbot"));
        assertFalse(markup.contains("href=\"/edit\""));
        assertFalse(markup.contains("composer-tools"));
        assertFalse(markup.contains("OliveGrove"));
        assertFalse(markup.contains("New prompt"));
    }
}
