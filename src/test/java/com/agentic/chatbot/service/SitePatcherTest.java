package com.agentic.chatbot.service;

import com.agentic.chatbot.llm.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bot edits the generated site on disk: surgical changes that stack, and survive a rebuild. */
public class SitePatcherTest {

    public static final String HOME = """
            <!DOCTYPE html><html><head><title>Crumb Hearth · Overview</title>
            <link href="https://fonts.googleapis.com/css2?family=Poppins:wght@400" rel="stylesheet"/>
            <style>:root{--bg:#ffffff;--ink:#111111;--accent:#1a73e8;--soft:#eef4ff;--dark:#0b1220;--muted:#64748b;--r:30px;}.hero{padding:4rem}</style></head><body>
            <header class="top"><a class="brand" href="/site/home"><span class="mark" style="background-image:url('/uploads/chat/logo.png')"></span>Crumb Hearth</a>
            <nav class="nav">
              <a href="/site/home" class="active">Overview</a>
              <a href="/site/pricing" class="">Plans</a>
              <a href="/site/contact" class="">Talk to us</a>
            </nav>
            <a class="cta" href="/site/contact">Book now</a></header>
            <section class="hero center"><div><h1>Welcome to Crumb Hearth</h1>
            <p>Fresh bread every day.</p></div><div class="visual"><img src="/uploads/chat/hero.png" alt="x"/></div></section>
            <section class="section"><div class="grid-3"><a class="card" href="/site/pricing"><div class="card-media"><img class="card-img" src="/uploads/chat/card.png" alt="a" loading="lazy"/></div><div class="card-body"><h3>Plans</h3></div></a></div></section>
            <footer class="site-footer"><div><strong>Crumb Hearth</strong></div><div><a href="/site/pricing">Plans</a></div></footer></body></html>
            """;

    static final String PRICING = HOME.replace("<h1>Welcome to Crumb Hearth</h1>", "<h1>Plans</h1>")
            .replace("Crumb Hearth · Overview", "Crumb Hearth · Plans");

    @TempDir
    Path dir;

    private Path templates() {
        return dir.resolve("src/main/resources/templates");
    }

    private String page(String name) throws IOException {
        return Files.readString(templates().resolve(name + ".html"), StandardCharsets.UTF_8);
    }

    @BeforeEach
    void site() throws IOException {
        Files.createDirectories(templates());
        Files.writeString(templates().resolve("home.html"), HOME);
        Files.writeString(templates().resolve("pricing.html"), PRICING);
        Files.writeString(dir.resolve("SITE_META.json"), "{\"brand\":\"Crumb Hearth\",\"language\":\"java\"}");
        Files.writeString(dir.resolve("USER_STORY.md"), "# Big site generated\n\nBuild a bakery website called Crumb Hearth\n\n"
                + "Brand: Crumb Hearth · language: Java\n\nMenu: Overview, Plans, Talk to us\n");
    }

    private SitePatcher patcher() {
        return new SitePatcher(dir.toString(), null);
    }

    @Test
    void theHeroGetsShorterStepByStepAndTallerAgainOnTheSamePages() throws Exception {
        var p = patcher();
        var first = p.apply("make the hero shorter", false);
        assertTrue(first.changed(), first.toString());
        assertTrue(first.changes().get(0).contains("shorter"), first.toString());
        assertEquals(1, page("home").split("id=\"bot-edits\"", -1).length - 1);
        assertTrue(page("pricing").contains("id=\"bot-edits\""));
        assertTrue(page("home").contains("min-height:170px"));

        assertTrue(p.apply("make the hero even shorter", false).changed());
        assertTrue(page("home").contains("min-height:110px"));
        assertFalse(p.apply("hero smaller please", false).changed(), "already at the limit: it says so instead");

        p.apply("make the hero taller", false);
        p.apply("make the hero taller", false);
        assertFalse(page("home").contains("id=\"bot-edits\""), "back to the generated size: the block is gone");
    }

    @Test
    void renamingAMenuItemChangesMenuFooterCardsAndTitles() throws Exception {
        var out = patcher().apply("rename Plans to Pricing", false);
        assertTrue(out.changed(), out.toString());
        for (String name : new String[]{"home", "pricing"}) {
            String html = page(name);
            assertFalse(html.contains(">Plans<"), name + ": " + html);
            assertTrue(html.contains(">Pricing<"), name);
            assertTrue(html.contains("href=\"/site/pricing\""), "links keep pointing at the page");
        }
        assertTrue(page("pricing").contains("Crumb Hearth · Pricing</title>"));
        assertTrue(Files.readString(dir.resolve("USER_STORY.md")).contains("Menu: Overview, Pricing, Talk to us"));
        assertTrue(patcher().menuLabels().contains("Pricing"));
    }

    @Test
    void theHeadlineAndTheTopButtonAreRewritten() throws Exception {
        var p = patcher();
        assertTrue(p.apply("change the headline to \"Fresh bread, baked daily\"", false).changed());
        assertTrue(page("home").contains("<h1>Fresh bread, baked daily</h1>"));
        assertTrue(p.apply("change the cta to Order now", false).changed());
        assertTrue(page("home").contains(">Order now</a>") && page("pricing").contains(">Order now</a>"));
        var ask = p.apply("change the headline", false);
        assertTrue(ask.handled() && !ask.changed() && ask.clarify().contains("What should the headline say"), ask.toString());
    }

    @Test
    void renamingTheSiteTouchesEveryPlaceAndTheStoredFiles() throws Exception {
        var out = patcher().apply("rename the site to Golden Crust", false);
        assertTrue(out.changed(), out.toString());
        assertFalse(page("home").contains("Crumb Hearth"));
        assertTrue(page("home").contains("Golden Crust"));
        assertTrue(Files.readString(dir.resolve("SITE_META.json")).contains("Golden Crust"));
        assertEquals("Golden Crust", patcher().brand());
    }

    @Test
    void aContactFormIsAddedOnceAndPostsToTheContactPage() throws Exception {
        var p = patcher();
        var out = p.apply("add a contact form", false);
        assertTrue(out.changed(), out.toString());
        String home = page("home");
        assertTrue(home.contains("data-bot-section=\"contact-form\"") && home.contains("action=\"/site/contact\""));
        assertTrue(home.indexOf("contact-form") < home.indexOf("<footer"), "goes in front of the footer");
        var again = p.apply("add a contact form", false);
        assertFalse(again.changed());
        assertTrue(again.clarify().contains("already has a contact form"), again.toString());
    }

    @Test
    void menuItemsCanBeRemovedAndVagueRequestsGetAQuestion() throws Exception {
        var p = patcher();
        assertTrue(p.apply("remove Plans from the menu", false).changed());
        assertEquals(java.util.List.of("Overview", "Talk to us"), p.menuLabels());
        var vague = p.apply("rename the menu", false);
        assertTrue(vague.handled() && !vague.changed() && vague.clarify().contains("Which menu item"), vague.toString());
    }

    @Test
    void colourAndFontRequestsAreLeftToTheThemeEditor() {
        var p = patcher();
        assertFalse(p.apply("change the accent to green", true).handled());
        assertFalse(p.apply("change the website color to blue", true).handled());
        assertFalse(p.apply("make the site be blue", true).handled());
        assertFalse(p.apply("add a chatbot agent", true).handled());
    }

    @Test
    void editsSurviveARebuildWhichRestoresTheLookAndReplaysTheEdits() throws Exception {
        var p = patcher();
        // the owner's own colours and a pasted picture, then two edits
        Files.writeString(templates().resolve("home.html"), page("home")
                .replace("--accent:#1a73e8", "--accent:#16a34a").replace("/uploads/chat/hero.png", "/uploads/chat/mine.png"));
        p.apply("rename Plans to Pricing", false);
        p.apply("make the hero shorter", false);
        p.apply("add a contact form", false);

        var look = p.snapshotLook();
        // the generator rewrites the pages with its defaults
        Files.writeString(templates().resolve("home.html"), HOME);
        Files.writeString(templates().resolve("pricing.html"), PRICING);
        p.restoreLook(look);
        assertTrue(p.replay() >= 3);

        String home = page("home");
        assertTrue(home.contains("--accent:#16a34a"), "colours kept");
        assertTrue(home.contains("/uploads/chat/mine.png"), "the owner's picture kept");
        assertTrue(home.contains(">Pricing<") && !home.contains(">Plans<"), "rename replayed");
        assertTrue(home.contains("min-height:170px"), "hero size replayed");
        assertTrue(home.contains("data-bot-section=\"contact-form\""), "added section replayed");

        p.resetEdits();
        Files.writeString(templates().resolve("home.html"), HOME);
        assertEquals(0, p.replay(), "a new site does not inherit the old edits");
    }

    @Test
    void theStoredBriefIsTheOriginalRequestWithoutTheGeneratorsNotes() {
        String brief = patcher().storedBrief();
        assertTrue(brief.startsWith("Build a bakery website called Crumb Hearth"), brief);
        assertFalse(brief.contains("Menu:") || brief.contains("Big site generated"), brief);
    }

    /** A model that answers with a fixed JSON edit plan. */
    private record Plan(String json) implements LlmClient {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return json;
        }
    }

    @Test
    void theModelCanEditWhatThePatternsDoNotKnowButOnlyWithChecksOutOfTheFile() throws Exception {
        String plan = """
                Sure!
                {"reply":"Changed the tagline to mention sourdough.","rebuild":false,"ops":[
                 {"op":"replace","page":"home","find":"Fresh bread every day.","replace":"Sourdough, baked at dawn."},
                 {"op":"replace","page":"home","find":"text that is not on the page","replace":"x"},
                 {"op":"replace","page":"home","find":"Book now","replace":"<script>alert(1)</script>"},
                 {"op":"css","css":".hero h1{letter-spacing:.1em !important}"}]}
                """;
        var out = new SitePatcher(dir.toString(), new Plan(plan)).apply("mention sourdough in the tagline", true);
        assertTrue(out.changed(), out.toString());
        assertTrue(out.changes().get(0).startsWith("changed the tagline"), out.toString());
        String home = page("home");
        assertTrue(home.contains("Sourdough, baked at dawn."));
        assertFalse(home.contains("<script>alert"), "scripts are never written");
        assertTrue(home.contains("letter-spacing:.1em"));

        var rebuild = new SitePatcher(dir.toString(), new Plan("{\"rebuild\":true,\"ops\":[]}")).apply("make it fancier", true);
        assertFalse(rebuild.handled(), "the model asked for a rebuild");
        assertFalse(new SitePatcher(dir.toString(), new Plan(plan)).apply("mention sourdough in the tagline", false).handled(),
                "no model call when it is not allowed");
    }
}
