package com.agentic.chatbot.stage;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.BotModelTrainingService;
import com.agentic.chatbot.service.GitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoryImportServiceTest {

    @TempDir
    Path tmp;

    private StoryImportService importer() {
        BotModelTrainingService training = new BotModelTrainingService(tmp.resolve("bot-training.json").toString());
        training.load();
        LlmClient none = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("no model");
            }
        };
        StageService stages = new StageService(new StageLlm(none), training, tmp.resolve("generated-app").toString());
        return new StoryImportService(stages, new GitService("", "BOT", "bot@example.com"));
    }

    private static byte[] zip(String... namesAndContents) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void uploadedFilesFolderPathsAndZipAreCombinedAndStoredInTheRun() throws Exception {
        StoryImportService importer = importer();
        byte[] docx = zip("word/document.xml", "<w:document><w:body><w:p><w:r><w:t>As a clerk, I want &amp; need reports</w:t></w:r></w:p>"
                + "</w:body></w:document>");
        StoryImportService.Imported imported = importer.fromUploads(List.of(
                new MockMultipartFile("files", "stories/login.md", "text/markdown", "As a user, I want to log in.".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("files", "stories/notes.txt", "text/plain", "As a user, I want to log out.".getBytes(StandardCharsets.UTF_8)),
                new MockMultipartFile("files", "stories/report.docx", "application/octet-stream", docx),
                new MockMultipartFile("files", "bundle.zip", "application/zip", zip(
                        "more/a.csv", "id,story\n1,As an admin I want users\n", "more/logo.png", "binary"))), null);

        assertEquals(4, imported.files().size(), imported.files().toString());
        assertTrue(imported.content().contains("<!-- source: stories/login.md -->"));
        assertTrue(imported.content().contains("As a clerk, I want & need reports"), "docx text is extracted");
        assertTrue(imported.content().contains("1,As an admin I want users"));
        assertTrue(imported.skipped().stream().anyMatch(s -> s.contains("logo.png")));
        assertFalse(imported.truncated());
        assertTrue(Files.exists(tmp.resolve("generated-app/stages").resolve(imported.runId()).resolve("stories-input/stories/login.md")));
    }

    @Test
    void zipSlipAndEmptyUploadsAreRejected() throws Exception {
        StoryImportService importer = importer();
        assertThrows(IllegalArgumentException.class, () -> importer.fromUploads(List.of(
                new MockMultipartFile("files", "evil.zip", "application/zip", zip("../../evil.md", "x"))), null));
        assertThrows(IllegalArgumentException.class, () -> importer.fromUploads(List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> importer.fromUploads(List.of(
                new MockMultipartFile("files", "photo.png", "image/png", new byte[] {1, 2, 3})), null), "no story files");
    }

    @Test
    void hundredStoriesFitAndOversizedSetsAreTruncatedAtAFileBoundary() throws Exception {
        StoryImportService importer = importer();
        Path folder = Files.createDirectories(tmp.resolve("stories"));
        for (int i = 0; i < 100; i++) {
            Files.writeString(folder.resolve(String.format("story-%03d.md", i)),
                    "# Story " + i + "\nAs a user, I want feature " + i + ", so that it works.\nScenario: ok\n  Given a\n  When b\n  Then c\n");
        }
        StoryImportService.Imported all = importer.fromLocal(folder.toString(), null);
        assertEquals(100, all.files().size());
        assertFalse(all.truncated());

        Path big = Files.createDirectories(tmp.resolve("big"));
        String chunk = "As a user, I want something long. ".repeat(500);
        for (int i = 0; i < 8; i++) {
            Files.writeString(big.resolve("big-" + i + ".md"), chunk);
        }
        StoryImportService.Imported capped = importer.fromLocal(big.toString(), all.runId());
        assertTrue(capped.truncated());
        assertTrue(capped.content().length() < StageService.MAX_INPUT_CHARS);
        assertTrue(capped.files().size() < 8 && capped.skipped().stream().anyMatch(s -> s.contains("input limit")));
    }

    @Test
    void gitImportNeedsAnHttpsGithubUrlForRemoteVisitors() {
        StoryImportService importer = importer();
        assertThrows(IllegalArgumentException.class, () -> importer.fromGit("file:///c:/secret", "", "", null, null, true));
        assertThrows(IllegalArgumentException.class, () -> importer.fromGit("", "", "", null, null, false));
        assertThrows(IllegalArgumentException.class, () -> importer.fromGit("https://github.com/owner/repo", "", "../x", null, null, true));
    }
}
