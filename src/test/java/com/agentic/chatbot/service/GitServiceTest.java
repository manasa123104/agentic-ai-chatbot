package com.agentic.chatbot.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitServiceTest {

    @TempDir
    Path temp;

    private static String git(Path dir, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-c", "user.name=Test", "-c", "user.email=test@example.com"));
        command.addAll(List.of(args));
        Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) {
            throw new IllegalStateException(out);
        }
        return out;
    }

    private static boolean gitInstalled() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    void pushesConvertedFilesToANewBranch() throws Exception {
        Assumptions.assumeTrue(gitInstalled(), "git is not installed");
        Path remote = Files.createDirectories(temp.resolve("remote.git"));
        git(remote, "init", "-q", "--bare", "-b", "main");
        Path seed = Files.createDirectories(temp.resolve("seed"));
        git(seed, "init", "-q", "-b", "main");
        Files.writeString(seed.resolve("README.md"), "hello\n");
        git(seed, "add", "README.md");
        git(seed, "commit", "-q", "-m", "init");
        git(seed, "push", "-q", remote.toUri().toString(), "main");

        Path content = Files.createDirectories(temp.resolve("output/pkg"));
        Files.writeString(content.resolve("billing.py"), "class Billing:\n    pass\n");

        GitService service = new GitService("", "BOT", "bot@example.com");
        GitService.PushResult result = service.pushDirectory(temp.resolve("output"), remote.toUri().toString(),
                "converted/python", "", "converted/python", "Convert to Python", temp.resolve("work"));

        assertEquals("main", result.baseBranch());
        String tree = git(remote, "ls-tree", "-r", "--name-only", "converted/python");
        assertTrue(tree.contains("converted/python/pkg/billing.py"), tree);
        assertTrue(tree.contains("README.md"), tree);
        assertEquals("Convert to Python", git(remote, "log", "-1", "--format=%s", "converted/python").strip());
        assertEquals("BOT <bot@example.com>", git(remote, "log", "-1", "--format=%an <%ae>", "converted/python").strip());
    }

    @Test
    void validatesInputsAndBuildsGithubLinks() {
        Assumptions.assumeTrue(gitInstalled(), "git is not installed");
        GitService service = new GitService("", "BOT", "bot@example.com");
        assertThrows(IllegalArgumentException.class, () -> service.validateBranch("bad..name"));
        assertThrows(IllegalArgumentException.class, () -> GitService.validateRepoUrl("--upload-pack=evil"));
        assertThrows(IllegalArgumentException.class, () -> GitService.validateRepoUrl("ext::sh -c evil"));
        IllegalArgumentException org = assertThrows(IllegalArgumentException.class,
                () -> GitService.validateRepoUrl("https://github.com/spring-guides"));
        assertTrue(org.getMessage().contains("organisation") && org.getMessage().contains("spring-guides/gs-gradle"), org.getMessage());
        assertThrows(IllegalArgumentException.class, () -> GitService.validateRepoUrl("https://github.com/spring-guides/"));
        assertThrows(IllegalArgumentException.class,
                () -> GitService.validateRepoUrl("https://github.com/spring-guides/gs-gradle/tree/main/complete"));
        GitService.validateRepoUrl("https://github.com/spring-guides/gs-gradle");
        GitService.validateRepoUrl("https://github.com/spring-guides/gs-gradle.git");
        assertArrayEquals(new String[] {"manasa123104", "agentic-ai-chatbot"},
                GitService.githubRepo("https://github.com/manasa123104/agentic-ai-chatbot.git").orElseThrow());
        assertArrayEquals(new String[] {"owner", "repo"}, GitService.githubRepo("git@github.com:owner/repo.git").orElseThrow());
    }
}
