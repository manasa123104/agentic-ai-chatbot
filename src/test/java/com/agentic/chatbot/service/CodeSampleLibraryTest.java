package com.agentic.chatbot.service;

import com.agentic.chatbot.model.CodeSample;
import com.agentic.chatbot.model.ProgrammingLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeSampleLibraryTest {

    private final CodeSampleLibrary library = new CodeSampleLibrary();

    @Test
    void everySampleExistsInEveryLanguage() {
        assertEquals(15, library.languages().size());
        assertEquals(4, library.samples().size());
        for (CodeSample sample : library.samples()) {
            for (ProgrammingLanguage language : library.languages()) {
                String code = sample.code().get(language.id());
                assertTrue(code != null && !code.isBlank(), sample.id() + " is missing " + language.id());
            }
        }
    }

    @Test
    void resolvesLanguagesByNameExtensionAndAlias() {
        assertEquals("csharp", library.language("C#").orElseThrow().id());
        assertEquals("plsql", library.language("pl/sql").orElseThrow().id());
        assertEquals("cobol", library.language("Mainframe").orElseThrow().id());
        assertEquals("go", library.language("golang").orElseThrow().id());
        assertEquals("python", library.language("py").orElseThrow().id());
        assertEquals("cpp", library.language("C++").orElseThrow().id());
        assertFalse(library.language("brainfuck").isPresent());
    }

    @Test
    void matchesSamplesIgnoringWhitespace() {
        String java = library.samples().get(0).code().get("java");
        String reformatted = java.replace("    ", "\t").replace("\n", "\r\n\n");

        CodeSampleLibrary.SampleMatch match = library.match(reformatted, null).orElseThrow();

        assertEquals("hello-world", match.sample().id());
        assertEquals("java", match.languageId());
        assertFalse(library.match(java.replace("Hello", "Bye"), "java").isPresent());
    }

    @Test
    void detectsTheLanguageOfEverySample() {
        for (CodeSample sample : library.samples()) {
            sample.code().forEach((languageId, code) ->
                    assertEquals(languageId, CodeConversionService.detectLanguage(code),
                            "wrong language detected for " + sample.id() + "/" + languageId));
        }
    }
}
