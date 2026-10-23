package com.agentic.chatbot.web;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Code converter labels the input repo and the output repo. */
class ConvertTemplateTest {

    @Test
    void labelsInputRepoAndOutputRepo() throws Exception {
        String html = new String(new ClassPathResource("templates/convert.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(html.contains("Input — the code or repository you want to convert."));
        assertTrue(html.contains("Input repository URL"));
        assertTrue(html.contains("Input · Source code"));
        assertTrue(html.contains("Output · Converted code"));
        assertTrue(html.contains("Output repository URL"));
        assertTrue(html.contains("This GitHub link is the output."));
    }
}
