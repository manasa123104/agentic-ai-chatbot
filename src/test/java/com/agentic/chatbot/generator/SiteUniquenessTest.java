package com.agentic.chatbot.generator;

import com.agentic.chatbot.stage.StageLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SiteUniquenessTest {

    private static final Pattern H1 = Pattern.compile("<h1>(.*?)</h1>");

    private static String h1(String html) {
        Matcher m = H1.matcher(html);
        assertTrue(m.find(), "page has an H1");
        return m.group(1);
    }

    @Test
    void twoRestaurantsGetDifferentHeadingsPaletteAndFonts() {
        String a = "Build a multi-page restaurant website called OliveGrove";
        String b = "Build a multi-page restaurant website called Saffron Table";
        var pa = BigSiteTemplates.generate("OliveGrove", a);
        var pb = BigSiteTemplates.generate("Saffron Table", b);
        assertNotEquals(h1(pa.get("home")), h1(pb.get("home")));
        assertTrue(h1(pa.get("home")).contains("OliveGrove") || pa.get("home").contains("OliveGrove"));
        var da = SiteVariant.design("OliveGrove", a);
        var db = SiteVariant.design("Saffron Table", b);
        assertNotEquals(da.signature(), db.signature());
        assertNotEquals(pa.get("about"), pb.get("about"));
    }

    @Test
    void manyBrandsOfTheSameKindAreNotCopies() {
        Set<String> signatures = new HashSet<>();
        Set<String> headlines = new HashSet<>();
        String[] brands = {"OliveGrove", "Saffron Table", "Ember Hall", "Nori House", "Basil Lane", "Copper Pot",
                "Fig Street", "Juniper Dining"};
        for (String brand : brands) {
            var d = SiteVariant.design(brand, "restaurant website called " + brand);
            signatures.add(d.signature());
            headlines.add(d.heroHeadline());
        }
        assertTrue(signatures.size() >= 6, "palette/font/layout signatures differ: " + signatures);
        assertTrue(headlines.size() >= 6, "hero headlines differ: " + headlines);
    }

    @Test
    void sameBrandIsStable() {
        String story = "travel website called SkyTrail";
        assertEquals(SiteVariant.design("SkyTrail", story).signature(), SiteVariant.design("SkyTrail", story).signature());
        assertEquals(h1(BigSiteTemplates.generate("SkyTrail", story).get("home")),
                h1(BigSiteTemplates.generate("SkyTrail", story).get("home")));
    }

    @Test
    void explicitColorAndFontAreKept() {
        var d = SiteVariant.design("OliveGrove", "restaurant website called OliveGrove with blue colors and Poppins font");
        assertEquals("#1565c0", d.theme().accent());
        assertTrue(d.theme().fontFamily().startsWith("Poppins"));
    }

    @Test
    void brandIsReadFromThePrompt() {
        assertEquals("Harmony Notes", SiteVariant.brandFrom(
                "Generate Java code for a music academy website called Harmony Notes (Spring Boot + Thymeleaf).", "x"));
        assertEquals("SkyTrail", SiteVariant.brandFrom(
                "Generate Java website code for a travel company called SkyTrail Travels: Spring Boot", "x").split(" ")[0]);
        assertEquals("fallback", SiteVariant.brandFrom("make a site", "fallback"));
    }

    @Test
    void chatbotOnlyWhenAsked() {
        assertFalse(BigSiteTemplates.generate("OliveGrove", "restaurant website called OliveGrove").get("home")
                .contains("sa-fab"));
        var pages = BigSiteTemplates.generate("OliveGrove",
                "restaurant website called OliveGrove with a chatbot agent");
        for (String page : pages.values()) {
            assertTrue(page.contains("id=\"sa-fab\""));
            assertTrue(page.contains("/api/site-agent"));
        }
        assertTrue(SiteAgent.wantsAgent("Add an AI assistant to the site"));
        assertTrue(SiteAgent.wantsAgent("site with chat bot"));
        assertFalse(SiteAgent.wantsAgent("website for a travel agent called Voyage"));
        assertFalse(SiteAgent.wantsAgent("restaurant site, no chatbot"));
        assertFalse(SiteAgent.wantsAgent("Build a wedding planning website"));
    }

    @Test
    void agentAnswersFromTheSiteContent() {
        var theme = SiteThemePack.fromStory("restaurant website");
        var k = SiteAgent.knowledge("OliveGrove", theme, null);
        var pricing = SiteAgent.reply(k, "how much does it cost?");
        assertTrue(pricing.actions().stream().anyMatch(a -> a.href().equals("/site/pricing")));
        var service = SiteAgent.reply(k, "tell me about private dining");
        assertTrue(service.text().contains("OliveGrove"));
        assertFalse(SiteAgent.reply(k, "").suggestions().isEmpty());
        assertTrue(SiteAgent.toJson(k).contains("\"brand\":\"OliveGrove\""));
    }

    @Test
    void languageIsDetectedFromThePrompt() {
        assertEquals(StageLanguage.JAVA, SiteBackends.detect("restaurant website called OliveGrove"));
        assertEquals(StageLanguage.PYTHON, SiteBackends.detect("Build a Python travel website called Roam"));
        assertEquals(StageLanguage.GO, SiteBackends.detect("Create a music site in Go called Tempo"));
        assertEquals(StageLanguage.TYPESCRIPT, SiteBackends.detect("TypeScript wedding site called Vows"));
        assertEquals(StageLanguage.JAVASCRIPT, SiteBackends.detect("Node.js gym site called Forge"));
        assertEquals(StageLanguage.KOTLIN, SiteBackends.detect("Kotlin bookstore site"));
        assertEquals(StageLanguage.CSHARP, SiteBackends.detect("C# hospital site"));
        assertEquals(StageLanguage.RUST, SiteBackends.detect("Rust esports fan site"));
        assertEquals(StageLanguage.GO, SiteBackends.detect("restaurant site called Fig (backend language: Go)"));
    }

    @Test
    void nonJavaLanguageGetsARunnableServerFolder(@TempDir Path dir) throws Exception {
        Map<String, String> pages = Map.of("home", "<html>home</html>", "contact", "<html>contact</html>");
        List<Path> written = SiteBackends.write(StageLanguage.PYTHON, dir, "Roam", pages, true);
        assertTrue(Files.exists(dir.resolve("server/python/server.py")));
        assertTrue(Files.exists(dir.resolve("server/python/public/home.html")));
        assertTrue(Files.exists(dir.resolve("server/python/public/contact-success.html")));
        assertTrue(Files.readString(dir.resolve("server/python/README.md")).contains("python server.py"));
        assertFalse(written.isEmpty());

        // switching language replaces the old server folder
        SiteBackends.write(StageLanguage.GO, dir, "Roam", pages, false);
        assertFalse(Files.exists(dir.resolve("server/python")));
        assertTrue(Files.exists(dir.resolve("server/go/main.go")));
        assertTrue(Files.exists(dir.resolve("server/go/go.mod")));

        assertTrue(SiteBackends.write(StageLanguage.JAVA, dir, "Roam", pages, false).isEmpty());
        assertFalse(Files.exists(dir.resolve("server")));
    }

    @Test
    void everyServerLanguageHasItsMainFile(@TempDir Path dir) throws Exception {
        Map<StageLanguage, String> main = Map.of(
                StageLanguage.JAVASCRIPT, "server.js", StageLanguage.TYPESCRIPT, "server.ts",
                StageLanguage.KOTLIN, "server.kt", StageLanguage.CSHARP, "Program.cs",
                StageLanguage.RUST, "src/main.rs", StageLanguage.PHP, "router.php", StageLanguage.RUBY, "server.rb");
        for (var e : main.entrySet()) {
            SiteBackends.write(e.getKey(), dir, "Roam", Map.of("home", "<html/>"), false);
            Path file = dir.resolve("server/" + e.getKey().id() + "/" + e.getValue());
            assertTrue(Files.exists(file), file.toString());
            assertTrue(Files.readString(file).contains("Roam"));
            assertTrue(SiteBackends.hasServer(e.getKey()));
        }
        SiteBackends.write(StageLanguage.COBOL, dir, "Roam", Map.of("home", "<html/>"), false);
        assertFalse(SiteBackends.hasServer(StageLanguage.COBOL));
        assertTrue(Files.readString(dir.resolve("server/cobol/README.md")).contains("/convert"));
    }

    @Test
    void designBriefAsksForUniquenessLanguageAndAgentOnlyWhenNeeded() {
        String brief = ScreenCodeGenerator.designBrief("Python travel website called Roam with a chatbot agent");
        assertTrue(brief.contains("Roam"));
        assertTrue(brief.contains("Python"));
        assertTrue(brief.contains("/api/site-agent"));
        assertTrue(brief.contains("unique"));
        assertTrue(ScreenCodeGenerator.designBrief("restaurant website called Fig").contains("Do NOT add a chatbot"));
    }
}
