package com.agentic.chatbot.stage;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The five independent stage bots, in the order they are chained.
 */
public enum StageType {
    BA("ba", "Business Analyst", "User stories with Given/When/Then acceptance criteria"),
    DEVELOPER("developer", "Developer", "Commented code in the chosen language"),
    TESTER("tester", "Tester", "Unit tests and UI automation tests"),
    DEPLOY("deploy", "Deploy", "Dockerfile, scripts, CI/CD workflow, Kubernetes YAML, DEPLOY.md"),
    TRAINER("trainer", "Trainer", "Run summary, approved training examples and stats");

    /** Older and alternative ids still accepted by the API; see {@link #testerPart(String)} for the Tester ones. */
    private static final Map<String, StageType> ALIASES = Map.of(
            "unit-test", TESTER, "unit-tester", TESTER, "functional-test", TESTER, "automation-test", TESTER,
            "automation", TESTER, "java-developer", DEVELOPER, "dev", DEVELOPER, "deployment", DEPLOY,
            "business-analyst", BA, "training", TRAINER);

    private final String id;
    private final String title;
    private final String produces;

    StageType(String id, String title, String produces) {
        this.id = id;
        this.title = title;
        this.produces = produces;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String produces() {
        return produces;
    }

    public Optional<StageType> next() {
        int i = ordinal() + 1;
        return i < values().length ? Optional.of(values()[i]) : Optional.empty();
    }

    public static Optional<StageType> fromId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String wanted = id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        return Arrays.stream(values())
                .filter(s -> s.id.equals(wanted) || s.name().equalsIgnoreCase(id.trim()))
                .findFirst()
                .or(() -> Optional.ofNullable(ALIASES.get(wanted)));
    }

    /** "unit" or "automation" when an old Tester alias was used, else empty (both parts). */
    public static Optional<String> testerPart(String id) {
        String wanted = id == null ? "" : id.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        if (wanted.equals("unit-test") || wanted.equals("unit-tester")) {
            return Optional.of("unit");
        }
        if (wanted.equals("functional-test") || wanted.startsWith("automation")) {
            return Optional.of("automation");
        }
        return Optional.empty();
    }
}
