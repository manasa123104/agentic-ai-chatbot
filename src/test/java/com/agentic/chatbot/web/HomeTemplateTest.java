package com.agentic.chatbot.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Home is a flash-card landing for converter and Stage bots. */
class HomeTemplateTest {

    @Test
    void homeDoesNotOfferWebsiteDevelopment() throws Exception {
        String html = new String(new ClassPathResource("templates/index.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        String markup = html.contains("<script") ? html.substring(0, html.indexOf("<script")) : html;
        assertTrue(markup.contains("Welcome to BOT"));
        assertTrue(markup.contains("flash-card"));
        assertTrue(markup.contains("Code converter"));
        assertTrue(markup.contains("Stage bots"));
        assertTrue(markup.contains("Delivery stages"));
        assertTrue(markup.contains("/convert"));
        assertTrue(markup.contains("/stages"));
        assertTrue(markup.contains("start-convert.jpg"));
        assertTrue(markup.contains("start-stages.jpg"));
        assertTrue(markup.contains("home-guard"));
        assertTrue(markup.contains("Guardrail"));
        assertTrue(markup.contains("On by default"));
        assertTrue(markup.contains("Turn it off"));
        assertTrue(markup.contains("class=\"chat-composer\" hidden"));
        assertFalse(markup.contains("New prompt"));
        assertFalse(markup.contains("Choose a workspace"));
        assertFalse(markup.contains("Websites in your language"));
        assertFalse(markup.contains("OliveGrove"));
        assertFalse(markup.contains("<ol>"));
        assertFalse(markup.contains("From idea to release"));
    }
}
