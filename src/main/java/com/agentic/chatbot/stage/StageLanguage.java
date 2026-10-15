package com.agentic.chatbot.stage;

import java.util.Arrays;
import java.util.Locale;

/**
 * Target programming language for the Developer, Tester and Deploy stages: the converter's 15 languages plus
 * "Other" (free text, AI model only). Holds the conventions BOT needs: file extension, folders, the standard
 * unit-test framework and the default UI-automation flavour.
 */
public enum StageLanguage {
    JAVA("java", "Java", "java", "java", "JUnit 5", "both", "src/main/java", "src/test/java",
            "mvn -q compile"),
    PYTHON("python", "Python", "py", "python", "pytest", "python", "app", "tests",
            "python -m py_compile"),
    JAVASCRIPT("javascript", "JavaScript", "js", "javascript", "Jest", "playwright-js", "src", "tests",
            "node --check / npm test"),
    TYPESCRIPT("typescript", "TypeScript", "ts", "typescript", "Jest (ts-jest)", "playwright-ts", "src", "tests",
            "npx tsc --noEmit"),
    CSHARP("csharp", "C#", "cs", "csharp", "xUnit", "csharp", "src/App", "tests/App.Tests",
            "dotnet build"),
    CPP("cpp", "C++", "cpp", "cpp", "GoogleTest", "both", "src", "tests", "cmake --build"),
    C("c", "C", "c", "c", "Unity (ThrowTheSwitch)", "both", "src", "tests", "gcc -fsyntax-only"),
    GO("go", "Go", "go", "go", "Go testing package (go test)", "both", "app", "app", "go build ./..."),
    RUST("rust", "Rust", "rs", "rust", "Rust #[test] (cargo test)", "both", "src", "tests", "cargo build"),
    KOTLIN("kotlin", "Kotlin", "kt", "kotlin", "JUnit 5 (kotlin.test)", "java", "src/main/kotlin", "src/test/kotlin",
            "gradle build"),
    SWIFT("swift", "Swift", "swift", "swift", "XCTest", "both", "Sources/App", "Tests/AppTests", "swift build"),
    PHP("php", "PHP", "php", "php", "PHPUnit", "both", "src", "tests", "php -l"),
    RUBY("ruby", "Ruby", "rb", "ruby", "RSpec", "ruby", "lib", "spec", "ruby -c"),
    PLSQL("plsql", "PL/SQL", "sql", "sql", "utPLSQL", "both", "db", "test", "compile in Oracle (SQL*Plus / SQLcl)"),
    COBOL("cobol", "COBOL", "cbl", "cobol", "COBOL-Check", "both", "src", "test", "cobc -fsyntax-only"),
    OTHER("other", "Other", "txt", "", "the language's standard unit-test framework", "both", "src", "tests",
            "the language's own compiler");

    private final String id;
    private final String label;
    private final String ext;
    private final String fence;
    private final String unitFramework;
    private final String defaultAutomation;
    private final String srcDir;
    private final String testDir;
    private final String checkCommand;

    StageLanguage(String id, String label, String ext, String fence, String unitFramework, String defaultAutomation,
                  String srcDir, String testDir, String checkCommand) {
        this.id = id;
        this.label = label;
        this.ext = ext;
        this.fence = fence;
        this.unitFramework = unitFramework;
        this.defaultAutomation = defaultAutomation;
        this.srcDir = srcDir;
        this.testDir = testDir;
        this.checkCommand = checkCommand;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String ext() {
        return ext;
    }

    /** Markdown code-fence tag. */
    public String fence() {
        return fence;
    }

    public String unitFramework() {
        return unitFramework;
    }

    /** Automation flavour used when the user does not pick one (see {@link StageAutomation}). */
    public String defaultAutomation() {
        return defaultAutomation;
    }

    public String srcDir() {
        return srcDir;
    }

    public String testDir() {
        return testDir;
    }

    /** How the user can check the code builds, for the note when BOT cannot check it itself. */
    public String checkCommand() {
        return checkCommand;
    }

    /** Unknown or blank values give Java, the original behaviour; recognisable free text gives OTHER. */
    public static StageLanguage from(String value) {
        if (value == null || value.isBlank()) {
            return JAVA;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        String alias = switch (v) {
            case "py", "python3" -> "python";
            case "js", "node", "nodejs", "node.js" -> "javascript";
            case "ts" -> "typescript";
            case "c#", "cs", "dotnet", ".net" -> "csharp";
            case "c++", "cxx" -> "cpp";
            case "golang" -> "go";
            case "rs" -> "rust";
            case "kt" -> "kotlin";
            case "rb" -> "ruby";
            case "pl/sql", "pl-sql", "oracle", "sql" -> "plsql";
            case "cbl" -> "cobol";
            default -> v;
        };
        return Arrays.stream(values())
                .filter(l -> l.id.equals(alias) || l.label.equalsIgnoreCase(v))
                .findFirst()
                .orElse(OTHER);
    }
}
