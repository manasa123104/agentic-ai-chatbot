package com.agentic.chatbot.service;

import com.agentic.chatbot.generator.ScreenCodeGenerator;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.stage.StageLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** The user gets the stories and the code in the language that was generated, listed and zipped. */
class SiteCodeServiceTest {

    private static void generate(String story, Path dir) throws Exception {
        new ScreenCodeGenerator(mock(LlmClient.class)).generate(story, dir);
        Files.writeString(dir.resolve("WORKFLOW.md"), "# BOT workflow\n");
        Files.writeString(dir.resolve("BOT_MODEL.json"), "{}");
    }

    private static SiteCodeService service(Path dir) {
        return new SiteCodeService(new SiteStyleService(dir.toString()));
    }

    private static Set<String> entries(byte[] zip) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                names.add(e.getName());
            }
        }
        return names;
    }

    @Test
    void aPythonSiteListsAndZipsPythonCode(@TempDir Path dir) throws Exception {
        generate("Build a bakery website called Crumb Hearth with menu and contact pages. Backend language: Python.", dir);
        SiteCodeService svc = service(dir);

        assertEquals(StageLanguage.PYTHON, svc.language());
        SiteCodeService.Bundle b = svc.describe();
        assertEquals("Python", b.getLanguageLabel());
        assertTrue(b.group("code").getTitle().startsWith("Python"), b.group("code").getTitle());
        assertTrue(b.group("code").getFiles().contains("server/python/server.py"), b.group("code").getFiles().toString());
        assertTrue(b.group("stories").getFiles().contains("USER_STORY.md"));
        assertTrue(b.group("site").getFiles().contains("src/main/resources/templates/home.html"));
        assertTrue(b.group("site").getFiles().contains("server/python/public/home.html"));
        assertTrue(b.group("logic").getFiles().containsAll(java.util.List.of("WORKFLOW.md", "BOT_MODEL.json")));
        // the Java preview app is still there, but as the reference app, not as "the code"
        assertTrue(b.group("reference").getFiles().stream().anyMatch(f -> f.endsWith(".java")));
        assertFalse(b.group("code").getFiles().stream().anyMatch(f -> f.endsWith(".java")));

        Set<String> code = entries(svc.codeZip());
        assertTrue(code.stream().anyMatch(n -> n.endsWith("/server/python/server.py")), code.toString());
        assertTrue(code.stream().anyMatch(n -> n.endsWith("/USER_STORY.md")), code.toString());
        assertTrue(code.stream().anyMatch(n -> n.endsWith("/WORKFLOW.md")), code.toString());
        assertTrue(code.stream().noneMatch(n -> n.endsWith(".java")), code.toString());
        assertTrue(svc.isDeliverable("server/python/server.py"));
        assertFalse(svc.isDeliverable("../pom.xml"));
        assertFalse(svc.isDeliverable("stages/x/run.json"));
        assertTrue(svc.baseName().endsWith("-python"), svc.baseName());
        assertNotNull(svc.storiesMarkdown());
    }

    @Test
    void aJavaSiteListsTheSpringBootProjectAsItsCode(@TempDir Path dir) throws Exception {
        generate("Build a travel website called SkyTrail with destinations and contact pages.", dir);
        SiteCodeService svc = service(dir);

        SiteCodeService.Bundle b = svc.describe();
        assertEquals("Java", b.getLanguageLabel());
        assertNull(b.group("reference"));
        assertTrue(b.group("code").getFiles().stream().anyMatch(f -> f.endsWith("Controller.java")), b.group("code").getFiles().toString());
        assertTrue(b.group("code").getFiles().contains("pom.xml"));

        Set<String> code = entries(svc.codeZip());
        assertTrue(code.stream().anyMatch(n -> n.endsWith("Controller.java")), code.toString());
        assertTrue(code.stream().anyMatch(n -> n.endsWith("/src/main/resources/templates/home.html")), code.toString());
    }

    @Test
    void everyLanguageWithAServerShowsItsOwnFiles(@TempDir Path dir) throws Exception {
        String[][] cases = {{"Go", "server/go/"}, {"TypeScript", "server/typescript/"}, {"Kotlin", "server/kotlin/"},
                {"C#", "server/csharp/"}, {"Rust", "server/rust/"}};
        for (String[] c : cases) {
            Path sub = Files.createDirectories(dir.resolve(c[0].replace("#", "sharp")));
            generate("Build a music academy website called Harmony Notes with a contact form. Backend language: " + c[0] + ".", sub);
            SiteCodeService.Bundle b = service(sub).describe();
            assertEquals(c[0], b.getLanguageLabel());
            assertTrue(b.group("code").getFiles().stream().allMatch(f -> f.startsWith(c[1])), c[0] + " " + b.group("code").getFiles());
            assertTrue(b.group("code").getFiles().size() >= 2, c[0] + " " + b.group("code").getFiles());
        }
    }

    @Test
    void aSingleScreenInPythonAlsoGetsAPythonServer(@TempDir Path dir) throws Exception {
        generate("Create a login page where users sign in with username and password. Backend language: Python.", dir);
        SiteCodeService svc = service(dir);

        assertEquals(StageLanguage.PYTHON, svc.language());
        SiteCodeService.Bundle b = svc.describe();
        assertTrue(b.group("code").getFiles().contains("server/python/server.py"), b.group("code").getFiles().toString());
        // "/" opens the screen
        assertTrue(Files.isRegularFile(dir.resolve("server/python/public/home.html")));
    }

    @Test
    void downloadsServeTheStoriesTheCodeZipAndOneFile(@TempDir Path dir) throws Exception {
        generate("Build a bakery website called Crumb Hearth with menu and contact pages. Backend language: Python.", dir);
        SiteStyleService styles = new SiteStyleService(dir.toString());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.agentic.chatbot.web.CodeDownloadController(new SiteCodeService(styles), new SiteZipService(styles))).build();
        var get = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.class;

        var stories = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/code/download/stories"))
                .andReturn().getResponse();
        assertEquals(200, stories.getStatus());
        assertTrue(stories.getHeader("Content-Disposition").contains("user-stories.md"), stories.getHeader("Content-Disposition"));
        assertTrue(stories.getContentAsString().contains("Crumb Hearth"));

        var code = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/code/download/code.zip"))
                .andReturn().getResponse();
        assertEquals(200, code.getStatus());
        assertTrue(code.getHeader("Content-Disposition").contains("python-code.zip"), code.getHeader("Content-Disposition"));
        assertTrue(entries(code.getContentAsByteArray()).stream().anyMatch(n -> n.endsWith("server/python/server.py")));

        for (String url : new String[]{"/download/site.zip", "/code/download/site.zip"}) {
            var site = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url)).andReturn().getResponse();
            assertEquals(200, site.getStatus(), url);
            Set<String> names = entries(site.getContentAsByteArray());
            assertTrue(names.contains("stories/USER_STORY.md"), names.toString());
            assertTrue(names.contains("code/python/server.py"), names.toString());
            assertTrue(names.contains("site/index.html"), names.toString());
        }

        var file = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/code/download/file").param("file", "server/python/server.py")).andReturn().getResponse();
        assertEquals(200, file.getStatus());
        assertTrue(file.getContentAsString().contains("python"), file.getContentAsString());
        var outside = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/code/download/file").param("file", "../pom.xml")).andReturn().getResponse();
        assertEquals(404, outside.getStatus());
    }

    @Test
    void nothingGeneratedMeansAnEmptyBundle(@TempDir Path dir) {
        assertTrue(service(dir).describe().isEmpty());
    }
}
