package com.agentic.chatbot.service;

import com.agentic.chatbot.model.CodeSample;
import com.agentic.chatbot.model.LanguageCatalog;
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
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Hand-written reference programs (Hello World, Factorial, Binary Search, FizzBuzz) in every
 * supported language. They power the samples gallery and let the converter translate these
 * programs offline, without an LLM.
 */
@Service
public class CodeSampleLibrary {

    /** Languages with sample files; the rest of {@link LanguageCatalog} is converted by the AI model only. */
    private static final List<ProgrammingLanguage> LANGUAGES = LanguageCatalog.sampleLanguages();

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

    /** The languages that have sample programs (offline conversion). */
    public List<ProgrammingLanguage> languages() {
        return LANGUAGES;
    }

    /** Languages converted by the AI model only. */
    public List<ProgrammingLanguage> moreLanguages() {
        return LanguageCatalog.moreLanguages();
    }

    /** Sample languages first, then the AI-only ones. */
    public List<ProgrammingLanguage> allLanguages() {
        return LanguageCatalog.allLanguages();
    }

    public List<CodeSample> samples() {
        return samples;
    }

    /** Resolves any catalog language by id, display name, alias or file extension (case-insensitive). */
    public Optional<ProgrammingLanguage> language(String value) {
        return LanguageCatalog.find(value).map(LanguageCatalog.Entry::language);
    }

    /** Language of a source file from its extension; {@code content} settles ambiguous ones such as {@code .m}. */
    public Optional<ProgrammingLanguage> languageOfFile(String fileName, Supplier<String> content) {
        return LanguageCatalog.forFile(fileName, content).map(LanguageCatalog.Entry::language);
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
