package com.agentic.chatbot.mcp;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.BotModelTrainingService;
import com.agentic.chatbot.service.GitHubPreflight;
import com.agentic.chatbot.service.GitService;
import com.agentic.chatbot.stage.StageLlm;
import com.agentic.chatbot.stage.StageService;
import com.agentic.chatbot.web.StageApiController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpControllerTest {

    @TempDir
    Path tmp;

    private McpController mcp() {
        BotModelTrainingService training = new BotModelTrainingService(tmp.resolve("bot-training.json").toString());
        training.load();
        StageService stages = new StageService(new StageLlm(new LlmClient() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new AssertionError("offline");
            }
        }), training, tmp.resolve("generated-app").toString());
        StageApiController api = new StageApiController(stages, null, new GitService("", "BOT", "bot@example.com"),
                new GitHubPreflight(""));
        McpProperties mp = new McpProperties();
        mp.setEnabled(true);
        return new McpController(mp, stages, api);
    }

    @Test
    void toolsListIncludesStageAndPushTools() {
        Map<String, Object> res = mcp().handle(Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "tools/list", "params", Map.of()),
                null, new MockHttpServletRequest());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) res.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) result.get("tools");
        assertTrue(tools.stream().anyMatch(t -> "run_stage".equals(t.get("name"))));
        assertTrue(tools.stream().anyMatch(t -> "push_run".equals(t.get("name"))));
        assertTrue(tools.stream().anyMatch(t -> "list_stages".equals(t.get("name"))));
    }

    @Test
    void toolsCallListStagesReturnsDataAnalyst() {
        Map<String, Object> res = mcp().handle(Map.of(
                "jsonrpc", "2.0", "id", 2, "method", "tools/call",
                "params", Map.of("name", "list_stages", "arguments", Map.of())),
                null, new MockHttpServletRequest());
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) res.get("result");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> stages = (List<Map<String, String>>) result.get("structuredContent");
        assertTrue(stages.stream().anyMatch(s -> "data-analyst".equals(s.get("id"))));
        assertFalse(result.containsKey("guardrail"));
    }
}
