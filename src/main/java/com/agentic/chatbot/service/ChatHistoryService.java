package com.agentic.chatbot.service;

import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.ChatHistoryEntry;
import com.agentic.chatbot.model.ConversionJob;
import com.agentic.chatbot.model.ConversionRequest;
import com.agentic.chatbot.model.ConversionResponse;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.stage.StageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Persists chatbot queries and pipeline results to a JSON file so they survive restarts.
 */
@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final int MAX_ENTRIES = 100;
    static final String SOURCE_HOME = "home";
    static final String SOURCE_CONVERT = "convert";
    static final String SOURCE_STAGES = "stages";

    private final Path historyFile;
    private final ObjectMapper mapper;
    private final CopyOnWriteArrayList<ChatHistoryEntry> entries = new CopyOnWriteArrayList<>();

    public ChatHistoryService(@Value("${agentic.history-file:data/chat-history.json}") String historyFile) {
        this.historyFile = Path.of(historyFile).toAbsolutePath().normalize();
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    void load() {
        try {
            if (Files.exists(historyFile)) {
                List<ChatHistoryEntry> loaded = mapper.readValue(
                        historyFile.toFile(),
                        new TypeReference<List<ChatHistoryEntry>>() {});
                if (loaded != null) {
                    entries.addAll(loaded);
                }
                log.info("Loaded {} chat history entries from {}", entries.size(), historyFile);
            }
        } catch (Exception e) {
            log.warn("Could not load chat history from {}: {}", historyFile, e.getMessage());
        }
    }

    public ChatHistoryEntry save(String userStory, PipelineResponse response) {
        ChatHistoryEntry entry = create(response != null && response.getConversion() != null
                ? SOURCE_CONVERT : SOURCE_HOME, userStory);
        if (response != null) {
            entry.setSuccess(response.isSuccess());
            entry.setMessage(response.getMessage());
            entry.setPreviewUrl(response.getPreviewUrl());
            entry.setRedirectPath(response.getRedirectPath());
            entry.setOutputDirectory(response.getOutputDirectory());
            List<BotStepLog> steps = response.getSteps();
            entry.setSteps(steps != null ? new ArrayList<>(steps) : new ArrayList<>());
            entry.setConversion(response.getConversion());
        }
        if (entry.getConversion() != null && blank(entry.getRedirectPath())) {
            entry.setRedirectPath("/convert?history=" + entry.getId());
        }
        store(entry);
        return entry;
    }

    /** Paste-code conversion from the Code converter page. */
    public ChatHistoryEntry saveConversion(ConversionRequest request, ConversionResponse response) {
        String from = response != null && !blank(response.sourceLanguage()) ? response.sourceLanguage() : "code";
        String to = response != null && !blank(response.targetLanguage()) ? response.targetLanguage() : "target";
        String snippet = request != null ? clip(request.code(), 80) : "";
        String title = "Code converter · " + from + " → " + to + (snippet.isBlank() ? "" : " · " + snippet);
        ChatHistoryEntry entry = create(SOURCE_CONVERT, title);
        entry.setSuccess(response != null && response.success());
        entry.setMessage(response != null ? response.message() : "");
        entry.setConversion(response);
        entry.setRedirectPath("/convert?history=" + entry.getId());
        store(entry);
        return entry;
    }

    /** Project conversion job: one sidebar row per job, updated when the job finishes. */
    public ChatHistoryEntry saveConversionJob(ConversionJob job) {
        if (job == null || blank(job.getId())) {
            return null;
        }
        String title = convertJobTitle(job);
        ChatHistoryEntry entry = findByJobId(job.getId()).orElse(null);
        if (entry == null) {
            entry = create(SOURCE_CONVERT, title);
            entry.setJobId(job.getId());
            entry.setRedirectPath("/convert?job=" + job.getId());
            entries.add(0, entry);
        } else if (blank(entry.getUserStory())) {
            entry.setUserStory(title);
        }
        entry.setSuccess(job.getStatus() == ConversionJob.Status.DONE);
        entry.setMessage(job.getMessage());
        bump(entry);
        trimToMax();
        persist();
        return entry;
    }

    /** One six-stage run: later stages refresh the same row. */
    public ChatHistoryEntry saveStage(StageResponse response, String input) {
        if (response == null || blank(response.runId())) {
            return null;
        }
        String title = stageTitle(response, input);
        ChatHistoryEntry entry = findByRunId(response.runId()).orElse(null);
        if (entry == null) {
            entry = create(SOURCE_STAGES, title);
            entry.setRunId(response.runId());
            entry.setRedirectPath("/stages?run=" + response.runId());
            entries.add(0, entry);
        }
        entry.setSuccess(response.success());
        String domain = blank(response.domain()) ? "" : " · " + response.domain();
        entry.setMessage((response.stageTitle() == null ? response.stage() : response.stageTitle())
                + (response.success() ? " finished" : " failed") + domain);
        bump(entry);
        trimToMax();
        persist();
        return entry;
    }

    /** Import an existing stage run folder (restart / backfill). */
    public ChatHistoryEntry saveStageRun(java.util.Map<String, Object> run) {
        if (run == null) {
            return null;
        }
        Object id = run.get("runId");
        String runId = id == null ? "" : String.valueOf(id);
        if (blank(runId)) {
            return null;
        }
        if (findByRunId(runId).isPresent()) {
            return findByRunId(runId).orElseThrow();
        }
        String domain = firstText(run.get("domainLabel"), run.get("domain"));
        String language = firstText(run.get("language"));
        String input = firstStageInput(run);
        String title = "Stage bots"
                + (domain.isBlank() ? "" : " · " + domain)
                + (language.isBlank() ? "" : " · " + language)
                + (input.isBlank() ? "" : " · " + clip(input, 60));
        ChatHistoryEntry entry = create(SOURCE_STAGES, title);
        entry.setRunId(runId);
        entry.setRedirectPath("/stages?run=" + runId);
        entry.setSuccess(true);
        entry.setMessage(stageSummary(run));
        store(entry);
        return entry;
    }

    public Optional<ChatHistoryEntry> findByJobId(String jobId) {
        if (blank(jobId)) {
            return Optional.empty();
        }
        return entries.stream().filter(e -> jobId.equals(e.getJobId())).findFirst();
    }

    public Optional<ChatHistoryEntry> findByRunId(String runId) {
        if (blank(runId)) {
            return Optional.empty();
        }
        return entries.stream().filter(e -> runId.equals(e.getRunId())).findFirst();
    }

    public List<ChatHistoryEntry> list() {
        return Collections.unmodifiableList(entries);
    }

    public Optional<ChatHistoryEntry> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return entries.stream().filter(e -> id.equals(e.getId())).findFirst();
    }

    public boolean delete(String id) {
        boolean removed = entries.removeIf(e -> id != null && id.equals(e.getId()));
        if (removed) {
            persist();
        }
        return removed;
    }

    public void clear() {
        entries.clear();
        persist();
    }

    private ChatHistoryEntry create(String source, String title) {
        ChatHistoryEntry entry = new ChatHistoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setCreatedAt(Instant.now());
        entry.setSource(source);
        entry.setUserStory(title != null ? title.trim() : "");
        return entry;
    }

    private void store(ChatHistoryEntry entry) {
        entries.add(0, entry);
        trimToMax();
        persist();
    }

    private void bump(ChatHistoryEntry entry) {
        if (!entries.isEmpty() && entries.get(0) == entry) {
            return;
        }
        entries.remove(entry);
        entries.add(0, entry);
    }

    private static String convertJobTitle(ConversionJob job) {
        String label = blank(job.getSourceLabel()) ? "project" : job.getSourceLabel();
        String target = blank(job.getTargetLanguage()) ? job.getTargetLanguageId() : job.getTargetLanguage();
        return "Code converter · " + label + (blank(target) ? "" : " → " + target);
    }

    private static String stageTitle(StageResponse response, String input) {
        String stage = blank(response.stageTitle()) ? response.stage() : response.stageTitle();
        String domain = blank(response.domain()) ? "" : " · " + response.domain();
        String clipped = clip(input, 70);
        return "Stage bots · " + stage + domain + (clipped.isBlank() ? "" : " · " + clipped);
    }

    private static String stageSummary(java.util.Map<String, Object> run) {
        Object stages = run.get("stages");
        if (stages instanceof java.util.Map<?, ?> map) {
            return map.size() + " stage" + (map.size() == 1 ? "" : "s") + " saved";
        }
        return "Stage run saved";
    }

    private static String firstStageInput(java.util.Map<String, Object> run) {
        Object stages = run.get("stages");
        if (stages instanceof java.util.Map<?, ?> map) {
            for (String key : java.util.List.of("ba", "developer", "tester", "data-analyst", "deploy", "trainer")) {
                Object raw = map.get(key);
                if (raw instanceof java.util.Map<?, ?> entry && entry.get("input") != null) {
                    String input = String.valueOf(entry.get("input")).trim();
                    if (!input.isBlank()) {
                        return input;
                    }
                }
            }
        }
        return "";
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return "";
    }

    private static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.trim().replaceAll("\\s+", " ");
        return flat.length() <= max ? flat : flat.substring(0, Math.max(0, max - 1)) + "…";
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private void trimToMax() {
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
    }

    private synchronized void persist() {
        try {
            Files.createDirectories(historyFile.getParent());
            mapper.writeValue(historyFile.toFile(), new ArrayList<>(entries));
        } catch (IOException e) {
            log.error("Failed to persist chat history to {}", historyFile, e);
        }
    }
}
