package com.agentic.chatbot.stage;

import java.util.List;
import java.util.Map;

/**
 * Domain-specific system prompts for each stage, plus few-shot blocks built from approved training examples.
 * Prompts are kept short because the local model may have a small context window.
 */
public final class StagePrompts {

    private static final int EXAMPLE_INPUT_CHARS = 300;
    private static final int EXAMPLE_OUTPUT_CHARS = 1200;

    private StagePrompts() {
    }

    /** Java with the default automation; kept for callers that only know the domain. */
    public static String system(StageType stage, StageDomain domain) {
        return system(stage, domain, StageLanguage.JAVA, "Java", StageAutomation.BOTH);
    }

    public static String system(StageType stage, StageDomain domain, StageLanguage language, String languageName,
                                StageAutomation automation) {
        return system(stage, domain, language, languageName, language.unitFramework(), automation);
    }

    /** For any language: the profile carries the name and unit-test framework of catalog and typed languages. */
    public static String system(StageType stage, StageDomain domain, LanguageProfile language, StageAutomation automation) {
        return system(stage, domain, language.kind(), language.label(), language.unitFramework(), automation);
    }

    private static String system(StageType stage, StageDomain domain, StageLanguage language, String languageName,
                                 String unitFramework, StageAutomation automation) {
        boolean bank = domain == StageDomain.BANKING;
        boolean java = language == StageLanguage.JAVA;
        String d = domain.label();
        return switch (stage) {
            case BA -> "You are a senior Business Analyst for the " + d + " domain."
                    + " You work for " + domain.organisation() + " and know " + domain.expertise() + ".\n"
                    + "Turn the requirement into 3 to 5 user stories. Format in Markdown exactly like this:\n"
                    + "# Feature: <short feature name>\n"
                    + "## Story 1: <title>\n"
                    + "As a <role>, I want <goal>, so that <benefit>.\n"
                    + "### Acceptance criteria\n"
                    + "Scenario: <name>\n  Given ...\n  When ...\n  Then ...\n"
                    + "Give every story 2 or 3 scenarios, including validation and error cases, with concrete example values. "
                    + "End with a short '## Assumptions' list. No code.";
            case DEVELOPER -> "You are a senior " + languageName + " developer in the " + d + " domain."
                    + " You know " + domain.expertise() + ".\n"
                    + (java
                    ? "Write clean, compilable Java 17 using only the JDK (no frameworks unless asked). "
                    : "Write clean, idiomatic, runnable " + languageName + " using only the standard library unless asked. ")
                    + (bank ? "Use exact decimal types for money (BigDecimal, Decimal, ...), never binary floating point. "
                    : "Model statuses as enums or constants with explicit allowed transitions. ")
                    + (java
                    ? "Every public class and public method has a Javadoc comment. "
                    : "Every public type and function has a doc comment in the language's usual style. ")
                    + "Every business rule has an inline comment starting with 'Business rule:' that says which rule it "
                    + "enforces. Validate inputs and raise clear errors.";
            case TESTER -> "You are a senior software tester (unit tests, UI automation and manual functional testing) for " + d + " software.\n"
                    + (java
                    ? "Write JUnit 5 tests (org.junit.jupiter.api) for the given Java code. "
                    : "Write " + unitFramework + " unit tests for the given " + languageName + " code. ")
                    + "Use only constructors and functions that exist in the code shown. Cover the happy path, every "
                    + "validation rule and edge case. Use descriptive test names and a short comment on what each test "
                    + "proves. No mocking libraries.\n"
                    + "For UI automation (" + automation.label() + ") use the page-object pattern: each Given/When/Then "
                    + "scenario becomes one test, use id locators with clear ids, explicit waits, and read the base URL "
                    + "from configuration. Add a short comment above each test naming its scenario.\n"
                    + "Manual (functional) test cases are for a person to run by hand: ID, title, preconditions, numbered steps and "
                    + "expected results taken from the Given/When/Then scenarios.";
            case DEPLOY -> "You are a senior DevOps engineer for " + d + " systems. You write minimal, secure, "
                    + "production-ready Dockerfiles, build scripts and GitHub Actions pipelines for " + languageName
                    + " projects, with short comments explaining each step.";
            case TRAINER -> "You review the outputs of the other stage bots.";
        };
    }

    /** User prompt for the Developer in any language other than Java. */
    public static String developerAny(String stories, String languageName, String fence, String srcDir, String feature) {
        String tag = fence == null || fence.isBlank() ? "" : fence;
        return "User stories:\n" + clip(stories, 2500) + "\n\n"
                + "Write the complete " + languageName + " implementation of these stories (feature " + feature + "): "
                + "domain model, errors and a service with the business rules. Use 1 to 4 files under " + srcDir + "/. "
                + "Precede each file with a line 'FILE: <path>' and put its code in its own ```" + tag + " block. "
                + "Include doc comments and 'Business rule:' comments.";
    }

    /** User prompt for unit tests in any language other than Java. */
    public static String unitTestAny(String code, String languageName, String framework, String fence, String testDir) {
        String tag = fence == null || fence.isBlank() ? "" : fence;
        return "Code under test:\n```" + tag + "\n" + clip(code, 4000) + "\n```\n"
                + "Write " + framework + " unit tests in " + languageName + " for this code, in " + testDir + "/. "
                + "Precede each file with a line 'FILE: <path>' and put it in its own ```" + tag + " block.";
    }

    /** User prompt for UI automation in flavours other than the built-in Java and Ruby Selenium prompts. */
    public static String automationAny(String stories, StageAutomation automation, String feature, String baseUrl) {
        return "User stories:\n" + clip(stories, 2200) + "\n\n"
                + "Write UI automation tests using " + automation.label() + " for feature " + feature
                + ": a page object plus one test per scenario, base URL from the APP_BASE_URL environment variable "
                + "(default " + baseUrl + "). Precede each file with a line 'FILE: <path>' (paths relative to the test "
                + "project) and put it in its own ```" + automation.fence() + " block. Include the dependency file "
                + "(requirements.txt, package.json or .csproj).";
    }

    /** User prompt for deployment files when BOT has no built-in template for the language. */
    public static String deployAny(String code, String languageName, int port) {
        return "Project code (" + languageName + "):\n" + clip(code, 2500) + "\n\n"
                + "Write a multi-stage Dockerfile for this " + languageName + " project that builds it, runs its unit "
                + "tests and starts it on port " + port + ". Reply with one ```dockerfile block and nothing else.";
    }

    /** BA prompt for stories the user uploaded or pulled from GitHub: organise them, never drop or invent any. */
    public static String baRefine(String stories) {
        return "These are existing user stories (possibly from several files):\n" + clip(stories, 9000) + "\n\n"
                + "Organise them into the Markdown format from your instructions, grouped by feature. Keep EVERY story and its "
                + "acceptance criteria; do not invent new stories. Add Given/When/Then scenarios only where they are missing. "
                + "Write the user stories now.";
    }

    /** Manual (functional) test cases a person runs by hand. */
    public static String manualTests(String stories, String code, String baseUrl) {
        return (stories.isBlank() ? "" : "User stories:\n" + clip(stories, 2200) + "\n\n")
                + (code.isBlank() ? "" : "Code (for context):\n" + clip(code, 1500) + "\n\n")
                + "Write MANUAL functional test cases that a person can run by hand against the running application at "
                + baseUrl + ". One case per Given/When/Then scenario, plus validation and error cases. Markdown, exactly:\n"
                + "## TC-001: <title>\n**Story:** <story>\n**Preconditions**\n- ...\n**Steps**\n1. ...\n2. ...\n"
                + "**Expected result**\n- ...\nUse concrete example values. No code.";
    }

    public static String baUser(String requirement) {
        return "Requirement:\n" + clip(requirement, 1500) + "\n\nWrite the user stories now.";
    }

    public static String developerPlan(String stories, String pkg) {
        return "User stories:\n" + clip(stories, 2500) + "\n\n"
                + "List the Java classes (at most 4) needed to implement these stories in package " + pkg + ". "
                + "Include domain model, exception and service classes. One per line, format: ClassName - purpose. No code.";
    }

    public static String developerClass(String stories, String pkg, List<String> plan, String type,
                                        String writtenSoFar) {
        return "User stories:\n" + clip(stories, 2000) + "\n\n"
                + "Package: " + pkg + "\nClasses in this module: " + String.join(", ", plan) + "\n"
                + (writtenSoFar.isBlank() ? "" : "Already written (public signatures):\n" + clip(writtenSoFar, 1500) + "\n")
                + "Write ONLY the complete file " + type + ".java (package " + pkg + "). "
                + "Include Javadoc and 'Business rule:' comments. Reply with one ```java code block.";
    }

    public static String repair(String type, String code, List<String> errors) {
        return "This Java file " + type + ".java does not compile. Errors:\n"
                + String.join("\n", errors.stream().limit(6).toList())
                + "\n\nCode:\n```java\n" + clip(code, 3500) + "\n```\nReturn the full corrected file in one ```java block.";
    }

    public static String unitTest(String classCode, String otherSignatures, String pkg, String testName) {
        return unitTest(classCode, otherSignatures, "", pkg, testName);
    }

    /** Unit-test prompt; {@code stories} (may be blank) describe the expected behaviour when only signatures are sent. */
    public static String unitTest(String classCode, String otherSignatures, String stories, String pkg, String testName) {
        return "Class under test:\n```java\n" + clip(classCode, 3500) + "\n```\n"
                + (stories.isBlank() ? "" : "Expected behaviour (user stories):\n" + clip(stories, 1200) + "\n")
                + (otherSignatures.isBlank() ? "" : "Other classes (signatures only):\n" + clip(otherSignatures, 1200) + "\n")
                + "Package: " + pkg + "\n"
                + "Write the JUnit 5 test class " + testName + " in that package. Reply with one ```java block.";
    }

    public static String seleniumJava(String stories, String pkg, String feature, String baseUrl) {
        return "User stories:\n" + clip(stories, 2200) + "\n\n"
                + "Write Selenium WebDriver 4 + JUnit 5 tests in Java 17, package " + pkg + ". Produce exactly two files, "
                + "each preceded by a line 'FILE: <name>':\n"
                + "FILE: " + feature + "Page.java (page object with By.id locators and action methods)\n"
                + "FILE: " + feature + "SeleniumTest.java (one @Test per scenario, ChromeDriver, base URL from "
                + "System.getProperty(\"app.baseUrl\", \"" + baseUrl + "\"))\n"
                + "Put each file in its own ```java block.";
    }

    public static String seleniumRuby(String stories, String feature, String baseUrl) {
        String snake = snake(feature);
        return "User stories:\n" + clip(stories, 2200) + "\n\n"
                + "Write Ruby Selenium tests with the selenium-webdriver gem and RSpec. Produce exactly two files, "
                + "each preceded by a line 'FILE: <path>':\n"
                + "FILE: spec/pages/" + snake + "_page.rb (page object class " + feature + "Page with id locators)\n"
                + "FILE: spec/" + snake + "_spec.rb (require_relative 'spec_helper' and 'pages/" + snake + "_page'; "
                + "one 'it' per scenario; the driver is @driver and the base URL is BASE_URL, default " + baseUrl + ")\n"
                + "Put each file in its own ```ruby block.";
    }

    /** Few-shot block from approved examples; empty when the stage has not been trained yet. */
    public static String fewShot(List<Map<String, String>> examples) {
        if (examples == null || examples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\nApproved examples from reviewers. Follow their structure, naming and level of detail:\n");
        int n = 0;
        for (Map<String, String> ex : examples) {
            n++;
            sb.append("\n### Example ").append(n).append(" input\n").append(clip(ex.getOrDefault("input", ""), EXAMPLE_INPUT_CHARS))
                    .append("\n### Example ").append(n).append(" approved output\n").append(clip(ex.getOrDefault("output", ""), EXAMPLE_OUTPUT_CHARS))
                    .append('\n');
            String feedback = ex.getOrDefault("feedback", "");
            if (!feedback.isBlank()) {
                sb.append("### Reviewer feedback\n").append(clip(feedback, 300)).append('\n');
            }
        }
        return sb.toString();
    }

    public static String clip(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= max ? t : t.substring(0, max) + "\n…(truncated)";
    }

    public static String snake(String pascal) {
        return pascal.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }
}
