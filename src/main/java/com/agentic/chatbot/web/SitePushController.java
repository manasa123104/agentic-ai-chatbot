package com.agentic.chatbot.web;

import com.agentic.chatbot.service.GitService;
import com.agentic.chatbot.service.SiteExportService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Push the generated website to a new GitHub branch. Same rule as the converter and the stage bots: the host machine may
 * use its own Git login, every other visitor pastes their own token (used for this push only, never stored or logged).
 */
@RestController
@RequestMapping("/api/site")
public class SitePushController {

    /** Body of a push request; {@code token} is the visitor's own GitHub token. */
    public record PushRequest(String repoUrl, String branch, String baseBranch, String folder, String commitMessage,
                              String token, Boolean pull) {
    }

    /** Folder in the repository when none is typed, so the site never lands loose in the root of someone's repo. */
    static final String DEFAULT_FOLDER = "generated-site";

    private final SiteExportService export;
    private final GitService git;

    public SitePushController(SiteExportService export, GitService git) {
        this.export = export;
        this.git = git;
    }

    @PostMapping("/push")
    public Map<String, Object> push(@RequestBody PushRequest body, HttpServletRequest request) throws IOException {
        String token = GitPushPolicy.authorize(request, body.repoUrl(), body.token());
        String repoUrl = body.repoUrl() == null ? "" : body.repoUrl().trim();
        if (!export.hasSite()) {
            throw new IllegalArgumentException("There is no generated website yet. Describe a website to BOT first, then push it.");
        }
        String brand = export.brand();
        String branch = body.branch() == null || body.branch().isBlank()
                ? "bot/site-" + SiteExportService.slug(brand) + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                : body.branch().trim();
        String folder = body.folder() == null || body.folder().isBlank() ? DEFAULT_FOLDER
                : body.folder().trim().equals("/") ? "" : body.folder().trim();
        String message = body.commitMessage() == null || body.commitMessage().isBlank()
                ? "Add generated website " + brand + " from BOT" : body.commitMessage();

        Path work = Files.createTempDirectory("bot-site-push-");
        try {
            Path files = work.resolve("export");
            int copied = export.export(files);
            GitService.PushResult result = git.pushDirectory(files, repoUrl, branch, body.baseBranch(), folder, message,
                    work, token, body.pull() == null || body.pull());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("repoUrl", repoUrl);
            out.put("brand", brand);
            out.put("branch", result.branch());
            out.put("baseBranch", result.baseBranch());
            out.put("folder", folder);
            out.put("commit", result.commit());
            out.put("branchUrl", result.branchUrl());
            out.put("compareUrl", result.compareUrl());
            out.put("files", copied);
            return out;
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException(e.getMessage());
        } finally {
            GitService.deleteQuietly(work);
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler(RequestOrigin.HostOnlyException.class)
    public ResponseEntity<Map<String, String>> hostOnly(RequestOrigin.HostOnlyException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({UncheckedIOException.class, IOException.class})
    public ResponseEntity<Map<String, String>> ioFailure(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
