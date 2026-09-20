package com.agentic.chatbot.service;

import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.ChatHistoryEntry;
import com.agentic.chatbot.model.PipelineResponse;
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
        ChatHistoryEntry entry = new ChatHistoryEntry();
        entry.setId(UUID.randomUUID().toString());
        entry.setCreatedAt(Instant.now());
        entry.setUserStory(userStory != null ? userStory.trim() : "");
        if (response != null) {
            entry.setSuccess(response.isSuccess());
            entry.setMessage(response.getMessage());
            entry.setPreviewUrl(response.getPreviewUrl());
            entry.setRedirectPath(response.getRedirectPath());
            entry.setOutputDirectory(response.getOutputDirectory());
            List<BotStepLog> steps = response.getSteps();
            entry.setSteps(steps != null ? new ArrayList<>(steps) : new ArrayList<>());
        }
        entries.add(0, entry);
        trimToMax();
        persist();
        return entry;
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
