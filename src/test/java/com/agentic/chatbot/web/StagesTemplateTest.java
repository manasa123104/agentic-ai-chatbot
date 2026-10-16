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
        assertTrue(html.contains("All 5 stages done &mdash; Push to GitHub"));
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
    void pipelineStripPillsOpenWhatIsDropdowns() throws Exception {
        String html = page();
        assertTrue(html.contains("class=\"pipe-btn\" aria-expanded=\"false\""), "pills must be buttons that expand");
        assertTrue(html.contains("class=\"pipe-chev\""), "pills need a chevron affordance");
        assertTrue(html.contains("id=\"pipeHint\""));
        for (String[] s : new String[][] {{"ba", "BA (Business Analyst)"}, {"developer", "Developer"}, {"tester", "Tester"},
                {"deploy", "Deploy"}, {"trainer", "Trainer"}}) {
            assertTrue(html.contains("id=\"pipeHelp-" + s[0] + "\""), "missing strip dropdown for " + s[0]);
            assertTrue(html.contains("<h3>What is " + s[1] + "?</h3>"), "missing strip heading for " + s[1]);
        }
        assertTrue(html.contains("showPipeHelp"));
    }

    @Test
    void wholeRunPushIsItsOwnSectionAfterTheTrainerCard() throws Exception {
        String html = page();
        int trainerCard = html.indexOf("class=\"convert-card stage-card trainer-card\"");
        int trainerEnd = html.indexOf("</section>", html.indexOf("id=\"exampleList\"", trainerCard));
        int finalPush = html.indexOf("<section class=\"convert-card final-push\" id=\"finalPush\"");
        assertTrue(trainerCard > 0 && finalPush > trainerEnd, "the end push section must come after the Trainer card");
        assertTrue(html.indexOf("id=\"pipelineComplete\"") > finalPush, "the whole-run panel lives in the end section");
        assertTrue(html.indexOf("</main>") > html.indexOf("id=\"pipelineComplete\""));
        String trainer = html.substring(trainerCard, trainerEnd);
        assertFalse(trainer.contains("gh-push-btn"), "no push button inside the Trainer card any more");
        for (String field : new String[] {"gh-repo", "gh-branch", "gh-token", "gh-pull", "gh-newlink-url", "gh-newlink-pr"}) {
            assertTrue(html.substring(finalPush).contains("class=\"" + field), "end panel misses " + field);
        }
        assertTrue(html.contains("function updateFinalPush"));
    }

    @Test
    void deployDropdownsMentionNoPush() throws Exception {
        String html = page();
        int from = html.indexOf("id=\"pipeHelp-deploy\"");
        String strip = html.substring(from, html.indexOf("</article>", from)).toLowerCase();
        assertFalse(strip.contains("push"), "the Deploy dropdown must not offer or mention a push");
        int card = html.indexOf("<summary>What is Deploy?</summary>");
        String help = html.substring(card, html.indexOf("</details>", card)).toLowerCase();
        assertFalse(help.contains("push"), "the Deploy card help must not mention a push");
    }

    @Test
    void deployCardHasNoGithubPush() throws Exception {
        String html = page();
        assertTrue(html.contains("s.id == 'developer' or s.id == 'tester'}"));
        assertFalse(html.contains("s.id == 'tester' or s.id == 'deploy'"), "the Deploy card must not get a push panel");
        assertEquals(1, html.split("data-scope=\"run\"", -1).length - 1, "only the Trainer's complete panel pushes the whole run");
    }
}
