package com.agentic.chatbot.stage;

import com.agentic.chatbot.stage.StageResponse.StageFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between a list of files and the single text "bundle" a stage shows, and recovers Java file names
 * from code. Code stages separate files with {@code === FILE: path ===} lines so their output can be edited,
 * pasted into the next stage and split back into files.
 */
public final class StageFiles {

    private static final Pattern MARKER = Pattern.compile(
            "^\\s*(?:[/#*]+\\s*)?(?:=+\\s*)?FILE:\\s*`?([\\w./\\\\ -]+?)`?\\s*(?:=+)?\\s*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE = Pattern.compile("```[\\w+#-]*[ \\t]*\\r?\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PUBLIC_TYPE = Pattern.compile(
            "^\\s*public\\s+(?:(?:abstract|final|sealed|non-sealed|static)\\s+)*(?:class|record|enum|interface|@interface)\\s+([A-Z]\\w*)",
            Pattern.MULTILINE);
    private static final Pattern ANY_TYPE = Pattern.compile(
            "^\\s*(?:(?:public|abstract|final|sealed|non-sealed)\\s+)*(?:class|record|enum|interface)\\s+([A-Z]\\w*)",
            Pattern.MULTILINE);

    private StageFiles() {
    }

    public static String bundle(List<StageFile> files) {
        if (files.size() == 1 && files.get(0).path().endsWith(".md")) {
            return files.get(0).content();
        }
        StringBuilder sb = new StringBuilder();
        for (StageFile f : files) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append("=== FILE: ").append(f.path()).append(" ===\n");
            sb.append(f.content().stripTrailing()).append('\n');
        }
        return sb.toString();
    }

    /** Splits a bundle on FILE markers; returns path (as written, possibly blank) to content, in order. */
    public static List<Map.Entry<String, String>> split(String text) {
        List<Map.Entry<String, String>> parts = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return parts;
        }
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        String currentPath = null;
        StringBuilder current = new StringBuilder();
        boolean sawMarker = false;
        for (String line : lines) {
            Matcher m = MARKER.matcher(line);
            if (m.matches()) {
                if (sawMarker || !current.toString().isBlank()) {
                    addPart(parts, currentPath, current.toString());
                }
                currentPath = m.group(1).trim().replace('\\', '/');
                current.setLength(0);
                sawMarker = true;
            } else {
                current.append(line).append('\n');
            }
        }
        addPart(parts, currentPath, current.toString());
        if (!sawMarker) {
            return splitFences(text);
        }
        return parts;
    }

    private static void addPart(List<Map.Entry<String, String>> parts, String path, String raw) {
        String content = stripFences(raw).strip();
        if (!content.isBlank()) {
            parts.add(Map.entry(path == null ? "" : path, content + "\n"));
        }
    }

    /** No markers: each fenced block is a file; without fences the whole text is one file. */
    private static List<Map.Entry<String, String>> splitFences(String text) {
        List<Map.Entry<String, String>> parts = new ArrayList<>();
        Matcher m = FENCE.matcher(text);
        while (m.find()) {
            String body = m.group(1).strip();
            if (!body.isBlank()) {
                parts.add(Map.entry("", body + "\n"));
            }
        }
        if (parts.isEmpty() && !text.isBlank()) {
            parts.add(Map.entry("", text.strip() + "\n"));
        }
        return parts;
    }

    /** Removes a surrounding Markdown code fence, if the text has one. */
    public static String stripFences(String text) {
        if (text == null) {
            return "";
        }
        Matcher m = FENCE.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        String t = text.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            t = nl < 0 ? "" : t.substring(nl + 1);
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t;
    }

    /**
     * Java sources in a bundle or a paste, one entry per compilation unit, keyed by canonical
     * {@code root/pkg/path/Type.java}. Pasted code with several {@code package} lines is split at each one.
     */
    public static Map<String, String> javaSources(String text, String root, String defaultPackage) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> part : split(text)) {
            String path = part.getKey();
            if (!path.isBlank() && !path.toLowerCase(Locale.ROOT).endsWith(".java")) {
                continue;
            }
            for (String unit : splitCompilationUnits(part.getValue())) {
                if (!looksLikeJava(unit)) {
                    continue;
                }
                String code = ensurePackage(unit, defaultPackage);
                Optional<String> type = primaryType(code);
                if (type.isEmpty()) {
                    continue;
                }
                out.put(javaPath(root, packageOf(code).orElse(""), type.get()), code);
            }
        }
        return out;
    }

    private static List<String> splitCompilationUnits(String code) {
        Matcher m = PACKAGE.matcher(code);
        List<Integer> starts = new ArrayList<>();
        while (m.find()) {
            starts.add(m.start());
        }
        if (starts.size() <= 1) {
            return List.of(code);
        }
        List<String> units = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : code.length();
            int start = i == 0 ? 0 : starts.get(i);
            units.add(code.substring(start, end).strip() + "\n");
        }
        return units;
    }

    public static boolean looksLikeJava(String code) {
        return code != null && ANY_TYPE.matcher(code).find();
    }

    public static Optional<String> packageOf(String code) {
        Matcher m = PACKAGE.matcher(code);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }

    public static Optional<String> primaryType(String code) {
        Matcher m = PUBLIC_TYPE.matcher(code);
        if (m.find()) {
            return Optional.of(m.group(1));
        }
        Matcher any = ANY_TYPE.matcher(code);
        return any.find() ? Optional.of(any.group(1)) : Optional.empty();
    }

    public static String ensurePackage(String code, String defaultPackage) {
        String c = code.strip().replaceFirst("(?m)^(\\s*package\\s+[\\w.]*?\\w)\\.+\\s*;", "$1;") + "\n";
        if (packageOf(c).isPresent() || defaultPackage == null || defaultPackage.isBlank()) {
            return c;
        }
        return "package " + defaultPackage + ";\n\n" + c;
    }

    public static String javaPath(String root, String pkg, String type) {
        String dir = pkg == null || pkg.isBlank() ? "" : pkg.replace('.', '/') + "/";
        String prefix = root == null || root.isBlank() ? "" : (root.endsWith("/") ? root : root + "/");
        return prefix + dir + type + ".java";
    }

    /** Public method and constructor signatures only: a compact summary of a class for small-context prompts. */
    public static String signatures(String code) {
        StringBuilder sb = new StringBuilder();
        for (String line : code.split("\\R")) {
            String t = line.strip();
            boolean decl = t.startsWith("public ") || t.startsWith("package ") || t.startsWith("enum ")
                    || t.matches("^(?:public\\s+)?(?:record|enum|class|interface)\\s.*");
            if (decl && !t.startsWith("//") && !t.startsWith("*")) {
                if (t.endsWith("{")) {
                    t = t.substring(0, t.length() - 1).strip() + (t.contains("(") ? ";" : " {");
                }
                sb.append(t).append('\n');
            } else if (t.matches("^[A-Z_][A-Z0-9_]*\\s*(?:\\(.*\\))?\\s*[,;]?$")) {
                sb.append("  ").append(t).append('\n');
            }
        }
        return sb.toString();
    }

    private static final Map<String, String> JDK_TYPES = Map.ofEntries(
            Map.entry("BigDecimal", "java.math.BigDecimal"), Map.entry("BigInteger", "java.math.BigInteger"),
            Map.entry("RoundingMode", "java.math.RoundingMode"), Map.entry("LocalDate", "java.time.LocalDate"),
            Map.entry("LocalDateTime", "java.time.LocalDateTime"), Map.entry("Instant", "java.time.Instant"),
            Map.entry("Clock", "java.time.Clock"), Map.entry("Duration", "java.time.Duration"),
            Map.entry("ZoneOffset", "java.time.ZoneOffset"), Map.entry("List", "java.util.List"),
            Map.entry("ArrayList", "java.util.ArrayList"), Map.entry("Map", "java.util.Map"),
            Map.entry("HashMap", "java.util.HashMap"), Map.entry("LinkedHashMap", "java.util.LinkedHashMap"),
            Map.entry("Set", "java.util.Set"), Map.entry("HashSet", "java.util.HashSet"),
            Map.entry("Optional", "java.util.Optional"), Map.entry("Objects", "java.util.Objects"),
            Map.entry("UUID", "java.util.UUID"), Map.entry("Collections", "java.util.Collections"),
            Map.entry("ConcurrentHashMap", "java.util.concurrent.ConcurrentHashMap"));

    /** Small models often use JDK types without importing them; adds the missing imports after the package line. */
    public static String addMissingJdkImports(String code) {
        List<String> missing = new ArrayList<>();
        String body = code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "").replaceAll("\"(?:\\\\.|[^\"\\\\])*\"", "\"\"");
        for (Map.Entry<String, String> e : JDK_TYPES.entrySet()) {
            String simple = e.getKey();
            String pkg = e.getValue().substring(0, e.getValue().lastIndexOf('.'));
            boolean used = Pattern.compile("(?<![\\w.])" + simple + "\\b(?!\\s*\\.java)").matcher(body).find();
            boolean imported = code.contains("import " + e.getValue() + ";") || code.contains("import " + pkg + ".*;");
            boolean declared = Pattern.compile("\\b(?:class|record|enum|interface)\\s+" + simple + "\\b").matcher(body).find();
            if (used && !imported && !declared) {
                missing.add("import " + e.getValue() + ";");
            }
        }
        if (missing.isEmpty()) {
            return code;
        }
        missing.sort(String::compareTo);
        String imports = String.join("\n", missing) + "\n";
        Matcher pkg = PACKAGE.matcher(code);
        if (pkg.find()) {
            return code.substring(0, pkg.end()) + "\n\n" + imports + code.substring(pkg.end()).replaceFirst("^\\n+", "\n");
        }
        return imports + "\n" + code;
    }

    /** Removes lines a looping model repeated: any non-blank line already seen in the same block is dropped. */
    public static String collapseRepeats(String text) {
        StringBuilder out = new StringBuilder();
        java.util.Set<String> seenInBlock = new java.util.HashSet<>();
        String previous = null;
        for (String line : text.split("\\R", -1)) {
            String key = line.strip();
            if (key.startsWith("#") || key.toLowerCase(Locale.ROOT).startsWith("scenario")) {
                seenInBlock.clear();
            }
            boolean step = key.matches("(?i)^(and|but|then|given|when)\\b.*");
            if (!key.isEmpty() && ((step && !seenInBlock.add(key)) || key.equals(previous))) {
                continue;
            }
            out.append(line).append('\n');
            previous = key.isEmpty() ? previous : key;
        }
        return out.toString().stripTrailing() + "\n";
    }

    /** Safe relative path: forward slashes, no drive letters, no "..". */
    public static String safeRelative(String path) {
        if (path == null) {
            return "";
        }
        String p = path.replace('\\', '/').strip();
        p = p.replaceAll("^[A-Za-z]:", "");
        List<String> kept = new ArrayList<>();
        for (String seg : p.split("/")) {
            if (seg.isBlank() || seg.equals(".") || seg.equals("..")) {
                continue;
            }
            kept.add(seg.replaceAll("[^\\w.\\- ]", "_"));
        }
        return String.join("/", kept);
    }
}
