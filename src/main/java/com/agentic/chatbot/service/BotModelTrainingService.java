package com.agentic.chatbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stores end-user prompts so the BOT model grows from untrained → trained over time.
 */
@Service
public class BotModelTrainingService {

    private static final Logger log = LoggerFactory.getLogger(BotModelTrainingService.class);
    private static final int MAX_EXAMPLES = 200;
    private static final int MAX_STAGE_EXAMPLES = 300;
    private static final int MAX_STAGE_INPUT = 6000;
    private static final int MAX_STAGE_OUTPUT = 40000;
    private static final String STAGE_KIND = "stage-example";

    private final Path storeFile;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final CopyOnWriteArrayList<Map<String, String>> examples = new CopyOnWriteArrayList<>();

    public BotModelTrainingService(
            @Value("${agentic.training-file:data/bot-training.json}") String trainingFile) {
        this.storeFile = Path.of(trainingFile).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void load() {
        try {
            if (Files.exists(storeFile)) {
                List<Map<String, String>> loaded = mapper.readValue(
                        Files.readString(storeFile), new TypeReference<>() {});
                if (loaded != null) {
                    examples.addAll(loaded);
                }
            }
            log.info("BOT model training store: {} examples ({})",
                    examples.size(), examples.isEmpty() ? "untrained" : "learning");
        } catch (Exception e) {
            log.warn("Could not load BOT training store: {}", e.getMessage());
        }
    }

    public synchronized Map<String, String> record(String userPrompt, String refinedStory, String systemPrompt) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("id", "t-" + Instant.now().toEpochMilli());
        entry.put("at", Instant.now().toString());
        entry.put("userPrompt", userPrompt == null ? "" : userPrompt.trim());
        entry.put("refinedStory", refinedStory == null ? "" : refinedStory.trim());
        entry.put("systemPrompt", systemPrompt == null ? "" : systemPrompt.trim());
        examples.add(0, entry);
        long prompts = examples.stream().filter(e -> !isStageExample(e)).count();
        for (int i = examples.size() - 1; i >= 0 && prompts > MAX_EXAMPLES; i--) {
            if (!isStageExample(examples.get(i))) {
                examples.remove(i);
                prompts--;
            }
        }
        persist();
        return status();
    }

    public Map<String, String> status() {
        Map<String, String> status = new LinkedHashMap<>();
        int n = examples.size();
        status.put("promptCount", String.valueOf(n));
        status.put("state", n == 0 ? "untrained" : (n < 5 ? "warming-up" : "training"));
        status.put("label", n == 0
                ? "Untrained BOT model"
                : "BOT model trained on " + n + " prompt" + (n == 1 ? "" : "s"));
        return status;
    }

    public List<Map<String, String>> recentExamples(int limit) {
        int n = Math.min(Math.max(limit, 0), examples.size());
        return new ArrayList<>(examples.subList(0, n));
    }

    /**
     * Stores an output a reviewer approved (possibly after correcting it) for one stage bot. These examples are
     * kept apart from chat prompts when trimming, and are fed back to the stage as few-shot examples.
     */
    public synchronized Map<String, String> recordStageExample(String stage, String domain, String input,
                                                               String output, String feedback) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("id", "s-" + Instant.now().toEpochMilli() + "-" + examples.size());
        entry.put("at", Instant.now().toString());
        entry.put("kind", STAGE_KIND);
        entry.put("stage", stage);
        entry.put("domain", domain);
        entry.put("input", clip(input, MAX_STAGE_INPUT));
        entry.put("output", clip(output, MAX_STAGE_OUTPUT));
        entry.put("feedback", clip(feedback, 1000));
        // Keep the chat-history fields filled so older readers of this file still work.
        entry.put("userPrompt", entry.get("input"));
        entry.put("refinedStory", "");
        entry.put("systemPrompt", "");
        examples.add(0, entry);
        long stageCount = examples.stream().filter(BotModelTrainingService::isStageExample).count();
        for (int i = examples.size() - 1; i >= 0 && stageCount > MAX_STAGE_EXAMPLES; i--) {
            if (isStageExample(examples.get(i))) {
                examples.remove(i);
                stageCount--;
            }
        }
        persist();
        return entry;
    }

    /** Most recent approved examples for a stage, preferring the same domain. */
    public List<Map<String, String>> stageExamples(String stage, String domain, int limit) {
        List<Map<String, String>> same = new ArrayList<>();
        for (Map<String, String> e : examples) {
            if (isStageExample(e) && stage.equals(e.get("stage")) && domain.equals(e.get("domain"))) {
                same.add(e);
                if (same.size() >= limit) {
                    break;
                }
            }
        }
        return same;
    }

    /** Approved example counts: {@code total} plus {@code stages -> stage -> domain -> count}. */
    public Map<String, Object> stageStats() {
        Map<String, Map<String, Integer>> byStage = new LinkedHashMap<>();
        int total = 0;
        for (Map<String, String> e : examples) {
            if (isStageExample(e)) {
                total++;
                byStage.computeIfAbsent(e.getOrDefault("stage", "?"), k -> new LinkedHashMap<>())
                        .merge(e.getOrDefault("domain", "?"), 1, Integer::sum);
            }
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("stages", byStage);
        return stats;
    }

    /** Approved stage examples, newest first, optionally filtered by stage and/or domain key (blank = any). */
    public List<Map<String, String>> listStageExamples(String stage, String domain, int limit) {
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, String> e : examples) {
            boolean stageOk = stage == null || stage.isBlank() || stage.equals(e.get("stage"));
            boolean domainOk = domain == null || domain.isBlank() || domain.equals(e.get("domain"));
            if (isStageExample(e) && stageOk && domainOk) {
                out.add(e);
                if (out.size() >= limit) {
                    break;
                }
            }
        }
        return out;
    }

    /** Deletes one approved stage example; chat prompts cannot be deleted this way. */
    public synchronized boolean deleteStageExample(String id) {
        boolean removed = examples.removeIf(e -> isStageExample(e) && e.get("id") != null && e.get("id").equals(id));
        if (removed) {
            persist();
        }
        return removed;
    }

    private static boolean isStageExample(Map<String, String> entry) {
        return STAGE_KIND.equals(entry.get("kind"));
    }

    private static String clip(String text, int max) {
        String t = text == null ? "" : text.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }

    private void persist() {
        try {
            Files.createDirectories(storeFile.getParent());
            Files.writeString(storeFile, mapper.writeValueAsString(examples));
        } catch (Exception e) {
            log.warn("Could not save BOT training store: {}", e.getMessage());
        }
    }
}
