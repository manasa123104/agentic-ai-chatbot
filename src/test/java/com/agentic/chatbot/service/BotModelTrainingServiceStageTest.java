package com.agentic.chatbot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotModelTrainingServiceStageTest {

    @TempDir
    Path tmp;

    private BotModelTrainingService store() {
        BotModelTrainingService s = new BotModelTrainingService(tmp.resolve("data/bot-training.json").toString());
        s.load();
        return s;
    }

    @Test
    void storesStageExamplesPerStageAndDomainAndReloadsThem() {
        BotModelTrainingService store = store();
        store.recordStageExample("ba", "banking", "fund transfer", "stories v1", "");
        store.recordStageExample("ba", "banking", "loan approval", "stories v2", "more edge cases");
        store.recordStageExample("ba", "logistics", "shipment tracking", "tracking stories", "");
        store.recordStageExample("developer", "banking", "stories", "code", "");

        BotModelTrainingService reloaded = store();
        List<Map<String, String>> banking = reloaded.stageExamples("ba", "banking", 5);
        assertEquals(2, banking.size());
        assertEquals("stories v2", banking.get(0).get("output"), "newest first");
        assertEquals("more edge cases", banking.get(0).get("feedback"));
        assertEquals(1, reloaded.stageExamples("ba", "banking", 1).size());
        assertEquals(1, reloaded.stageExamples("ba", "logistics", 5).size());
        assertTrue(reloaded.stageExamples("unit-test", "banking", 5).isEmpty());

        Map<String, Object> stats = reloaded.stageStats();
        assertEquals(4, stats.get("total"));
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Integer>> stages = (Map<String, Map<String, Integer>>) stats.get("stages");
        assertEquals(2, stages.get("ba").get("banking"));
        assertEquals(1, stages.get("developer").get("banking"));
    }

    @Test
    void chatPromptsDoNotEvictApprovedStageExamples() throws Exception {
        BotModelTrainingService store = store();
        store.recordStageExample("functional-test", "logistics", "stories", "selenium", "");
        for (int i = 0; i < 205; i++) {
            store.record("prompt " + i, "story " + i, "");
        }
        assertEquals(1, store.stageExamples("functional-test", "logistics", 5).size());
        assertEquals(201, store.recentExamples(1000).size(), "200 prompts are kept plus the stage example");
        assertTrue(Files.readString(tmp.resolve("data/bot-training.json")).contains("\"kind\" : \"stage-example\""));
    }

    @Test
    void listsAndDeletesStageExamples() {
        BotModelTrainingService store = store();
        Map<String, String> a = store.recordStageExample("tester", "healthcare:python", "code", "tests", "");
        store.recordStageExample("deploy", "banking", "code", "Dockerfile", "");
        store.record("chat prompt", "story", "");

        assertEquals(2, store.listStageExamples("", "", 10).size(), "chat prompts are not stage examples");
        assertEquals(1, store.listStageExamples("tester", "healthcare:python", 10).size());
        assertTrue(store.deleteStageExample(a.get("id")));
        assertEquals(1, store().listStageExamples(null, null, 10).size(), "deletion is persisted");
        assertEquals(1, store.recentExamples(10).stream().filter(e -> e.containsKey("refinedStory")
                && "story".equals(e.get("refinedStory"))).count());
    }

    @Test
    void existingChatTrainingStillWorks() {
        BotModelTrainingService store = store();
        Map<String, String> status = store.record("bakery site", "refined", "system");
        assertEquals("1", status.get("promptCount"));
        assertEquals(0, store.stageStats().get("total"));
    }
}
