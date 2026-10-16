package com.agentic.chatbot.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/** Guards the stages page markup: Trainer help, the whole-run push after the Trainer, and no push on Deploy. */
class StagesTemplateTest {

    private static String page() throws Exception {
        return new String(new ClassPathResource("templates/stages.html").getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void explainsTrainerAndOffersWholeRunPush() throws Exception {
        String html = page();
        assertTrue(html.contains("What is Trainer?"));
        assertTrue(html.contains("bot-training.json"));
        assertTrue(html.contains("Pipeline complete"));
        assertTrue(html.contains("id=\"pipelineComplete\""));
        assertTrue(html.contains("data-scope=\"run\""));
        assertTrue(html.contains("Your new GitHub link"));
    }

    @Test
    void everyStageHasAWhatIsDefinition() throws Exception {
        String html = page();
        for (String stage : new String[] {"Business Analyst", "Developer", "Tester", "Deploy", "Trainer"}) {
            assertTrue(html.contains("<summary>What is " + stage + "?</summary>"), "missing help for " + stage);
        }
    }

    @Test
    void deployCardHasNoGithubPush() throws Exception {
        String html = page();
        assertTrue(html.contains("s.id == 'developer' or s.id == 'tester'}"));
        assertFalse(html.contains("s.id == 'tester' or s.id == 'deploy'"), "the Deploy card must not get a push panel");
        assertEquals(1, html.split("data-scope=\"run\"", -1).length - 1, "only the Trainer's complete panel pushes the whole run");
    }
}
