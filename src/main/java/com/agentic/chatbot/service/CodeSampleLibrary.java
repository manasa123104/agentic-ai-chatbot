package com.agentic.chatbot.service;

import com.agentic.chatbot.model.CodeSample;
import com.agentic.chatbot.model.ProgrammingLanguage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-written reference programs (Hello World, Factorial, Binary Search, FizzBuzz) in every
 * supported language. They power the samples gallery and let the converter translate these
 * programs offline, without an LLM.
 */
@Service
public class CodeSampleLibrary {

    private static final List<ProgrammingLanguage> LANGUAGES = List.of(
            new ProgrammingLanguage("java", "Java", "java",
                    "java Program.java", List.of("jdk")),
            new ProgrammingLanguage("python", "Python", "py",
                    "python program.py", List.of("py", "python3", "python 3")),
            new ProgrammingLanguage("javascript", "JavaScript", "js",
                    "node program.js", List.of("js", "node", "node.js", "nodejs")),
            new ProgrammingLanguage("typescript", "TypeScript", "ts",
                    "npx tsx program.ts", List.of("ts")),
            new ProgrammingLanguage("csharp", "C#", "cs",
                    "dotnet run (inside a console project)", List.of("c#", "cs", "c sharp", ".net", "dotnet")),
            new ProgrammingLanguage("cpp", "C++", "cpp",
                    "g++ -std=c++17 program.cpp -o program && ./program", List.of("c++", "cplusplus")),
            new ProgrammingLanguage("c", "C", "c",
                    "gcc program.c -o program && ./program", List.of("ansi c", "c99")),
            new ProgrammingLanguage("go", "Go", "go",
                    "go run program.go", List.of("golang")),
            new ProgrammingLanguage("rust", "Rust", "rs",
                    "rustc program.rs && ./program", List.of("rs")),
            new ProgrammingLanguage("kotlin", "Kotlin", "kt",
                    "kotlinc program.kt -include-runtime -d program.jar && java -jar program.jar", List.of("kt")),
            new ProgrammingLanguage("swift", "Swift", "swift",
                    "swift program.swift", List.of()),
            new ProgrammingLanguage("php", "PHP", "php",
                    "php program.php", List.of()),
            new ProgrammingLanguage("ruby", "Ruby", "rb",
                    "ruby program.rb", List.of("rb")),
            new ProgrammingLanguage("plsql", "PL/SQL (Oracle SQL)", "sql",
                    "Run in SQL*Plus or SQL Developer: @program.sql",
                    List.of("pl/sql", "sql", "oracle", "oracle pl/sql", "oracle sql")),
            new ProgrammingLanguage("cobol", "COBOL (Mainframe)", "cbl",
                    "cobc -x program.cbl && ./program (GnuCOBOL) or compile with IBM Enterprise COBOL on z/OS",
                    List.of("cbl", "mainframe", "ibm mainframe", "ibm z/os", "z/os")));

    private static final List<String[]> SAMPLE_INFO = List.of(
            new String[] {"hello-world", "Hello World",
                    "Prints the text \"Hello, World!\" to standard output. Shows the minimum program structure: entry point and console output."},
            new String[] {"factorial", "Factorial (recursion)",
                    "Computes 10! = 3628800. A function returns 1 when n <= 1 (base case), otherwise n * factorial(n - 1). COBOL uses a loop because paragraphs are not recursive by default."},
            new String[] {"binary-search", "Binary search (arrays + loops)",
                    "Searches the sorted array [2, 5, 8, 12, 16, 23, 38, 56, 72, 91] for 23 by repeatedly halving the low/high range, then prints \"Found 23 at index 5\" (0-based) or \"23 not found\"."},
            new String[] {"fizzbuzz", "FizzBuzz (conditionals)",
                    "Loops 1..15 and prints FizzBuzz for multiples of 15, Fizz for multiples of 3, Buzz for multiples of 5, otherwise the number."});

    private final List<CodeSample> samples;

    public CodeSampleLibrary() {
        List<CodeSample> loaded = new ArrayList<>();
        for (String[] info : SAMPLE_INFO) {
            Map<String, String> code = new LinkedHashMap<>();
            for (ProgrammingLanguage language : LANGUAGES) {
                String text = readResource("code-samples/" + info[0] + "/" + language.id() + "." + language.extension());
                if (text != null) {
                    code.put(language.id(), text);
                }
            }
            loaded.add(new CodeSample(info[0], info[1], info[2], Collections.unmodifiableMap(code)));
        }
        this.samples = List.copyOf(loaded);
    }

    public List<ProgrammingLanguage> languages() {
        return LANGUAGES;
    }

    public List<CodeSample> samples() {
        return samples;
    }

    /** Resolves a language by id, display name, file extension, or alias (case-insensitive). */
    public Optional<ProgrammingLanguage> language(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String key = value.trim().toLowerCase(Locale.ROOT);
        for (ProgrammingLanguage language : LANGUAGES) {
            if (language.id().equals(key)
                    || language.name().toLowerCase(Locale.ROOT).equals(key)
                    || language.extension().equals(key)
                    || language.aliases().contains(key)) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds the built-in sample whose code equals {@code code}, ignoring whitespace. The
     * preferred language is checked first because some one-liners look alike across languages.
     */
    public Optional<SampleMatch> match(String code, String preferredLanguageId) {
        String fingerprint = fingerprint(code);
        if (fingerprint.isEmpty()) {
            return Optional.empty();
        }
        if (preferredLanguageId != null) {
            for (CodeSample sample : samples) {
                String candidate = sample.code().get(preferredLanguageId);
                if (candidate != null && fingerprint(candidate).equals(fingerprint)) {
                    return Optional.of(new SampleMatch(sample, preferredLanguageId));
                }
            }
        }
        for (CodeSample sample : samples) {
            for (Map.Entry<String, String> entry : sample.code().entrySet()) {
                if (fingerprint(entry.getValue()).equals(fingerprint)) {
                    return Optional.of(new SampleMatch(sample, entry.getKey()));
                }
            }
        }
        return Optional.empty();
    }

    static String fingerprint(String code) {
        return code == null ? "" : code.replaceAll("\\s+", "");
    }

    private static String readResource(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            return null;
        }
    }

    public record SampleMatch(CodeSample sample, String languageId) {
    }
}
