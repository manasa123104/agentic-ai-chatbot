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
    void convertsJavaToCatalogLanguagesWithTheirOwnExtension() throws Exception {
        AtomicReference<String> prompt = new AtomicReference<>();
        LlmClient llm = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                prompt.set(userPrompt);
                return "=== NOTES ===\nSame logic.\n=== CODE ===\n```scala\ndef add(a: Int, b: Int): Int = a + b\n```";
            }
        };

        ConversionResponse response = service(llm).convert(
                new ConversionRequest("java", "scala", "int add(int a, int b) { return a + b; }", null));

        assertTrue(response.success(), response.message());
        assertEquals("scala", response.targetLanguageId());
        assertEquals("conversions/converted_output.scala", response.outputFile());
        assertEquals("def add(a: Int, b: Int): Int = a + b", Files.readString(outputDir.resolve(response.outputFile())).strip());
        assertTrue(prompt.get().contains("Target language: Scala"), prompt.get());

        ConversionResponse elixir = service(llm).convert(
                new ConversionRequest("java", "Elixir", "int add(int a, int b) { return a + b; }", null));
        assertEquals("conversions/converted_output.ex", elixir.outputFile());
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

    @Test
    void addCommentsAndExplainLogicUseTheModel() {
        java.util.List<String> systemPrompts = new java.util.ArrayList<>();
        LlmClient llm = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                systemPrompts.add(systemPrompt);
                return systemPrompt.contains("business people")
                        ? "Summary: Prints FizzBuzz.\nBusiness rules:\n1. Multiples of 3 print Fizz."
                        : "=== NOTES ===\nok\n=== CODE ===\n# Prints FizzBuzz\nprint('FizzBuzz')";
            }
        };
        ConversionResponse response = service(llm).convert(new ConversionRequest("java", "python",
                sample("fizzbuzz", "java"), null, true, true));

        assertTrue(response.success(), response.message());
        assertEquals("llm", response.mode(), "a sample is sent to the model when comments are requested");
        assertTrue(systemPrompts.get(0).contains("Add clear comments"));
        assertTrue(response.businessLogic().contains("Multiples of 3 print Fizz"));
    }

    @Test
    void retriesWhenTheModelSkipsFunctions() {
        java.util.List<String> prompts = new java.util.ArrayList<>();
        LlmClient lazyThenComplete = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                prompts.add(userPrompt);
                return prompts.size() == 1
                        ? "=== NOTES ===\nok\n=== CODE ===\ndef tax_low(v):\n    return v * 0.1\n# ... other rules"
                        : "=== NOTES ===\nok\n=== CODE ===\ndef tax_low(v):\n    return v * 0.1\n\ndef tax_high(v):\n    return v * 0.3";
            }
        };
        String java = "public class Tax {\n    static double taxLow(double v) { return v * 0.1; }\n"
                + "    static double taxHigh(double v) { return v * 0.3; }\n}\n";
        ConversionResponse response = service(lazyThenComplete).convert(new ConversionRequest("java", "python", java, null));

        assertTrue(response.success(), response.message());
        assertEquals(2, prompts.size());
        assertTrue(prompts.get(1).contains("skipped taxHigh"), prompts.get(1));
        assertTrue(response.convertedCode().contains("def tax_high"));
        assertFalse(response.explanation().contains("WARNING"));
    }

    @Test
    void keepsAPartTheModelFailedOnAsCommentsAndConvertsTheRest() {
        LlmClient failsOnBeta = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                String part = userPrompt.substring(userPrompt.lastIndexOf("=== SOURCE CODE ==="));
                if (part.contains("beta(")) {
                    throw new IllegalStateException("timed out");
                }
                StringBuilder js = new StringBuilder();
                var m = java.util.regex.Pattern.compile("static int (\\w+)\\(").matcher(part);
                while (m.find()) {
                    js.append("    static ").append(m.group(1)).append("() {\n        return 1;\n    }\n\n");
                }
                String code = part.contains("class A") ? "export class A {\n" + js + "}\n" : js.toString();
                return "=== NOTES ===\nok\n=== CODE ===\n" + code;
            }
        };
        StringBuilder java = new StringBuilder("public class A {\n");
        for (String name : new String[] {"alpha", "beta", "gamma"}) {
            java.append("    static int ").append(name).append("() {\n");
            for (int k = 0; k < 40; k++) {
                java.append("        int v").append(k).append(" = ").append(k).append(" * 1000;\n");
            }
            java.append("        return 1;\n    }\n\n");
        }
        java.append("}\n");
        ConversionResponse response = new CodeConversionService(library, failsOnBeta, outputDir.toString(), 1_000)
                .convert(new ConversionRequest("java", "javascript", java.toString(), null));

        assertTrue(response.success(), response.message());
        String out = response.convertedCode();
        assertTrue(out.contains("static alpha()") && out.contains("static gamma()"), out);
        assertTrue(out.contains("// TODO: part 2 was not converted (timed out)"), out);
        assertTrue(out.contains("//     static int beta() {"), out);
        assertTrue(response.explanation().contains("WARNING: part 2"), response.explanation());
    }

    @Test
    void dropsFunctionsALaterPartRepeatedFromTheReference() {
        String converted = "class T {\n    static rule0(v) {\n        return v * 2;\n    }\n\n    static rule1(v) {\n"
                + "        return v;\n    }\n}\n";
        String source = "    public static double rule2(double v) {\n        return v + 1;\n    }\n";
        String part = "    static rule0(v) {\n        return v * 3;\n    }\n\n    static rule1(v) {\n        return v;\n    }\n\n"
                + "    static rule2(v) {\n        return v + 1;\n    }\n";
        String kept = CodeConversionService.withoutEchoedFunctions(part, source, converted, "javascript", true);
        assertFalse(kept.contains("rule0"));
        assertFalse(kept.contains("rule1"));
        assertTrue(kept.contains("static rule2(v)"));
    }

    @Test
    void dropsARepeatedFunctionInsideARepeatedClassWrapper() {
        String converted = "export class T {\n    static rule0(v) {\n        return v;\n    }\n}\n";
        String source = "    static double rule1(double v) {\n        return v;\n    }\n";
        String part = "export class T {\n    static rule1(v) {\n        return v;\n    }\n\n    static rule0(v) {\n"
                + "        return 0;\n    }\n}\n";
        String kept = CodeConversionService.withoutEchoedFunctions(part, source, converted, "javascript", true);
        assertTrue(kept.startsWith("export class T {\n    static rule1(v)"));
        assertFalse(kept.contains("rule0"));
        assertTrue(kept.strip().endsWith("}"));
    }

    @Test
    void dropsARepeatedConstructorAndMainButKeepsOnesFromTheSource() {
        String converted = "export class L {\n    constructor() {\n        this.entries = [];\n    }\n}\n\n"
                + "function main() {\n    console.log(1);\n}\n";
        String part = "    constructor() {\n        this.entries = [];\n    }\n\n    fee(a) {\n        return a;\n    }\n\n"
                + "function main() {\n    console.log(2);\n}\n";
        String source = "    public double fee(double a) {\n        return a;\n    }\n";
        String kept = CodeConversionService.withoutEchoedFunctions(part, source, converted, "javascript", true);
        assertFalse(kept.contains("constructor"), kept);
        assertFalse(kept.contains("main"), kept);
        assertTrue(kept.contains("fee(a)"));

        String sourceWithBoth = "    public L(int x) {\n    }\n\n    public static void main(String[] args) {\n    }\n";
        String both = CodeConversionService.withoutEchoedFunctions(part, sourceWithBoth, converted, "javascript", true);
        assertTrue(both.contains("constructor()") && both.contains("function main()"), both);
    }

    @Test
    void takesTheCodeOfACutOffFencedAnswer() {
        LlmClient rambling = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return "=== NOTES ===\nThe rule computes a value and so on, and so on\n\n```javascript\n"
                        + "function twice(v) {\n    return v * 2;\n}\n";
            }
        };
        ConversionResponse response = service(rambling).convert(new ConversionRequest("java", "javascript",
                "class A {\n    static int twice(int v) {\n        return v * 2;\n    }\n}\n", null));
        assertTrue(response.success(), response.message());
        assertTrue(response.convertedCode().startsWith("function twice(v)"), response.convertedCode());
        assertFalse(response.convertedCode().contains("NOTES"));
    }

    @Test
    void rewritesJavaTypesTheModelLeftInJavaScript() {
        String leftover = "class T {\n    static double rule12(double income) {\n        double rate = 0.1;\n"
                + "        for (int k = 1; k <= 3; k++) {\n            rate += (double) k;\n        }\n"
                + "        for (double e : this.entries) {\n            if (e > 0) {\n            }\n        }\n"
                + "        return income * rate;\n    }\n\n    countAbove1(int limit, String name) {\n"
                + "        System.out.println(name.length());\n        return limit;\n    }\n}\n";
        String js = CodeConversionService.withoutJavaTypes("java", "javascript", leftover);
        assertEquals("class T {\n    static rule12(income) {\n        let rate = 0.1;\n"
                + "        for (let k = 1; k <= 3; k++) {\n            rate += k;\n        }\n"
                + "        for (const e of this.entries) {\n            if (e > 0) {\n            }\n        }\n"
                + "        return income * rate;\n    }\n\n    countAbove1(limit, name) {\n"
                + "        console.log(name.length);\n        return limit;\n    }\n}\n", js);
        assertEquals(leftover, CodeConversionService.withoutJavaTypes("java", "python", leftover));
    }

    @Test
    void restoresStaticOnMethodsCalledThroughTheClass() {
        String source = "class T {\n    public static double rule0(double v) {\n        return v;\n    }\n"
                + "    double other(double v) {\n        return v;\n    }\n}\n";
        String converted = "export class T {\n    rule0(v) {\n        return v;\n    }\n\n    other(v) {\n        return v;\n"
                + "    }\n}\nconsole.log(T.rule0(1));\n";
        String fixed = CodeConversionService.restoreStatic(source, converted, "javascript");
        assertTrue(fixed.contains("    static rule0(v) {"));
        assertTrue(fixed.contains("    other(v) {"));
        assertEquals(converted, CodeConversionService.restoreStatic(source, converted, "python"));

        String withMain = "class T {\n    public static void main(String[] args) {\n    }\n}\n";
        String instanceMain = "export class T {\n    main() {\n        console.log(1);\n    }\n}\n";
        String entry = CodeChunker.ensureMainCall("javascript",
                CodeConversionService.restoreStatic(withMain, instanceMain, "javascript"));
        assertTrue(entry.contains("    static main() {") && entry.endsWith("\nT.main();\n"), entry);

        String instanceCall = CodeConversionService.restoreStatic(withMain,
                "export class T {\n    main() {\n    }\n}\n\nnew T().main();\n", "javascript");
        assertTrue(instanceCall.endsWith("\nT.main();\n"), instanceCall);
        assertEquals(instanceCall, CodeChunker.ensureMainCall("javascript", instanceCall));
    }

    @Test
    void reportsAMissingEntryPoint() {
        String source = "class A {\n    public static void main(String[] args) {\n        System.out.println(1);\n    }\n}\n";
        assertEquals(java.util.List.of("main"), CodeConversionService.missingFunctions(source, "class A {}\n"));
        assertTrue(CodeConversionService.missingFunctions(source, "if __name__ == \"__main__\":\n    print(1)\n").isEmpty());
        assertTrue(CodeConversionService.missingFunctions(source, "class A {\n    static main() {}\n}\n").isEmpty());
    }

    @Test
    void detectsFunctionsTheModelSkipped() {
        String source = "public class T {\n    public static double rule1(double v) {\n        return v;\n    }\n"
                + "    private int finalPrice(int a) throws Exception {\n        if (a > 0) {\n            return a;\n        }\n"
                + "        return 0;\n    }\n}\n";
        assertEquals(java.util.Set.of("rule1", "finalPrice"), CodeConversionService.declaredFunctions(source));
        assertEquals(java.util.List.of("finalPrice"),
                CodeConversionService.missingFunctions(source, "def rule1(v):\n    return v\n# ... other methods"));
        assertTrue(CodeConversionService.missingFunctions(source,
                "def rule1(v):\n    return v\n\ndef final_price(a):\n    return a\n").isEmpty());
    }

    @Test
    void explainLogicOfflineFallsBackToTheSampleDescription() {
        ConversionResponse response = service(OFFLINE).convert(new ConversionRequest("java", "go",
                sample("factorial", "java"), null, true, true));

        assertTrue(response.success(), response.message());
        assertEquals("sample-library", response.mode());
        assertTrue(response.businessLogic().startsWith("Summary: Computes 10!"), response.businessLogic());
    }
}
