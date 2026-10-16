package com.agentic.chatbot.stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plain-English business logic derived from stage output: the Developer prompt asks for {@code Business rule:} comments, so
 * those comments become {@code Foo.business-logic.md} next to each code file and one stage-level {@code BUSINESS_LOGIC.md}.
 * Works offline (no model call) and is skipped gracefully when a file has no such comments.
 */
public final class StageBusinessLogic {

    public static final String PROJECT_FILE = "BUSINESS_LOGIC.md";

    private static final Pattern RULE = Pattern.compile("(?i)Business rule:\\s*(.+?)\\s*(?:\\*/|-->)?\\s*$");
    private static final Pattern CODE_FILE =
            Pattern.compile("(?i).+\\.(java|py|js|ts|tsx|jsx|cs|rb|go|php|kt|scala|swift|rs|cpp|c|sql)$");

    private StageBusinessLogic() {
    }

    public static boolean isCode(String path) {
        return CODE_FILE.matcher(path).matches();
    }

    /** The "Business rule:" sentences of one file, in order, without duplicates. */
    public static List<String> rules(String content) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        if (content != null) {
            for (String line : content.split("\\R")) {
                Matcher m = RULE.matcher(line);
                if (m.find() && !m.group(1).isBlank()) {
                    seen.putIfAbsent(m.group(1).strip(), true);
                }
            }
        }
        return List.copyOf(seen.keySet());
    }

    /** {@code dir/Foo.java} becomes {@code dir/Foo.business-logic.md}. */
    public static String companionName(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return (dot > slash + 1 ? path.substring(0, dot) : path) + ".business-logic.md";
    }

    /** Markdown for one code file; a short note when it carries no business-rule comments. */
    public static String fileMarkdown(String path, String content) {
        StringBuilder sb = new StringBuilder("# Business logic: ").append(path).append("\n\n");
        List<String> rules = rules(content);
        if (rules.isEmpty()) {
            sb.append("No business rules are described in the comments of this file.\n");
        } else {
            rules.forEach(r -> sb.append("- ").append(r).append('\n'));
        }
        return sb.toString();
    }

    /** Stage-level document: the user stories (when the run has them) and the rules found in each code file. */
    public static String projectMarkdown(String title, String stories, Map<String, String> codeFiles) {
        StringBuilder sb = new StringBuilder("# Business logic: ").append(title).append("\n\n");
        if (stories != null && !stories.isBlank()) {
            sb.append("## User stories\n\n").append(stories.strip()).append("\n\n");
        }
        sb.append("## Business rules in the code\n\n");
        boolean any = false;
        for (Map.Entry<String, String> e : codeFiles.entrySet()) {
            List<String> rules = rules(e.getValue());
            if (rules.isEmpty()) {
                continue;
            }
            any = true;
            sb.append("### ").append(e.getKey()).append("\n\n");
            rules.forEach(r -> sb.append("- ").append(r).append('\n'));
            sb.append('\n');
        }
        if (!any) {
            sb.append("No business-rule comments were found in the generated code yet. Run the Developer stage to produce them.\n");
        }
        return sb.toString();
    }
}
