package com.agentic.chatbot.web;

import com.agentic.chatbot.model.ConversionJob;
import com.agentic.chatbot.service.BusinessLogicFiles;
import com.agentic.chatbot.service.RepositoryConversionService;
import com.agentic.chatbot.service.RepositoryConversionService.Options;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Project / repository conversion: start a job from a Git URL, a folder on this machine, uploaded files or a ZIP,
 * or large pasted code; follow its progress; open, edit, save and download the converted files; push them to a
 * Git branch.
 */
@RestController
@RequestMapping("/api/convert/jobs")
public class RepositoryConversionController {

    private final RepositoryConversionService jobs;

    public RepositoryConversionController(RepositoryConversionService jobs) {
        this.jobs = jobs;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversionJob start(@RequestParam String inputType,
                               @RequestParam String targetLanguage,
                               @RequestParam(defaultValue = "auto") String sourceLanguage,
                               @RequestParam(required = false) String instructions,
                               @RequestParam(defaultValue = "false") boolean addComments,
                               @RequestParam(defaultValue = "false") boolean explainLogic,
                               @RequestParam(required = false) String repoUrl,
                               @RequestParam(required = false) String branch,
                               @RequestParam(required = false) String localPath,
                               @RequestParam(required = false) String subfolder,
                               @RequestParam(required = false) String fileName,
                               @RequestParam(required = false) String code,
                               @RequestParam(required = false) List<MultipartFile> files,
                               HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!RequestOrigin.isHost(request)) {
            if ("local".equals(inputType)) {
                throw new RequestOrigin.HostOnlyException(RequestOrigin.HOST_ONLY_LOCAL_FOLDER);
            }
            if ("git".equals(inputType) && (repoUrl == null || !repoUrl.trim().toLowerCase(java.util.Locale.ROOT).startsWith("https://"))) {
                throw new RequestOrigin.HostOnlyException(RequestOrigin.HOST_ONLY_REPO_URL);
            }
        }
        Options options = new Options(sourceLanguage, targetLanguage, instructions, addComments, explainLogic);
        ConversionJob job = switch (inputType) {
            case "git" -> jobs.startFromGit(repoUrl, branch, subfolder, options);
            case "local" -> jobs.startFromLocal(localPath, subfolder, options);
            case "upload" -> jobs.startFromUploads(files, subfolder, options);
            case "text" -> jobs.startFromText(fileName, code, options);
            default -> throw new IllegalArgumentException("inputType must be git, local, upload or text.");
        };
        jobs.assignOwner(job, VisitorCookie.owner(request, response));
        return jobs.snapshot(job);
    }

    /** Jobs belong to the browser that started them; anyone else gets 404 (the host machine sees all). */
    private ConversionJob own(String id, HttpServletRequest request, HttpServletResponse response) {
        return jobs.getFor(id, VisitorCookie.owner(request, response), RequestOrigin.isHost(request))
                .orElseThrow(JobNotFoundException::new);
    }

    public static class JobNotFoundException extends RuntimeException {
        public JobNotFoundException() {
            super("Unknown conversion job.");
        }
    }

    @GetMapping
    public List<ConversionJob> list(HttpServletRequest request, HttpServletResponse response) {
        return jobs.listFor(VisitorCookie.owner(request, response), RequestOrigin.isHost(request));
    }

    @GetMapping("/{id}")
    public ConversionJob get(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        return jobs.snapshot(own(id, request, response));
    }

    /** Stop button: ends the job after the current file; its finished files are kept. */
    @PostMapping("/{id}/stop")
    public ConversionJob stop(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        own(id, request, response);
        return jobs.stop(id);
    }

    /** Resume button: converts the remaining files of a stopped job. */
    @PostMapping("/{id}/resume")
    public ConversionJob resume(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        own(id, request, response);
        return jobs.resume(id);
    }

    @GetMapping("/{id}/files")
    public List<String> files(@PathVariable String id, @RequestParam(defaultValue = "output") String side,
                              HttpServletRequest request, HttpServletResponse response) throws IOException {
        own(id, request, response);
        return jobs.listFiles(id, side);
    }

    @GetMapping("/{id}/file")
    public Map<String, String> read(@PathVariable String id, @RequestParam String path,
                                    @RequestParam(defaultValue = "output") String side,
                                    HttpServletRequest request, HttpServletResponse response) throws IOException {
        own(id, request, response);
        return Map.of("path", path, "side", side, "content", jobs.readFile(id, side, path));
    }

    @PutMapping("/{id}/file")
    public Map<String, Object> save(@PathVariable String id, @RequestParam String path,
                                    @RequestBody Map<String, String> body,
                                    HttpServletRequest request, HttpServletResponse response) throws IOException {
        own(id, request, response);
        jobs.saveFile(id, path, body.get("content"));
        return Map.of("saved", true, "path", path);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id, @RequestParam String path,
                                           @RequestParam(defaultValue = "output") String side,
                                           HttpServletRequest request, HttpServletResponse response) throws IOException {
        own(id, request, response);
        Path file = jobs.resolveFile(id, side, path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName().toString(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(Files.readAllBytes(file));
    }

    @GetMapping("/{id}/zip")
    public ResponseEntity<StreamingResponseBody> zip(@PathVariable String id, HttpServletRequest request,
                                                      HttpServletResponse response) {
        ConversionJob job = own(id, request, response);
        String name = "converted-" + job.getTargetLanguageId() + "-" + id + ".zip";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(out -> jobs.writeZip(id, out));
    }

    /** {@code token} is the visitor's own GitHub token (never stored or logged); {@code pull} starts from the remote branch. */
    public record PushRequest(String repoUrl, String branch, String baseBranch, String folder, String commitMessage,
                              String token, Boolean pull) {
        public PushRequest(String repoUrl, String branch, String baseBranch, String folder, String commitMessage) {
            this(repoUrl, branch, baseBranch, folder, commitMessage, null, null);
        }
    }

    @PostMapping("/{id}/push")
    public ConversionJob.PushRecord push(@PathVariable String id, @RequestBody PushRequest body,
                                         HttpServletRequest request, HttpServletResponse response) throws IOException {
        own(id, request, response);
        String token = GitPushPolicy.authorize(request, body.repoUrl(), body.token());
        return jobs.push(id, body.repoUrl(), body.branch(), body.baseBranch(), body.folder(),
                body.commitMessage(), token, body.pull() == null || body.pull());
    }

    /** Business logic of one converted file as a Markdown download named like the file (Foo.py -> Foo.business-logic.md). */
    @GetMapping("/{id}/logic")
    public ResponseEntity<byte[]> logic(@PathVariable String id, @RequestParam String path, HttpServletRequest request,
                                        HttpServletResponse response) {
        own(id, request, response);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(BusinessLogicFiles.downloadName(path), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(jobs.logicMarkdown(id, path).getBytes(StandardCharsets.UTF_8));
    }

    @ExceptionHandler(JobNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(JobNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(RequestOrigin.HostOnlyException.class)
    public ResponseEntity<Map<String, String>> hostOnly(RequestOrigin.HostOnlyException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> badRequest(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}
