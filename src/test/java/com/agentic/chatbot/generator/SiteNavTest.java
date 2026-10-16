package com.agentic.chatbot.generator;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The top menu and page headings come from the business, never the stock Home/About/Services/Pricing/Blog/Careers/Contact. */
class SiteNavTest {

    private static final Pattern NAV = Pattern.compile("(?s)<nav class=\"nav\">(.*?)</nav>");
    private static final Pattern LINK_TEXT = Pattern.compile("<a [^>]*>(.*?)</a>");

    private static List<String> navLabels(String html) {
        Matcher nav = NAV.matcher(html);
        assertTrue(nav.find(), "page has a nav");
        Matcher link = LINK_TEXT.matcher(nav.group(1));
        List<String> out = new ArrayList<>();
        while (link.find()) {
            out.add(link.group(1).replace("&amp;", "&"));
        }
        return out;
    }

    private static List<String> menu(String brand, String story) {
        return SiteNav.menuLabels(SiteVariant.design(brand, story).nav());
    }

    private static List<String> allLabels(String brand, String story) {
        return SiteVariant.design(brand, story).nav().stream().map(SiteNav.Item::label).toList();
    }

    @Test
    void restaurantMenuComesFromTheRestaurantAndItsStory() {
        String story = "Build a restaurant website called OliveGrove with wine, reservations and private dining";
        List<String> labels = allLabels("OliveGrove", story);
        assertTrue(labels.contains("Reservations"), labels.toString());
        assertTrue(labels.contains("Private dining"), labels.toString());
        assertTrue(labels.contains("Wine list"), labels.toString());
        assertNotEquals(SiteNav.GENERIC_LABELS, menu("OliveGrove", story));
    }

    @Test
    void clinicMenuUsesAppointments() {
        String story = "As a patient I want a clinic website called Juniper Care to meet our doctors and book appointments";
        List<String> labels = allLabels("Juniper Care", story);
        assertTrue(labels.contains("Appointments"), labels.toString());
        assertEquals(labels.size(), new HashSet<>(labels).size(), "labels are unique: " + labels);
        assertFalse(labels.stream().anyMatch(SiteNav.GENERIC_LABELS::contains), labels.toString());
    }

    @Test
    void musicSchoolMenuUsesMusicWords() {
        List<String> labels = allLabels("Harmony Notes", "Music academy website called Harmony Notes with spring recitals");
        assertTrue(labels.contains("Events"), "story names recitals/events: " + labels);
        Set<String> music = Set.of("Lessons", "Instruments", "Ensembles", "Studios", "Class packs", "Lesson levels",
                "Term options", "Studio time", "Our teachers", "Meet the faculty", "The ensemble", "Coaches",
                "Book a lesson", "Trial class", "Our sound", "Why we teach", "Studio story", "Warm up", "Backstage",
                "Tune in", "Recitals", "Practice notes", "Concert diary", "Sheet music", "Classes");
        assertTrue(labels.stream().filter(music::contains).count() >= 3, "music vocabulary: " + labels);
    }

    @Test
    void differentTopicsGetDifferentMenusAndNeverTheStockSeven() {
        Map<String, String> stories = Map.ofEntries(
                Map.entry("OliveGrove", "restaurant website called OliveGrove"),
                Map.entry("Juniper Care", "clinic website for patients and doctors called Juniper Care"),
                Map.entry("Harmony Notes", "music academy website called Harmony Notes"),
                Map.entry("SkyTrail", "travel company website called SkyTrail"),
                Map.entry("Vows Bloom", "wedding planning website called Vows Bloom"),
                Map.entry("Paw Lodge", "pet boarding website called Paw Lodge"),
                Map.entry("Quill Books", "bookstore website called Quill Books"),
                Map.entry("Fern Garden", "garden nursery website called Fern Garden"),
                Map.entry("NeonRift", "esports gaming club website called NeonRift"),
                Map.entry("Orion Dome", "planetarium website called Orion Dome"),
                Map.entry("Leafshine", "cleaning service website called Leafshine"),
                Map.entry("Forge Gym", "fitness gym website called Forge Gym"),
                Map.entry("Atlas Trust", "bank website called Atlas Trust"),
                Map.entry("Nimbus Labs", "software product website called Nimbus Labs"));
        Set<List<String>> menus = new HashSet<>();
        for (var e : stories.entrySet()) {
            List<String> menu = menu(e.getKey(), e.getValue());
            assertNotEquals(SiteNav.GENERIC_LABELS, menu, e.getKey() + " got the stock menu");
            for (String stock : SiteNav.GENERIC_LABELS) {
                assertFalse(menu.contains(stock), e.getKey() + " menu contains stock label " + stock + ": " + menu);
            }
            assertTrue(menu.size() >= 4, menu.toString());
            menus.add(menu);

            // the rendered page uses exactly that menu, on every page
            var pages = BigSiteTemplates.generate(e.getKey(), e.getValue());
            for (var page : pages.entrySet()) {
                assertEquals(menu, navLabels(page.getValue()), e.getKey() + " / " + page.getKey());
            }
        }
        assertTrue(menus.size() >= 12, "menus differ between topics: " + menus.size());
    }

    @Test
    void sameTopicDifferentBrandsCanDifferToo() {
        Set<List<String>> menus = new HashSet<>();
        for (String brand : new String[]{"OliveGrove", "Saffron Table", "Ember Hall", "Nori House", "Basil Lane",
                "Copper Pot", "Fig Street", "Juniper Dining"}) {
            menus.add(allLabels(brand, "restaurant website called " + brand));
        }
        assertTrue(menus.size() >= 4, "restaurants do not all share one menu: " + menus);
    }

    @Test
    void menuIsStableForTheSameBrand() {
        String story = "travel website called SkyTrail";
        assertEquals(allLabels("SkyTrail", story), allLabels("SkyTrail", story));
    }

    @Test
    void storyThatTalksAboutHiringPutsThatPageInTheMenu() {
        String hiring = "restaurant website called OliveGrove, we are hiring line cooks";
        var nav = SiteVariant.design("OliveGrove", hiring).nav();
        assertTrue(SiteNav.menuLabels(nav).contains(SiteNav.label(nav, "careers")), SiteNav.menuLabels(nav).toString());
    }

    @Test
    void pageHeadingsAndFooterDoNotFallBackToStockWords() {
        String story = "restaurant website called OliveGrove with reservations";
        var pages = BigSiteTemplates.generate("OliveGrove", story);
        for (String html : pages.values()) {
            assertFalse(html.contains(">Careers</a>"), "no stock Careers link");
            assertFalse(html.contains(">Pricing</a>"), "no stock Pricing link");
            assertFalse(html.contains("<h2>Pricing</h2>"));
            assertFalse(html.contains("<h2>Blog</h2>"));
            assertFalse(html.contains("<h2>Careers at "));
        }
        // routes keep their stable slugs so links, exports and the editor still work
        assertTrue(pages.get("home").contains("href=\"/site/pricing\""));
        assertEquals(List.of("home", "about", "services", "pricing", "blog", "careers", "contact"),
                List.copyOf(pages.keySet()));
        assertTrue(pages.get("home").toLowerCase(Locale.ROOT).contains("reservations"));
    }

    @Test
    void widgetIsAChatGptStyleChat() {
        String html = BigSiteTemplates.generate("OliveGrove", "restaurant website called OliveGrove with a chatbot agent")
                .get("home");
        assertTrue(html.contains("/api/site-agent/stream"), "streams from the agent endpoint");
        assertTrue(html.contains("history: prior"), "sends earlier turns");
        assertTrue(html.contains("Thinking..."), "shows a thinking state");
        assertTrue(html.contains("id=\"sa-input\"") && html.contains("id=\"sa-send\"") && html.contains("id=\"sa-new\""));
        assertTrue(html.contains("\"pages\":["), "knows the site's page names");
        assertFalse(html.contains("@@"), "all placeholders filled");
        assertFalse(html.contains("[[") || html.contains("[("), "no Thymeleaf inlining sequences in the script");
    }

    @Test
    void agentKnowsTheSiteMenuNames() {
        String story = "restaurant website called OliveGrove with reservations and a chatbot";
        var d = SiteVariant.design("OliveGrove", story);
        var k = SiteAgent.knowledge("OliveGrove", d.theme(), null, d.nav());
        String pricing = SiteNav.label(d.nav(), "pricing");
        assertEquals(pricing, k.pageLabel("pricing"));
        assertNotEquals("Pricing", pricing);
        assertTrue(SiteAgent.toJson(k).contains("\"label\":" + "\"" + pricing.replace("&", "\\u0026") + "\""));
        assertTrue(SiteAgent.systemPrompt(k).contains(pricing + " (/site/pricing)"));
        var reply = SiteAgent.reply(k, "how much does it cost?");
        assertTrue(reply.actions().stream().anyMatch(a -> a.label().equals(pricing) && a.href().equals("/site/pricing")));
    }
}
