package com.agentic.chatbot.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Files uploaded on the home page travel inside the prompt, after a marker line:
 *
 * <pre>
 * Build a bakery website
 *
 * === Uploaded files (use as the user stories and requirements for the website) ===
 * --- stories.md ---
 * As a customer, I want to ...
 * </pre>
 *
 * <p>This class reads that format and produces the text the website generators work from: the typed request plus the
 * requirement text of the files (stories, notes, specs), plus the module names found in uploaded code - never the raw
 * source code, which would only confuse topic, name and feature detection. Several files are used together.
 */
public final class UploadedStory {

    /** The line that starts the uploaded files in a prompt. */
    public static final String MARKER = "=== Uploaded files";

    /** Request used when a file is uploaded without any typed words. */
    public static final String DEFAULT_REQUEST = "Build a website from the uploaded user stories and requirements.";

    /** One uploaded file. */
    public record Doc(String path, String text, Kind kind) {
    }

    public enum Kind { REQUIREMENTS, CODE, DATA }

    /** The typed request and the files that came with it. */
    public record Parsed(String request, List<Doc> docs) {
        public boolean hasFiles() {
            return !docs.isEmpty();
        }
    }

    private static final Pattern MARKER_LINE = Pattern.compile("(?m)^=== Uploaded files[^\\n]*===\\s*$");
    private static final Pattern DOC_HEAD = Pattern.compile("(?m)^--- (.+?)(?: \\(truncated\\))? ---\\s*$");
    private static final Pattern DECLARATION = Pattern.compile(
            "\\b(?:class|interface|enum|record|struct|trait|object|def|func|function|fn|fun)\\s+([A-Za-z][A-Za-z0-9_]{2,})");

    private static final Set<String> REQUIREMENT_EXT = Set.of("txt", "md", "markdown", "rst", "feature", "story", "text");
    private static final Set<String> DATA_EXT = Set.of("json", "yaml", "yml", "xml", "toml", "ini", "cfg", "conf",
            "properties", "csv", "tsv", "env", "http", "gradle");
    private static final Set<String> NOISE_NAMES = Set.of("main", "test", "tests", "init", "app", "application", "index",
            "util", "utils", "helper", "helpers", "config", "setup", "run", "get", "set", "new", "string", "self", "this");

    private static final int MAX_TEXT_PER_FILE = 6000;

    private UploadedStory() {
    }

    /** True when the prompt carries uploaded files. */
    public static boolean hasUploads(String prompt) {
        return prompt != null && MARKER_LINE.matcher(prompt).find();
    }

    /** Splits a prompt into the typed request and the uploaded files (none when there is no marker). */
    public static Parsed parse(String prompt) {
        if (prompt == null) {
            return new Parsed("", List.of());
        }
        Matcher marker = MARKER_LINE.matcher(prompt);
        if (!marker.find()) {
            return new Parsed(prompt.strip(), List.of());
        }
        String request = prompt.substring(0, marker.start()).strip();
        String rest = prompt.substring(marker.end());
        List<Doc> docs = new ArrayList<>();
        Matcher head = DOC_HEAD.matcher(rest);
        List<int[]> spans = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (head.find()) {
            names.add(head.group(1).strip());
            spans.add(new int[]{head.start(), head.end()});
        }
        for (int i = 0; i < names.size(); i++) {
            int from = spans.get(i)[1];
            int to = i + 1 < names.size() ? spans.get(i + 1)[0] : rest.length();
            docs.add(new Doc(names.get(i), rest.substring(from, to).strip(), kindOf(names.get(i))));
        }
        return new Parsed(request, docs);
    }

    /**
     * The text the generators use: the request, the requirement files' text and the modules of uploaded code. Prompts
     * without uploads are returned unchanged.
     */
    public static String forGeneration(String prompt) {
        if (!hasUploads(prompt)) {
            return prompt;
        }
        Parsed parsed = parse(prompt);
        StringBuilder sb = new StringBuilder();
        sb.append(parsed.request().isBlank() ? DEFAULT_REQUEST : parsed.request());
        List<String> modules = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (Doc doc : parsed.docs()) {
            names.add(doc.path());
            switch (doc.kind()) {
                case REQUIREMENTS -> sb.append("\n\n").append(limit(doc.text()));
                case CODE -> modules.addAll(moduleNames(doc.text()));
                case DATA -> {
                    if (doc.path().toLowerCase(Locale.ROOT).matches(".*\\.html?$")) {
                        sb.append("\n\n").append(limit(stripTags(doc.text())));
                    }
                }
            }
        }
        List<String> uniqueModules = new ArrayList<>(new LinkedHashSet<>(modules));
        if (!uniqueModules.isEmpty()) {
            sb.append("\n\nThe project includes: ")
                    .append(String.join(", ", uniqueModules.subList(0, Math.min(6, uniqueModules.size())))).append('.');
        }
        if (!names.isEmpty()) {
            sb.append("\n\nUploaded files: ").append(String.join(", ", names)).append('.');
        }
        return sb.toString();
    }

    /** Names of the uploaded files, for display. */
    public static List<String> fileNames(String prompt) {
        return parse(prompt).docs().stream().map(Doc::path).toList();
    }

    static Kind kindOf(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String ext = dot < 0 ? "" : lower.substring(dot + 1);
        if (REQUIREMENT_EXT.contains(ext)) {
            return Kind.REQUIREMENTS;
        }
        if (DATA_EXT.contains(ext) || ext.equals("html") || ext.equals("htm") || ext.equals("css")) {
            return Kind.DATA;
        }
        return Kind.CODE;
    }

    /** Class / function names of a source file as words ("OrderService" -> "Order Service"), at most four. */
    static List<String> moduleNames(String code) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = DECLARATION.matcher(code == null ? "" : code);
        while (m.find() && out.size() < 4) {
            String words = words(m.group(1));
            if (!words.isEmpty() && !NOISE_NAMES.contains(words.toLowerCase(Locale.ROOT))) {
                out.add(words);
            }
        }
        return new ArrayList<>(out);
    }

    private static String words(String identifier) {
        String spaced = identifier.replace('_', ' ').replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").trim();
        StringBuilder sb = new StringBuilder();
        for (String w : spaced.split("\\s+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String stripTags(String html) {
        return html.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ").replaceAll("<[^>]+>", " ")
                .replaceAll("[ \\t]+", " ").replaceAll("\\n\\s*\\n+", "\n").strip();
    }

    private static String limit(String text) {
        return text.length() <= MAX_TEXT_PER_FILE ? text : text.substring(0, MAX_TEXT_PER_FILE);
    }
}
