package com.agentic.chatbot.web;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.BotModelTrainingService;
import com.agentic.chatbot.service.GitService;
import com.agentic.chatbot.stage.StageLlm;
import com.agentic.chatbot.stage.StageRequest;
import com.agentic.chatbot.stage.StageResponse;
import com.agentic.chatbot.stage.StageService;
import com.agentic.chatbot.stage.StageType;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The whole-run push of the stages page: right run, clean URL, and messages that tell the failure causes apart. */
class StagePushTest {

    private static final LlmClient OFFLINE = new LlmClient() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new AssertionError("Offline stages must not call the model");
        }
    };

    @TempDir
    Path tmp;

    private StageService stages() {
        BotModelTrainingService training = new BotModelTrainingService(tmp.resolve("bot-training.json").toString());
        training.load();
        return new StageService(new StageLlm(OFFLINE), training, tmp.resolve("generated-app").toString());
    }

    private static StageApiController controller(StageService stages) {
        return new StageApiController(stages, null, new GitService("", "BOT", "bot@example.com"));
    }

    private static MockHttpServletRequest local() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static StageApiController.PushRequest push(String repoUrl) {
        return new StageApiController.PushRequest("run", repoUrl, "bot/test", "", "", "", "", true);
    }

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
    void trainerOnlyRunSaysWhichRunIsEmptyInsteadOfRunTheStageFirst() {
        StageService stages = stages();
        // the Trainer on a typed/stale run id creates a run that holds only run.json and a training report
        StageResponse trainer = stages.run(StageType.TRAINER,
                new StageRequest("banking", "", Map.of(), "20261015-000000-abcdef"));
        String id = trainer.runId();
        StageApiController controller = controller(stages);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> controller.push(id, push("https://github.com/manasa123104/tester"), local()));
        assertTrue(e.getMessage().contains("Run " + id + " has no generated files"), e.getMessage());
        assertFalse(e.getMessage().contains("run the stage first"), e.getMessage());
    }

    @Test
    void unknownRunIsReportedAsNotFound() {
        StageApiController controller = controller(stages());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> controller.push("20261015-000000-ffffff", push("https://github.com/manasa123104/tester"), local()));
        assertTrue(e.getMessage().contains("not found"), e.getMessage());
    }

    @Test
    void trailingDotAndSpacesAreStrippedFromTheRepositoryUrl() {
        assertEquals("https://github.com/manasa123104/tester", GitService.normalizeRepoUrl("https://github.com/manasa123104/tester."));
        assertEquals("https://github.com/manasa123104/tester", GitService.normalizeRepoUrl("  https://github.com/manasa123104/tester/ \n"));
        assertEquals("https://github.com/o/r.git", GitService.normalizeRepoUrl("https://github.com/o/r.git"));
        assertEquals("", GitService.normalizeRepoUrl(null));
        // a URL that still ends with a full stop (when validated directly) is explained, not sent to git
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GitService.validateRepoUrl("https://github.com/manasa123104/tester."));
        assertTrue(e.getMessage().contains("full stop"), e.getMessage());
    }

    @Test
    void badUrlIsReportedBeforeAnythingElse() {
        StageService stages = stages();
        String id = stages.runAll(new StageRequest("banking", "fund transfer between accounts", Map.of(), null)).get(0).runId();
        StageApiController controller = controller(stages);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> controller.push(id, push("not a url"), local()));
        assertTrue(e.getMessage().contains("repository URL"), e.getMessage());
        IllegalArgumentException org = assertThrows(IllegalArgumentException.class,
                () -> controller.push(id, push("https://github.com/manasa123104"), local()));
        assertTrue(org.getMessage().contains("organisation or user page"), org.getMessage());
    }

    @Test
    void badTokenAndBadRepositoryAreDifferentMessages() {
        String url = "https://github.com/manasa123104/tester";
        String token = StageApiController.friendlyGitError(
                "git clone failed: remote: Invalid username or password.\nfatal: Authentication failed for 'https://github.com/x/y/'", url);
        String missing = StageApiController.friendlyGitError("git clone failed: remote: Repository not found.", url);
        String denied = StageApiController.friendlyGitError("git push failed: The requested URL returned error: 403", url);
        assertTrue(token.contains("rejected the token"), token);
        assertTrue(missing.contains("Repository not found") && missing.contains("no trailing dot"), missing);
        assertTrue(denied.contains("cannot write"), denied);
        assertEquals("git push failed: something else", StageApiController.friendlyGitError("git push failed: something else", url));
    }

    @Test
    void emptyRemoteWithPullFirstDoesFirstPushInsteadOfRevParseHeadFailure() throws Exception {
        Assumptions.assumeTrue(gitInstalled(), "git is not installed");
        Path remote = Files.createDirectories(tmp.resolve("empty.git"));
        git(remote, "init", "-q", "--bare", "-b", "main");

        StageService stages = stages();
        String id = stages.runAll(new StageRequest("banking", "fund transfer between accounts", Map.of(), null)).get(0).runId();

        // pull first (push() defaults it on) against a repository with no commits at all
        Map<String, Object> out = controller(stages).push(id, push(remote.toUri().toString()), local());
        assertTrue((Integer) out.get("files") > 5, out.toString());
        String tree = git(remote, "ls-tree", "-r", "--name-only", "bot/test");
        assertTrue(tree.contains("stories.md") && tree.contains("Dockerfile"), tree);
    }

    @Test
    void emptyRemoteWithBaseBranchAndPullFirstAlsoPushes() throws Exception {
        Assumptions.assumeTrue(gitInstalled(), "git is not installed");
        Path remote = Files.createDirectories(tmp.resolve("empty2.git"));
        git(remote, "init", "-q", "--bare", "-b", "main");
        Path content = Files.createDirectories(tmp.resolve("content"));
        Files.writeString(content.resolve("a.txt"), "hi\n");

        GitService.PushResult r = new GitService("", "BOT", "bot@example.com").pushDirectory(content,
                remote.toUri().toString(), "feature/x", "main", "gen", "first", tmp.resolve("work"), null, true);
        assertEquals("feature/x", r.branch());
        assertTrue(git(remote, "ls-tree", "-r", "--name-only", "feature/x").contains("gen/a.txt"));
    }

    @Test
    void headRevParseErrorsAndMissingRepositoryNameGetFriendlyMessages() {
        String msg = StageApiController.friendlyGitError("git rev-parse failed: fatal: ambiguous argument 'HEAD': unknown revision "
                + "or path not in the working tree.", "https://github.com/o/r");
        assertTrue(msg.contains("looks empty") && msg.contains("uncheck Pull first"), msg);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GitService.validateRepoUrl(GitService.normalizeRepoUrl("https://github.com/manasa123104/")));
        assertTrue(e.getMessage().contains("repository name is missing"), e.getMessage());
    }

    @Test
    void completedRunPushesTheWholeRunEvenWhenTheUrlHasATrailingDot() throws Exception {
        Assumptions.assumeTrue(gitInstalled(), "git is not installed");
        Path remote = Files.createDirectories(tmp.resolve("tester.git"));
        git(remote, "init", "-q", "--bare", "-b", "main");
        Path seed = Files.createDirectories(tmp.resolve("seed"));
        git(seed, "init", "-q", "-b", "main");
        Files.writeString(seed.resolve("README.md"), "hello\n");
        git(seed, "add", "README.md");
        git(seed, "commit", "-q", "-m", "init");
        git(seed, "push", "-q", remote.toUri().toString(), "main");

        StageService stages = stages();
        List<StageResponse> run = stages.runAll(new StageRequest("banking", "fund transfer between accounts", Map.of(), null));
        String id = run.get(0).runId();

        // file URLs end in a path, so the stray dot is the same typo as on github.com/owner/repo.
        Map<String, Object> out = controller(stages).push(id, push(remote.toUri().toString() + "."), local());
        assertTrue((Integer) out.get("files") > 5, out.toString());
        String tree = git(remote, "ls-tree", "-r", "--name-only", "bot/test");
        assertTrue(tree.contains("stories.md") && tree.contains("Dockerfile"), tree);
        assertFalse(tree.contains("run.json"), tree);
    }
}
