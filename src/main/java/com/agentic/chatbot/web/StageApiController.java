package com.agentic.chatbot.web;

import com.agentic.chatbot.service.GitService;
import com.agentic.chatbot.stage.RunNotFoundException;
import com.agentic.chatbot.stage.StageAutomation;
import com.agentic.chatbot.stage.StageRequest;
import com.agentic.chatbot.stage.StageResponse;
import com.agentic.chatbot.stage.StageService;
import com.agentic.chatbot.stage.StageType;
import com.agentic.chatbot.stage.StoryImportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Independent stage bots: POST /api/stages/{ba|developer|tester|deploy|trainer} with {domain, input, options, runId}.
 * The older ids unit-test and functional-test still work and run only that part of the Tester.
 */
@RestController
@RequestMapping("/api/stages")
public class StageApiController {

    /** Body of an approve request: the (possibly corrected) output a reviewer accepts as a training example. */
    public record ApproveRequest(String domain, String language, String input, String output, String feedback,
                                 String runId) {
    }

    /** Body of a push request; {@code token} is the visitor's own GitHub token (never stored or logged). */
    public record PushRequest(String stage, String repoUrl, String branch, String baseBranch, String folder,
                              String commitMessage, String token, Boolean pull) {
    }


    private final StageService stages;
    private final StoryImportService stories;
    private final GitService git;

    public StageApiController(StageService stages, StoryImportService stories, GitService git) {
        this.stages = stages;
        this.stories = stories;
        this.git = git;
    }

    /**
     * Business Analyst input from uploaded files / a folder / a ZIP (inputType upload), a GitHub repository (git) or a folder
     * on the host machine (local). Returns the combined story text for the input box and the run that stores the files.
     */
    @PostMapping(value = "/ba/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importStories(@RequestParam String inputType,
                                             @RequestParam(required = false) String runId,
                                             @RequestParam(required = false) String repoUrl,
                                             @RequestParam(required = false) String branch,
                                             @RequestParam(required = false) String subfolder,
                                             @RequestParam(required = false) String localPath,
                                             @RequestParam(required = false) String token,
                                             @RequestParam(required = false) List<MultipartFile> files,
                                             HttpServletRequest request) throws IOException {
        boolean host = RequestOrigin.isHost(request);
        StoryImportService.Imported imported = switch (inputType) {
            case "upload" -> stories.fromUploads(files, runId);
            case "git" -> stories.fromGit(repoUrl, branch, subfolder, GitPushPolicy.validToken(token), runId, !host);
            case "local" -> {
                if (!host) {
                    throw new RequestOrigin.HostOnlyException(RequestOrigin.HOST_ONLY_LOCAL_FOLDER);
                }
                yield stories.fromLocal(localPath, runId);
            }
            default -> throw new IllegalArgumentException("inputType must be upload, git or local.");
        };
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", imported.runId());
        body.put("content", imported.content());
        body.put("files", imported.files());
        body.put("skipped", imported.skipped());
        body.put("truncated", imported.truncated());
        body.put("totalFiles", imported.totalFiles());
        return body;
    }

    /**
     * Pushes a stage's files (stage developer, tester; blank or "run" = the whole run) to a GitHub branch. The host machine
     * may use the server's token or Git login; everyone else (including the public Render site) must supply their own token.
     */
    @PostMapping("/runs/{runId}/push")
    public Map<String, Object> push(@PathVariable String runId, @RequestBody PushRequest body, HttpServletRequest request)
            throws IOException {
        // a typo such as a trailing "." must be fixed (or reported) before anything else, not surface as a git failure
        String repoUrl = GitService.normalizeRepoUrl(body.repoUrl());
        GitService.validateRepoUrl(repoUrl);
        String token = GitPushPolicy.authorize(request, repoUrl, body.token());
        String scope = body.stage() == null ? "" : body.stage().trim().toLowerCase(Locale.ROOT);
        StageType stage = scope.isEmpty() || scope.equals("run") ? null : stageOf(scope);
        String branch = body.branch() == null || body.branch().isBlank() ? "bot/" + runId : body.branch().trim();
        String message = body.commitMessage() == null || body.commitMessage().isBlank()
                ? "Add " + (stage == null ? "generated project" : stage.title() + " output") + " from BOT run " + runId
                : body.commitMessage();
        Path work = Files.createTempDirectory("bot-push-");
        try {
            Path export = work.resolve("export");
            Files.createDirectories(export);
            int copied = stages.exportFiles(runId, stage, export);
            if (copied == 0) {
                throw new IllegalArgumentException(emptyPushMessage(runId, stage));
            }
            GitService.PushResult result = git.pushDirectory(export, repoUrl, branch, body.baseBranch(), body.folder(), message,
                    work, token, body.pull() == null || body.pull());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("repoUrl", repoUrl);
            out.put("branch", result.branch());
            out.put("baseBranch", result.baseBranch());
            out.put("commit", result.commit());
            out.put("branchUrl", result.branchUrl());
            out.put("compareUrl", result.compareUrl());
            out.put("files", copied);
            return out;
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(friendlyGitError(e.getMessage(), repoUrl));
        } finally {
            GitService.deleteQuietly(work);
        }
    }

    /** Why a run (or one stage of it) has nothing to push: names the run so a wrong/stale run id is easy to spot. */
    static String emptyPushMessage(String runId, StageType stage) {
        if (stage == null) {
            return "Run " + runId + " has no generated files to push: it holds only a training report, or nothing at all. "
                    + "This is usually a run id that is not the one your stages used (check the run id under the stages, "
                    + "or the Trainer's run box), so run the stages for this run first or press New run and Run all stages.";
        }
        return "Run " + runId + " has no " + stage.title() + " files to push yet: run the " + stage.title()
                + " stage for this run first.";
    }

    /** Turns raw git failures into advice that tells a bad URL, a bad token and a missing permission apart. */
    static String friendlyGitError(String raw, String repoUrl) {
        String m = raw == null ? "" : raw;
        String lower = m.toLowerCase(Locale.ROOT);
        if (lower.contains("ambiguous argument 'head'") || lower.contains("unknown revision")
                || lower.contains("remote branch") && lower.contains("not found in upstream")
                || lower.contains("does not have any commits") || lower.contains("empty repository")) {
            return "This repository looks empty or the branch has no commits yet - uncheck Pull first, or create an initial "
                    + "commit on GitHub; BOT will also try a first-push automatically.";
        }
        if (lower.contains("repository not found") || lower.contains("not found") && lower.contains("clone")) {
            return "Repository not found: " + repoUrl + ". Check the URL (https://github.com/owner/repo, no trailing dot), "
                    + "and that your token can see the repository if it is private.";
        }
        if (lower.contains("authentication failed") || lower.contains("invalid username or password")
                || lower.contains("could not read username") || lower.contains("bad credentials")) {
            return "GitHub rejected the token (bad, expired or revoked). Create a new personal access token with repo "
                    + "access and paste it again.";
        }
        if (lower.contains("permission denied") || lower.contains("403") || lower.contains("write access")) {
            return "The token was accepted but cannot write to " + repoUrl + ": give it repo (contents: write) access "
                    + "to that repository.";
        }
        return m;
    }

    @PostMapping("/run-all")
    public Map<String, Object> runAll(@RequestBody StageRequest request) {
        List<StageResponse> results = stages.runAll(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", results.get(0).runId());
        body.put("stages", results);
        return body;
    }

    /** The domain dropdown: groups of {id, label, samples}; any other text is accepted as a custom domain. */
    @GetMapping("/domains")
    public List<Map<String, Object>> domains() {
        return StageOptions.domainGroups();
    }

    /** The language dropdown: built-in languages (group "samples") then catalog languages (group "more"). */
    @GetMapping("/languages")
    public List<Map<String, String>> languages() {
        return StageOptions.languages();
    }

    @GetMapping("/training")
    public Map<String, Object> training() {
        return stages.trainingStats();
    }

    @GetMapping("/training/examples")
    public List<Map<String, String>> trainingExamples(@RequestParam(required = false) String stage,
                                                      @RequestParam(required = false) String domain,
                                                      @RequestParam(defaultValue = "100") int limit) {
        return stages.trainingExamples(stage, domain, limit);
    }

    @DeleteMapping("/training/examples/{id}")
    public Map<String, Object> deleteExample(@PathVariable String id) {
        if (!stages.deleteTrainingExample(id)) {
            throw new IllegalArgumentException("Training example " + id + " not found.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", id);
        body.put("stats", stages.trainingStats());
        return body;
    }

    @PostMapping("/{stage}")
    public StageResponse run(@PathVariable String stage, @RequestBody StageRequest request) {
        return stages.run(stageOf(stage), legacy(stage, request));
    }

    @PostMapping("/{stage}/approve")
    public Map<String, Object> approve(@PathVariable String stage, @RequestBody ApproveRequest request) {
        return stages.approve(stageOf(stage), request.domain(), request.language(), request.input(), request.output(),
                request.feedback(), request.runId());
    }

    /** Lightweight "does this run still exist?" check: 200 with the file count, or 404 (code run_not_found). */
    @GetMapping("/runs/{runId}/status")
    public Map<String, Object> runStatus(@PathVariable String runId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", runId);
        body.put("exists", true);
        body.put("files", stages.runFileCount(runId));
        return body;
    }

    @GetMapping("/runs/{runId}/file")
    public ResponseEntity<byte[]> file(@PathVariable String runId, @RequestParam String path) throws IOException {
        Path file = stages.file(runId, path).orElseThrow(() -> new IllegalArgumentException("File not found: " + path));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName().toString()).build().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body(Files.readAllBytes(file));
    }

    /** Business logic of one code file ({@code path}) or of a whole stage/run, as a Markdown download. */
    @GetMapping("/runs/{runId}/logic")
    public ResponseEntity<byte[]> logic(@PathVariable String runId, @RequestParam(required = false) String stage,
                                        @RequestParam(required = false) String path) {
        StageType only = stage == null || stage.isBlank() ? null : stageOf(stage);
        String markdown = stages.businessLogic(runId, only, path);
        String name = path == null || path.isBlank() ? "BUSINESS_LOGIC.md"
                : com.agentic.chatbot.stage.StageBusinessLogic.companionName(path).replaceAll("^.*/", "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(markdown.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @GetMapping("/runs/{runId}/zip")
    public ResponseEntity<byte[]> zip(@PathVariable String runId, @RequestParam(required = false) String stage) {
        StageType only = stage == null || stage.isBlank() ? null : stageOf(stage);
        String name = "bot-" + (only == null ? "run" : only.id()) + "-" + runId + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(stages.zip(runId, only));
    }

    /**
     * Old Tester ids select one part. For functional-test, "language" used to mean the Selenium language
     * (java, ruby, both); it becomes the automation choice when no automation option is given.
     */
    static StageRequest legacy(String id, StageRequest request) {
        var part = StageType.testerPart(id);
        if (part.isEmpty()) {
            return request;
        }
        StageRequest r = request.withOption("testerPart", part.get());
        if (part.get().equals("automation") && request.option("automation", "").isBlank()) {
            String language = request.option("language", "");
            if (StageAutomation.fromId(language).isPresent()) {
                r = r.withOption("automation", language).withOption("language", null);
            }
        }
        return r;
    }

    private static StageType stageOf(String id) {
        return StageType.fromId(id).orElseThrow(() -> new IllegalArgumentException(
                "Unknown stage '" + id + "'. Use ba, developer, tester, deploy or trainer."));
    }

    /** The run folder is gone (e.g. hosting restarted): 404 with a code the page uses to clear its stale run. */
    @ExceptionHandler(RunNotFoundException.class)
    public ResponseEntity<Map<String, String>> runNotFound(RunNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage(), "code", RunNotFoundException.CODE));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RequestOrigin.HostOnlyException.class)
    public ResponseEntity<Map<String, String>> hostOnly(RequestOrigin.HostOnlyException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, String>> ioFailure(UncheckedIOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
    }
}
