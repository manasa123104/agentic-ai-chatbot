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
        while (examples.size() > MAX_EXAMPLES) {
            examples.remove(examples.size() - 1);
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

    private void persist() {
        try {
            Files.createDirectories(storeFile.getParent());
            Files.writeString(storeFile, mapper.writeValueAsString(examples));
        } catch (Exception e) {
            log.warn("Could not save BOT training store: {}", e.getMessage());
        }
    }
}
