package com.agentic.chatbot.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Home chat offers conversion and the 6-stage pipeline, not website generation chips. */
class HomeTemplateTest {

    @Test
    void homeDoesNotOfferWebsiteDevelopment() throws Exception {
        String html = new String(new ClassPathResource("templates/index.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String markup = html.contains("<script") ? html.substring(0, html.indexOf("<script")) : html;
        assertTrue(markup.contains("/convert"));
        assertTrue(markup.contains("/stages"));
        assertTrue(markup.contains("start-convert"));
        assertTrue(markup.contains("start-stages"));
        assertTrue(markup.contains("chat-composer"));
        assertFalse(markup.contains("Code conversion"));
        assertFalse(markup.contains("Python → Java"));
        assertFalse(markup.contains("FizzBuzz"));
        assertFalse(markup.contains("Edit generated site"));
        assertFalse(markup.contains("Websites in your language"));
        assertFalse(markup.contains("on-page chatbot"));
        assertFalse(markup.contains("Open website"));
        assertFalse(markup.contains("href=\"/edit\""));
        assertFalse(markup.contains("Describe a website"));
        assertFalse(markup.contains("user stories + website"));
        assertFalse(markup.contains("System prompt"));
        assertFalse(markup.contains("From your prompt"));
        assertFalse(markup.contains("composer-tools"));
        assertFalse(markup.contains("OliveGrove"));
    }
}
