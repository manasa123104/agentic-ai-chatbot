package com.agentic.chatbot.web;

import com.agentic.chatbot.model.ChatHistoryEntry;
import com.agentic.chatbot.service.ChatHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/history")
public class ChatHistoryApiController {

    private final ChatHistoryService historyService;

    public ChatHistoryApiController(ChatHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public List<ChatHistoryEntry> list() {
        return historyService.list();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ChatHistoryEntry> get(@PathVariable String id) {
        return historyService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String id) {
        boolean removed = historyService.delete(id);
        if (!removed) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("deleted", true, "id", id));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clear() {
        historyService.clear();
        return ResponseEntity.ok(Map.of("cleared", true));
    }
}
