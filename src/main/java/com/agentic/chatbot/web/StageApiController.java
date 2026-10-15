package com.agentic.chatbot.web;

import com.agentic.chatbot.stage.StageAutomation;
import com.agentic.chatbot.stage.StageRequest;
import com.agentic.chatbot.stage.StageResponse;
import com.agentic.chatbot.stage.StageService;
import com.agentic.chatbot.stage.StageType;
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private final StageService stages;

    public StageApiController(StageService stages) {
        this.stages = stages;
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

    @GetMapping("/runs/{runId}/file")
    public ResponseEntity<byte[]> file(@PathVariable String runId, @RequestParam String path) throws IOException {
        Path file = stages.file(runId, path).orElseThrow(() -> new IllegalArgumentException("File not found: " + path));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.getFileName().toString()).build().toString())
                .contentType(MediaType.TEXT_PLAIN)
                .body(Files.readAllBytes(file));
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(UncheckedIOException.class)
    public ResponseEntity<Map<String, String>> ioFailure(UncheckedIOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
    }
}
