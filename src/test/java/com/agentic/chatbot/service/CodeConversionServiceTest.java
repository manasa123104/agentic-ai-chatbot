package com.agentic.chatbot.service;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.ConversionRequest;
import com.agentic.chatbot.model.ConversionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeConversionServiceTest {

    private static final LlmClient OFFLINE = new LlmClient() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new AssertionError("Offline conversion must not call the LLM");
        }
    };

    @TempDir
    Path outputDir;

    private final CodeSampleLibrary library = new CodeSampleLibrary();

    private CodeConversionService service(LlmClient llm) {
        return new CodeConversionService(library, llm, outputDir.toString());
    }

    private String sample(String sampleId, String languageId) {
        return library.samples().stream()
                .filter(s -> s.id().equals(sampleId))
                .findFirst().orElseThrow()
                .code().get(languageId);
    }

    @Test
    void convertsBuiltInSamplesOfflineAndSavesTheOutput() throws Exception {
        ConversionResponse response = service(OFFLINE).convert(
                new ConversionRequest("java", "python", sample("binary-search", "java"), null));

        assertTrue(response.success(), response.message());
        assertEquals("sample-library", response.mode());
        assertEquals(sample("binary-search", "python"), response.convertedCode());
        assertEquals("conversions/converted_output.py", response.outputFile());
        assertEquals(response.convertedCode(), Files.readString(outputDir.resolve(response.outputFile())));
    }

    @Test
    void autoDetectsTheSourceLanguage() {
        ConversionResponse response = service(OFFLINE).convert(
                new ConversionRequest("auto", "Go", sample("fizzbuzz", "rust"), null));

        assertTrue(response.success(), response.message());
        assertEquals("rust", response.sourceLanguageId());
        assertEquals(sample("fizzbuzz", "go"), response.convertedCode());
    }

    @Test
    void correctsAWronglySelectedSourceLanguageForSamples() {
        ConversionResponse response = service(OFFLINE).convert(
                new ConversionRequest("java", "ruby", sample("factorial", "python"), null));

        assertTrue(response.success(), response.message());
        assertEquals("python", response.sourceLanguageId());
        assertEquals(sample("factorial", "ruby"), response.convertedCode());
    }

    @Test
    void keepsCobolColumnIndentation() {
        ConversionResponse response = service(OFFLINE).convert(
                new ConversionRequest("java", "mainframe", sample("hello-world", "java"), null));

        assertTrue(response.success(), response.message());
        assertEquals("cobol", response.targetLanguageId());
        assertTrue(response.convertedCode().startsWith("       IDENTIFICATION DIVISION."));
    }

    @Test
    void returnsCodeUnchangedWhenSourceAndTargetMatch() {
        ConversionResponse response = service(OFFLINE).convert(
                new ConversionRequest("python", "python", "print('hi')", null));

        assertTrue(response.success());
        assertEquals("passthrough", response.mode());
        assertEquals("print('hi')", response.convertedCode());
    }

    @Test
    void explainsHowToEnableTheLlmForCustomCodeWhenOffline() {
        ConversionResponse response = service(OFFLINE).convert(
                new ConversionRequest("java", "python", "class Adder { int add(int a, int b) { return a + b; } }", null));

        assertFalse(response.success());
        assertTrue(response.message().contains("OPENAI_API_KEY"));
    }

    @Test
    void rejectsEmptyCodeAndMissingTarget() {
        assertFalse(service(OFFLINE).convert(new ConversionRequest("java", "python", "   ", null)).success());
        assertFalse(service(OFFLINE).convert(new ConversionRequest("java", "", "print(1)", null)).success());
    }

    @Test
    void usesTheLlmForCustomCodeAndParsesItsReply() {
        AtomicReference<String> prompt = new AtomicReference<>();
        LlmClient llm = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                prompt.set(userPrompt);
                return "=== NOTES ===\nAdds two numbers.\n=== CODE ===\n```python\ndef add(a, b):\n    return a + b\n```\n";
            }
        };

        ConversionResponse response = service(llm).convert(new ConversionRequest(
                "java", "python", "int add(int a, int b) { return a + b; }", "use type hints"));

        assertTrue(response.success(), response.message());
        assertEquals("llm", response.mode());
        assertEquals("def add(a, b):\n    return a + b", response.convertedCode());
        assertEquals("Adds two numbers.", response.explanation());
        assertTrue(prompt.get().contains("Target language: Python"));
        assertTrue(prompt.get().contains("use type hints"));
        assertTrue(prompt.get().contains("return a + b;"));
    }

    @Test
    void extractsFencedCodeWhenTheModelIgnoresTheSectionMarkers() {
        LlmClient llm = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return "Here is the Go version:\n\n```go\npackage main\n\nfunc add(a, b int) int { return a + b }\n```\n\nIt keeps the same signature.";
            }
        };

        ConversionResponse response = service(llm).convert(
                new ConversionRequest("java", "go", "int add(int a, int b) { return a + b; }", null));

        assertTrue(response.success(), response.message());
        assertEquals("package main\n\nfunc add(a, b int) int { return a + b }", response.convertedCode());
        assertTrue(response.explanation().contains("same signature"));
    }

    @Test
    void acceptsTargetLanguagesOutsideTheBuiltInListWhenTheLlmIsAvailable() {
        LlmClient llm = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return "=== NOTES ===\nPrints hi.\n=== CODE ===\nobject Main extends App { println(\"hi\") }";
            }
        };

        ConversionResponse response = service(llm).convert(new ConversionRequest("auto", "Scala", "print('hi')", null));

        assertTrue(response.success(), response.message());
        assertEquals("Scala", response.targetLanguage());
        assertEquals("python", response.sourceLanguageId());
    }

    @Test
    void parsesChatInstructionWithPastedCode() {
        String code = sample("factorial", "python");
        ConversionRequest request = service(OFFLINE)
                .fromChatMessage("Convert this Python code to Java:\n" + code).orElseThrow();

        assertEquals("python", request.sourceLanguage());
        assertEquals("java", request.targetLanguage());
        assertEquals(CodeConversionService.normalizeCode(code), request.code());
        ConversionResponse response = service(OFFLINE).convert(request);
        assertTrue(response.success(), response.message());
        assertEquals(sample("factorial", "java"), response.convertedCode());
    }

    @Test
    void parsesChatInstructionWithFencedCodeAndAutoSource() {
        ConversionRequest request = service(OFFLINE)
                .fromChatMessage("please translate to C#\n```\n" + sample("fizzbuzz", "go") + "\n```").orElseThrow();

        assertEquals("auto", request.sourceLanguage());
        assertEquals("csharp", request.targetLanguage());
        assertEquals(sample("fizzbuzz", "csharp"), service(OFFLINE).convert(request).convertedCode());
    }

    @Test
    void parsesChatRequestsForNamedSamples() {
        CodeConversionService service = service(OFFLINE);

        ConversionRequest cobol = service.fromChatMessage("Show FizzBuzz in COBOL").orElseThrow();
        assertEquals("java", cobol.sourceLanguage());
        assertEquals("cobol", cobol.targetLanguage());
        assertEquals(sample("fizzbuzz", "cobol"), service.convert(cobol).convertedCode());

        ConversionRequest rust = service.fromChatMessage("Translate binary search from Kotlin to Rust.").orElseThrow();
        assertEquals("kotlin", rust.sourceLanguage());
        assertEquals("rust", rust.targetLanguage());
        assertEquals(sample("binary-search", "kotlin"), rust.code());

        ConversionRequest plsql = service.fromChatMessage("show hello world in PL/SQL").orElseThrow();
        assertEquals("plsql", plsql.targetLanguage());
    }

    @Test
    void leavesWebsitePromptsAndEditsAlone() {
        CodeConversionService service = service(OFFLINE);

        assertTrue(service.fromChatMessage("Change the website color to blue and use Poppins font").isEmpty());
        assertTrue(service.fromChatMessage("Convert the website to dark mode").isEmpty());
        assertTrue(service.fromChatMessage("Translate the about page to Spanish").isEmpty());
        assertTrue(service.fromChatMessage("Write the hero text in Ruby:\nWelcome to our gem store").isEmpty());
        assertTrue(service.fromChatMessage(
                "Generate Java code for a music academy website called Harmony Notes (Spring Boot + Thymeleaf).").isEmpty());
        assertTrue(service.fromChatMessage("").isEmpty());
    }
}
