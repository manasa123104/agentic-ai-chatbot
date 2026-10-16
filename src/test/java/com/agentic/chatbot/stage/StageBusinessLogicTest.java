package com.agentic.chatbot.stage;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageBusinessLogicTest {

    @Test
    void extractsBusinessRuleCommentsOnceAndInOrder() {
        String code = "class A {\n  // Business rule: Amount must be positive.\n  /* Business rule: Daily limit is 5000 */\n"
                + "  # business rule: Amount must be positive.\n}\n";
        assertEquals(List.of("Amount must be positive.", "Daily limit is 5000"), StageBusinessLogic.rules(code));
    }

    @Test
    void companionNamesKeepTheDirectoryAndDropTheExtension() {
        assertEquals("src/Foo.business-logic.md", StageBusinessLogic.companionName("src/Foo.java"));
        assertEquals("Foo.business-logic.md", StageBusinessLogic.companionName("Foo.py"));
        assertTrue(StageBusinessLogic.isCode("a/b/Foo.py"));
        assertFalse(StageBusinessLogic.isCode("pom.xml"));
    }

    @Test
    void filesWithoutRulesGetAShortNoteAndTheProjectFileListsStories() {
        assertTrue(StageBusinessLogic.fileMarkdown("A.java", "class A {}").contains("No business rules"));
        Map<String, String> code = new LinkedHashMap<>();
        code.put("A.java", "// Business rule: Rule one");
        code.put("B.java", "class B {}");
        String md = StageBusinessLogic.projectMarkdown("run x", "As a user I want...", code);
        assertTrue(md.contains("## User stories") && md.contains("### A.java") && md.contains("- Rule one"));
        assertFalse(md.contains("### B.java"));
        assertTrue(StageBusinessLogic.projectMarkdown("run x", "", Map.of()).contains("No business-rule comments"));
    }
}