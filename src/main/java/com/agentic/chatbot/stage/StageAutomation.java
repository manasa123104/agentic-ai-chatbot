package com.agentic.chatbot.stage;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * UI-automation flavour written by the Tester stage. Each writes into its own folder of the run.
 */
public enum StageAutomation {
    BOTH("both", "Selenium Java + Ruby", "", "", ""),
    SELENIUM_JAVA("java", "Selenium Java (JUnit 5)", "selenium-java/", "Java", "java"),
    SELENIUM_RUBY("ruby", "Selenium Ruby (RSpec)", "selenium-ruby/", "Ruby", "ruby"),
    SELENIUM_PYTHON("python", "Selenium Python (pytest)", "selenium-python/", "Python", "python"),
    SELENIUM_JS("javascript", "Selenium JavaScript (Mocha)", "selenium-js/", "JavaScript", "javascript"),
    SELENIUM_CSHARP("csharp", "Selenium C# (NUnit)", "selenium-csharp/", "C#", "csharp"),
    PLAYWRIGHT_JS("playwright-js", "Playwright JavaScript", "playwright-js/", "JavaScript", "javascript"),
    PLAYWRIGHT_TS("playwright-ts", "Playwright TypeScript", "playwright-ts/", "TypeScript", "typescript"),
    NONE("none", "No automation tests", "", "", "");

    /** Folders automation tests are written to; anything else from the Tester is a unit test. */
    public static final List<String> ROOTS = Arrays.stream(values()).map(a -> a.root).filter(r -> !r.isEmpty()).toList();

    private final String id;
    private final String label;
    private final String root;
    private final String language;
    private final String fence;

    StageAutomation(String id, String label, String root, String language, String fence) {
        this.id = id;
        this.label = label;
        this.root = root;
        this.language = language;
        this.fence = fence;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String root() {
        return root;
    }

    public String language() {
        return language;
    }

    public String fence() {
        return fence;
    }

    public static Optional<StageAutomation> fromId(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        String alias = switch (v) {
            case "selenium-java" -> "java";
            case "selenium-ruby" -> "ruby";
            case "selenium-python", "py" -> "python";
            case "selenium-js", "js" -> "javascript";
            case "selenium-csharp", "c#" -> "csharp";
            case "playwright", "playwright-javascript" -> "playwright-js";
            case "playwright-typescript" -> "playwright-ts";
            default -> v;
        };
        return Arrays.stream(values()).filter(a -> a.id.equals(alias)).findFirst();
    }

    /** The user's choice, else the code language's default (same language where Selenium supports it). */
    public static StageAutomation choose(String requested, StageLanguage language) {
        return fromId(requested).orElseGet(() -> fromId(language.defaultAutomation()).orElse(BOTH));
    }

    public static boolean isAutomationPath(String path) {
        return ROOTS.stream().anyMatch(path::startsWith);
    }
}
