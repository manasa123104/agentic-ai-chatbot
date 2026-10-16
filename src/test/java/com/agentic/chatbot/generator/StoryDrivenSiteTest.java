package com.agentic.chatbot.generator;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Different user stories give different websites; uploaded files are read as the user stories. */
class StoryDrivenSiteTest {

    private static final Pattern H1 = Pattern.compile("<h1>(.*?)</h1>");

    private static String h1(String html) {
        Matcher m = H1.matcher(html);
        assertTrue(m.find(), "page has an H1");
        return m.group(1);
    }

    @Test
    void unnamedStoriesGetDifferentBrandsHeadingsAndCards() {
        String a = "As a customer, I want to browse the daily bread menu. As a baker, I want to post today's specials.";
        String b = "As a patient, I want to book a dental check-up. As a dentist, I want to see my appointments.";
        String brandA = StoryDetails.brandOf(a);
        String brandB = StoryDetails.brandOf(b);
        assertNotEquals(brandA, brandB);
        var pa = BigSiteTemplates.generate(brandA, a);
        var pb = BigSiteTemplates.generate(brandB, b);
        assertNotEquals(h1(pa.get("home")), h1(pb.get("home")));
        assertNotEquals(pa.get("home"), pb.get("home"));
    }

    @Test
    void severalStoriesBecomeServiceCardsOfOneSite() {
        String story = "1. As a guest I want to reserve a table online\n"
                + "2. As a guest I want to see the seasonal tasting menu\n"
                + "3. As an owner I want to publish opening hours";
        StoryDetails details = StoryDetails.parse(story);
        assertTrue(details.storyCards().size() >= 3, "one card per story: " + details.storyCards());
        String home = BigSiteTemplates.generate(StoryDetails.brandOf(story), story).get("home");
        assertTrue(home.toLowerCase().contains("reserve") || home.toLowerCase().contains("table"), "story text is on the page");
    }

    @Test
    void manyDifferentStoriesGiveManyDifferentBrands() {
        Set<String> brands = new HashSet<>();
        String[] stories = {
                "I want a site where people can order fresh sourdough",
                "I need a page for my yoga classes and workshops",
                "Build a portfolio for a wedding photographer in Lisbon",
                "A site for our robotics club with events and sign-up",
                "Create a landing page for a bike repair shop in Pune"};
        for (String s : stories) {
            brands.add(StoryDetails.brandOf(s));
        }
        assertTrue(brands.size() >= 4, "brands differ: " + brands);
    }

    @Test
    void uploadedFileIsSplitIntoRequestAndFiles() {
        String prompt = "Build it for a bakery\n\n=== Uploaded files (use as the user stories and requirements for the website) ===\n"
                + "--- stories.md ---\n# Harbor Bakery\nAs a customer, I want to order bread online\nAs a baker, I want to list daily specials\n\n"
                + "--- OrderService.java ---\npublic class OrderService { }\n";
        UploadedStory.Parsed parsed = UploadedStory.parse(prompt);
        assertEquals("Build it for a bakery", parsed.request());
        assertEquals(2, parsed.docs().size());
        assertEquals(UploadedStory.Kind.REQUIREMENTS, parsed.docs().get(0).kind());
        assertEquals(UploadedStory.Kind.CODE, parsed.docs().get(1).kind());
        assertTrue(UploadedStory.hasUploads(prompt));
        assertFalse(UploadedStory.hasUploads("just a prompt"));
    }

    @Test
    void uploadedFileDrivesTheWebsiteNotItsRawCode() {
        String prompt = "\n\n=== Uploaded files (use as the user stories and requirements for the website) ===\n"
                + "--- stories.md ---\n# Harbor Bakery\nAs a customer, I want to order bread online\nAs a baker, I want to list daily specials\n\n"
                + "--- PricingEngine.java ---\npublic class PricingEngine { void login() {} }\n";
        String effective = UploadedStory.forGeneration(prompt);
        assertTrue(effective.contains("order bread online"));
        assertFalse(effective.contains("void login"), "raw code is not part of the story");
        assertTrue(effective.contains("Pricing Engine"), "module names of uploaded code are kept");
        assertEquals("Harbor Bakery", StoryDetails.brandOf(effective));
        var home = BigSiteTemplates.generate(StoryDetails.brandOf(effective), effective).get("home");
        assertTrue(home.contains("Harbor Bakery"));
    }

    @Test
    void severalUploadedFilesAreUsedTogether() {
        String prompt = "Make a site\n\n=== Uploaded files (use as the user stories and requirements for the website) ===\n"
                + "--- a.txt ---\nAs a runner, I want to log my weekly distance\n\n"
                + "--- b.txt ---\nAs a coach, I want to review my team's progress\n";
        String effective = UploadedStory.forGeneration(prompt);
        assertTrue(effective.contains("weekly distance") && effective.contains("team's progress"));
        List<String> names = UploadedStory.fileNames(prompt);
        assertEquals(List.of("a.txt", "b.txt"), names);
    }

    @Test
    void promptsWithoutUploadsAreUnchanged() {
        assertEquals("a travel website called SkyTrail", UploadedStory.forGeneration("a travel website called SkyTrail"));
    }
}
