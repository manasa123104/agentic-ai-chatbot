package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline Manual (functional) test cases: the cases a person runs by hand against the running app. Each Given/When/Then
 * scenario of the user stories becomes one case with preconditions (Given), numbered steps (When / And) and the expected
 * result (Then). Without stories, cases are derived from the public methods of the code.
 */
public final class ManualTestTemplates {

    public static final String PATH = "manual-tests/manual-test-cases.md";

    private static final Pattern STEP = Pattern.compile("^\\s*(Given|When|Then|And|But)\\b\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCENARIO = Pattern.compile("^\\s*Scenario(?: Outline)?:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORY = Pattern.compile("^\\s*#{1,4}\\s*Story\\s*\\d*[:.]?\\s*(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern METHOD = Pattern.compile(
            "(?m)^\\s*(?:public|export|def|func|fn|pub)\\s+(?:static\\s+|async\\s+)*(?:[\\w<>\\[\\],.?]+\\s+)?(\\w+)\\s*\\(");

    private ManualTestTemplates() {
    }

    /** One manual test case. */
    record Case(String story, String title, List<String> given, List<String> steps, List<String> expected) {
    }

    public static Map<String, String> files(StageDomain domain, String stories, String code, String baseUrl) {
        List<Case> cases = fromStories(stories);
        String basis = "user stories";
        if (cases.isEmpty()) {
            cases = fromCode(code);
            basis = "code";
        }
        Map<String, String> out = new LinkedHashMap<>();
        out.put(PATH, render(domain, cases, basis, baseUrl));
        return out;
    }

    /** True when at least one case can be written from the stories (Given/When/Then scenarios) or the code (methods). */
    public static boolean hasCases(String stories, String code) {
        return !fromStories(stories).isEmpty() || !fromCode(code).isEmpty();
    }

    static List<Case> fromStories(String stories) {
        List<Case> cases = new ArrayList<>();
        if (stories == null || stories.isBlank()) {
            return cases;
        }
        String story = "";
        String title = null;
        List<String> given = new ArrayList<>();
        List<String> steps = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (String raw : stories.split("\\R")) {
            String line = raw.strip();
            Matcher sc = SCENARIO.matcher(line);
            Matcher st = STORY.matcher(line);
            if (sc.matches() || st.matches()) {
                flush(cases, story, title, given, steps, expected);
                title = null;
                if (sc.matches()) {
                    title = sc.group(1).strip();
                } else {
                    story = st.group(1).strip();
                }
                continue;
            }
            Matcher step = STEP.matcher(line);
            if (title != null && step.matches()) {
                String kind = step.group(1).toLowerCase(Locale.ROOT);
                String text = step.group(2).strip();
                if (kind.equals("given")) {
                    given.add(text);
                } else if (kind.equals("when")) {
                    steps.add(text);
                } else if (kind.equals("then")) {
                    expected.add(text);
                } else if (!expected.isEmpty()) {
                    expected.add(text);
                } else if (!steps.isEmpty()) {
                    steps.add(text);
                } else {
                    given.add(text);
                }
            }
        }
        flush(cases, story, title, given, steps, expected);
        return cases;
    }

    private static void flush(List<Case> cases, String story, String title, List<String> given, List<String> steps,
                              List<String> expected) {
        if (title != null && (!steps.isEmpty() || !expected.isEmpty() || !given.isEmpty())) {
            cases.add(new Case(story, title, List.copyOf(given), List.copyOf(steps), List.copyOf(expected)));
        }
        given.clear();
        steps.clear();
        expected.clear();
    }

    static List<Case> fromCode(String code) {
        List<Case> cases = new ArrayList<>();
        if (code == null || code.isBlank()) {
            return cases;
        }
        List<String> seen = new ArrayList<>();
        Matcher m = METHOD.matcher(code);
        while (m.find() && seen.size() < 12) {
            String name = m.group(1);
            if (name.equals("main") || name.startsWith("get") && name.length() < 6 || seen.contains(name)) {
                continue;
            }
            seen.add(name);
            String words = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').toLowerCase(Locale.ROOT);
            cases.add(new Case("", "Valid input: " + words,
                    List.of("The application is running and you are signed in with valid test data"),
                    List.of("Open the screen or call the endpoint for \"" + words + "\"",
                            "Enter valid values for every field", "Submit"),
                    List.of("The action succeeds and the confirmation or result is shown",
                            "The data is saved and appears correctly when you reopen it")));
            cases.add(new Case("", "Invalid input: " + words,
                    List.of("The application is running and you are signed in with valid test data"),
                    List.of("Open the screen or call the endpoint for \"" + words + "\"",
                            "Leave a required field empty or enter an out-of-range value", "Submit"),
                    List.of("A clear validation error is shown", "Nothing is saved or changed")));
        }
        return cases;
    }

    static String render(StageDomain domain, List<Case> cases, String basis, String baseUrl) {
        StringBuilder sb = new StringBuilder("# Manual test cases (functional testing)\n\n")
                .append("Domain: ").append(domain.label()).append(" | Application URL: ").append(baseUrl)
                .append(" | Written from the ").append(basis).append("\n\n")
                .append("Run each case by hand in the browser (or with a REST client). Record Pass or Fail and any notes. ")
                .append("Do the preconditions first, then follow the numbered steps, then compare with the expected result.\n\n");
        if (cases.isEmpty()) {
            return sb.append("No user stories or code were found to write cases from. Add stories with Given/When/Then "
                    + "scenarios and run the Tester again.\n").toString();
        }
        sb.append("| ID | Title | Result |\n|---|---|---|\n");
        int n = 0;
        for (Case c : cases) {
            sb.append("| ").append(id(++n)).append(" | ").append(c.title().replace("|", "/")).append(" | Pass / Fail |\n");
        }
        sb.append('\n');
        n = 0;
        for (Case c : cases) {
            sb.append("## ").append(id(++n)).append(": ").append(c.title()).append("\n\n");
            if (!c.story().isBlank()) {
                sb.append("**Story:** ").append(c.story()).append("\n\n");
            }
            sb.append("**Preconditions**\n");
            if (c.given().isEmpty()) {
                sb.append("- The application is running at ").append(baseUrl).append('\n');
            } else {
                c.given().forEach(g -> sb.append("- ").append(g).append('\n'));
            }
            sb.append("\n**Steps**\n");
            if (c.steps().isEmpty()) {
                sb.append("1. Open the application and perform the action described by the title\n");
            } else {
                for (int i = 0; i < c.steps().size(); i++) {
                    sb.append(i + 1).append(". ").append(c.steps().get(i)).append('\n');
                }
            }
            sb.append("\n**Expected result**\n");
            if (c.expected().isEmpty()) {
                sb.append("- The behaviour matches the story\n");
            } else {
                c.expected().forEach(e -> sb.append("- ").append(e).append('\n'));
            }
            sb.append("\n**Actual result / notes:** ____________________\n\n");
        }
        return sb.toString();
    }

    private static String id(int n) {
        return String.format("TC-%03d", n);
    }
}
