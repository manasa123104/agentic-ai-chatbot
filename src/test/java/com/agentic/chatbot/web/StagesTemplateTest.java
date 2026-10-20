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
        assertTrue(html.contains("All 6 stages done &mdash; Push to GitHub"));
        assertTrue(html.contains("id=\"pipelineComplete\""));
        assertTrue(html.contains("id=\"finalPushStatus\""));
        assertTrue(html.contains("id=\"finalPushBtn\""));
        assertTrue(html.contains("data-scope=\"run\""));
        assertTrue(html.contains("Your new GitHub link"));
        // always on the page (not gated by a hidden attribute on the section)
        assertFalse(html.contains("id=\"finalPush\" hidden"), "final push must be visible without waiting for JS");
    }

    @Test
    void everyStageHasAWhatIsDefinition() throws Exception {
        String html = page();
        for (String stage : new String[] {"Business Analyst", "Developer", "Tester", "Data Analyst", "Deploy", "Trainer"}) {
            assertTrue(html.contains("<summary>What is " + stage + "?</summary>")
                            || html.contains("<h3>What is " + stage + "?</h3>")
                            || html.contains("<h3>What is BA (Business Analyst)?</h3>")
                            || (stage.equals("Data Analyst") && html.contains("What is Data Analyst?")),
                    "missing help for " + stage);
        }
    }

    @Test
    void pipelineStripPillsOpenWhatIsDropdowns() throws Exception {
        String html = page();
        assertTrue(html.contains("class=\"pipe-btn\" aria-expanded=\"false\""), "pills must be buttons that expand");
        assertTrue(html.contains("class=\"pipe-chev\""), "pills need a chevron affordance");
        assertTrue(html.contains("id=\"pipeHint\""));
        for (String[] s : new String[][] {{"ba", "BA (Business Analyst)"}, {"developer", "Developer"}, {"tester", "Tester"},
                {"data-analyst", "Data Analyst"}, {"deploy", "Deploy"}, {"trainer", "Trainer"}}) {
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
    void testerCardHasNoGithubPushMarkup() throws Exception {
        String html = page();
        int tabs = html.indexOf("<details class=\"help-tip\" open=\"open\" th:if=\"${s.id == 'tester'}\">");
        assertTrue(tabs > 0);
        String help = html.substring(tabs, html.indexOf("</details>", tabs)).toLowerCase();
        assertFalse(help.contains("push") || help.contains("github"), "Tester card help must not mention a push");
        int strip = html.indexOf("id=\"pipeHelp-tester\"");
        String stripHelp = html.substring(strip, html.indexOf("</article>", strip)).toLowerCase();
        assertFalse(stripHelp.contains("push") || stripHelp.contains("github"), "the Tester dropdown must not mention a push");
        assertFalse(html.contains("'tester' ? 'Pushes the code plus its tests"), "no Tester push hint text");
        assertTrue(html.contains("th:if=\"${s.id == 'developer'}\""));
        assertTrue(html.contains("class=\"gh-panel\"") || html.contains("class=\"gh-panel "),
                "Developer push panel present");
        assertTrue(html.contains("gh-complete"), "whole-run push panel present");
    }

    @Test
    void deployCardHasNoGithubPush() throws Exception {
        String html = page();
        assertTrue(html.contains("th:if=\"${s.id == 'developer'}\"\n             th:attr=\"data-scope=${s.id}\"")
                || html.contains("th:if=\"${s.id == 'developer'}\"\r\n             th:attr=\"data-scope=${s.id}\""),
                "only the Developer card keeps a push panel");
        assertFalse(html.contains("s.id == 'tester' or s.id == 'deploy'"), "the Deploy card must not get a push panel");
        assertFalse(html.contains("s.id == 'developer' or s.id == 'tester'"), "the Tester card must not get a push panel");
        assertEquals(1, html.split("data-scope=\"run\"", -1).length - 1, "only the Trainer's complete panel pushes the whole run");
    }

    @Test
    void dataAnalystHelpAndInputsAreWired() throws Exception {
        String html = page();
        assertTrue(html.contains("'data-analyst':"));
        assertTrue(html.contains("analytics/STATS.md"));
        assertTrue(html.contains("next === 'deploy' || next === 'data-analyst'")
                || html.contains("developerCodeForLaterStages"));
        assertTrue(html.contains("Running Data Analyst and Deploy together"));
        assertTrue(html.contains("Promise.all"));
        assertTrue(html.contains("High-level overview"));
        assertTrue(html.contains("Quality snapshot"));
        assertTrue(html.contains("Daily outcomes"));
        assertTrue(html.contains("Failure reasons"));
        assertTrue(html.contains("nine charts"));
    }

    @Test
    void storyUploadShowsLimitsAboveThePicker() throws Exception {
        String html = page();
        int limits = html.indexOf("class=\"ba-import-limits\"");
        int upload = html.indexOf("id=\"storyFiles\"");
        assertTrue(limits > 0 && upload > limits, "upload limits must sit above the file picker");
        assertTrue(html.contains("55,000"));
        assertTrue(html.contains("5 MB"));
        assertTrue(html.contains("1,000 files"));
        assertTrue(html.contains("100 MB"));
        assertFalse(html.contains("about 100 stories is fine"));
    }
}
