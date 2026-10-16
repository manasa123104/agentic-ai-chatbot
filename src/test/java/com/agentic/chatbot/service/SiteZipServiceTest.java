package com.agentic.chatbot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Download ZIP: user stories + code in the language of the run + the website. */
class SiteZipServiceTest {

    private static void put(Path root, String rel, String text) throws IOException {
        Path p = root.resolve(rel);
        Files.createDirectories(p.getParent());
        Files.writeString(p, text, StandardCharsets.UTF_8);
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                out.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    private static void site(Path root, String language) throws IOException {
        put(root, "SITE_META.json", "{\"brand\":\"Crumb Hearth\",\"language\":\"" + language + "\"}");
        put(root, "USER_STORY.md", "# Story\nAs a baker I want a site");
        put(root, "WORKFLOW.md", "# Workflow");
        put(root, "pom.xml", "<project/>");
        put(root, "src/main/java/com/x/App.java", "class App {}");
        put(root, "src/test/java/com/x/AppTest.java", "class AppTest {}");
        put(root, "src/main/resources/templates/home.html",
                "<html xmlns:th=\"http://www.thymeleaf.org\"><a href=\"/site/pricing\">Plans</a>"
                        + "<img src=\"/uploads/chat/hero.png\"/><span style=\"background-image:url('/uploads/chat/hero.png')\"></span></html>");
        put(root, "src/main/resources/templates/pricing.html", "<html>pricing</html>");
        put(root, "uploads/chat/hero.png", "PNG");
        put(root, "target/classes/Junk.class", "junk");
        put(root, "stages/run1/stories.md", "other run");
    }

    @Test
    void javaSitesGetStoriesTheSpringBootProjectAndTheStaticSite(@TempDir Path root) throws Exception {
        site(root, "java");
        Map<String, String> zip = unzip(SiteZipService.build(root));

        assertTrue(zip.containsKey("stories/USER_STORY.md") && zip.containsKey("stories/WORKFLOW.md"), zip.keySet().toString());
        assertTrue(zip.containsKey("code/pom.xml") && zip.containsKey("code/src/main/java/com/x/App.java")
                && zip.containsKey("code/src/test/java/com/x/AppTest.java"), zip.keySet().toString());
        assertTrue(zip.containsKey("code/src/main/resources/templates/home.html"));
        assertTrue(zip.containsKey("site/index.html") && zip.containsKey("site/home.html") && zip.containsKey("site/pricing.html"));
        assertTrue(zip.containsKey("site/uploads/chat/hero.png"));
        assertTrue(zip.containsKey("site/SITE_META.json") && zip.containsKey("README.md"));

        String index = zip.get("site/index.html");
        assertTrue(index.contains("href=\"pricing.html\"") && !index.contains("xmlns:th"), index);
        assertTrue(index.contains("src=\"uploads/chat/hero.png\"") && index.contains("url('uploads/chat/hero.png')"), index);
        assertFalse(zip.keySet().stream().anyMatch(k -> k.contains("target/") || k.contains("stages/")), zip.keySet().toString());
        assertTrue(zip.get("README.md").contains("Java") && zip.get("README.md").contains("mvn spring-boot:run"));
    }

    @Test
    void otherLanguagesGetTheirOwnServerInsteadOfTheJavaProject(@TempDir Path root) throws Exception {
        site(root, "python");
        put(root, "server/python/server.py", "print('hi')");
        put(root, "server/python/README.md", "run it");
        put(root, "server/python/public/home.html", "<html/>");
        Map<String, String> zip = unzip(SiteZipService.build(root));

        assertTrue(zip.containsKey("code/python/server.py") && zip.containsKey("code/python/README.md")
                && zip.containsKey("code/python/public/home.html"), zip.keySet().toString());
        assertFalse(zip.keySet().stream().anyMatch(k -> k.startsWith("code/src/") || k.equals("code/pom.xml")), zip.keySet().toString());
        assertTrue(zip.containsKey("stories/USER_STORY.md") && zip.containsKey("site/index.html"));
        assertTrue(zip.get("README.md").contains("Python"), zip.get("README.md"));
        assertEquals("python", SiteZipService.language(root));
    }

    @Test
    void theFileIsNamedAfterTheBrandAndNothingToDownloadIsRefused(@TempDir Path root) throws Exception {
        assertFalse(SiteZipService.hasContent(root));
        assertThrows(IllegalArgumentException.class, () -> SiteZipService.build(root));
        site(root, "java");
        assertEquals("bot-crumb-hearth-stories-code-site.zip", SiteZipService.fileName(root));
    }
}
