package com.agentic.chatbot.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Splits source files that are too large for one model call into parts that end on member boundaries
 * (after a method, field or top-level declaration), and joins the converted parts back into one file.
 */
public final class CodeChunker {

    /**
     * @param text          the source text of this part
     * @param continuesType {@code true} when this part continues a class/type opened in an earlier part
     */
    public record Chunk(String text, boolean continuesType) {
    }

    private static final Set<String> INDENT_LANGUAGES = Set.of("python", "ruby", "cobol", "plsql");
    /** Targets whose methods live inside a class body, so continuation parts are inserted before its closing brace. */
    private static final Set<String> CLASS_BODY_TARGETS = Set.of(
            "java", "csharp", "kotlin", "swift", "cpp", "php", "typescript", "javascript");
    private static final Set<String> BRACE_TARGETS = Set.of(
            "java", "csharp", "kotlin", "swift", "cpp", "php", "typescript", "javascript", "go", "rust", "c");
    /** A single member may exceed the part size up to this factor before it is cut between lines. */
    private static final int OVERSIZED_MEMBER_FACTOR = 3;
    private static final Pattern PYTHON_CUT = Pattern.compile("^( {0,4})(@|def |async def |class )");
    private static final Pattern RUBY_CUT = Pattern.compile("^( {0,2})(def |class |module )");

    private CodeChunker() {
    }

    public static List<Chunk> split(String code, String sourceLanguageId, int maxChars) {
        if (code == null || code.length() <= maxChars) {
            return List.of(new Chunk(code == null ? "" : code, false));
        }
        String id = sourceLanguageId == null ? "" : sourceLanguageId.toLowerCase(Locale.ROOT);
        boolean braces = !INDENT_LANGUAGES.contains(id) && code.indexOf('{') >= 0;
        List<int[]> cuts = braces ? braceCuts(code) : lineCuts(code, id);
        return pack(code, cuts, maxChars);
    }

    /** Text before the first member of the first type (package, imports, annotations, class line), for context. */
    public static String header(String code, int maxChars) {
        if (code == null) {
            return "";
        }
        int[] depth = depths(code);
        for (int i = 0; i < code.length(); i++) {
            if (code.charAt(i) == '{' && depth[i] == 0) {
                return trim(code.substring(0, i + 1), maxChars);
            }
        }
        int lines = 0;
        int end = 0;
        while (end < code.length() && lines < 40) {
            int next = code.indexOf('\n', end);
            end = next < 0 ? code.length() : next + 1;
            lines++;
        }
        return trim(code.substring(0, end), maxChars);
    }

    /**
     * Declaration line of the type that encloses {@code offset} in the source (for example
     * {@code class TaxRules {}), or "" when the offset is at top level.
     */
    public static String enclosingType(String code, int offset) {
        if (code == null || offset <= 0) {
            return "";
        }
        int end = Math.min(offset, code.length());
        int[] depth = depths(code);
        int open = -1;
        for (int i = 0; i < end; i++) {
            char c = code.charAt(i);
            if (c == '{' && depth[i] == 0 && !inCommentOrString(code, depth, i)) {
                open = i;
            } else if (c == '}' && depth[i] == 1 && !inCommentOrString(code, depth, i)) {
                open = -1;
            }
        }
        if (open >= 0) {
            int lineStart = code.lastIndexOf('\n', open) + 1;
            return code.substring(lineStart, open + 1).strip();
        }
        String before = code.substring(0, end);
        var python = Pattern.compile("(?m)^class\\s+\\w+[^\\n]*:\\s*$").matcher(before);
        String last = "";
        while (python.find()) {
            last = python.group().strip();
        }
        return last;
    }

    /** Whether converted code has a top-level class (or Python/Ruby class) that later members can be inserted into. */
    public static boolean keepsType(String converted, String targetLanguageId) {
        return insertionPoint(converted, targetLanguageId == null ? "" : targetLanguageId.toLowerCase(Locale.ROOT)) >= 0;
    }

    /** Joins converted parts; each part after the first is merged with {@link #merge}. */
    public static String stitch(String targetLanguageId, List<String> parts, boolean continuesType) {
        if (parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        String result = parts.get(0);
        for (String part : parts.subList(1, parts.size())) {
            result = merge(targetLanguageId, result, part, continuesType && keepsType(result, targetLanguageId));
        }
        return finish(targetLanguageId, result);
    }

    /**
     * Repairs the joined file: for brace languages, closing braces without an opening brace are removed and braces a
     * truncated answer left open are closed. Ends the file with a newline.
     */
    public static String finish(String targetLanguageId, String code) {
        String target = targetLanguageId == null ? "" : targetLanguageId.toLowerCase(Locale.ROOT);
        String text = code.stripTrailing();
        if (!BRACE_TARGETS.contains(target)) {
            return text + "\n";
        }
        String[] split = splitStrayClosers(text);
        text = split[1].isBlank() ? split[0].stripTrailing()
                : split[0].stripTrailing() + "\n" + dropLeadingBlankLines(split[1].stripTrailing());
        StringBuilder sb = new StringBuilder(text);
        for (int i = braceBalance(text); i > 0; i--) {
            sb.append("\n}");
        }
        return sb.append('\n').toString();
    }

    /**
     * Splits converted members (top-level blocks of a part) so individual functions can be dropped: for brace
     * languages each block ends with a '}' that returns to depth 0; for Python each block starts at a def/decorator
     * with the smallest indentation. Concatenating the blocks gives back the input.
     */
    public static List<String> blocks(String part, String targetLanguageId) {
        String target = targetLanguageId == null ? "" : targetLanguageId.toLowerCase(Locale.ROOT);
        List<String> blocks = new ArrayList<>();
        if (target.equals("python")) {
            String[] lines = part.split("(?<=\n)");
            int indent = Integer.MAX_VALUE;
            for (String line : lines) {
                var m = Pattern.compile("^(\\s*)(?:def |async def |@)").matcher(line);
                if (m.find()) {
                    indent = Math.min(indent, m.group(1).length());
                }
            }
            StringBuilder current = new StringBuilder();
            boolean previousDecorator = false;
            for (String line : lines) {
                String stripped = line.stripLeading();
                int lineIndent = line.length() - stripped.length();
                boolean start = PYTHON_WRAPPER.matcher(line.stripTrailing()).matches()
                        || lineIndent == indent && (stripped.startsWith("@")
                        || ((stripped.startsWith("def ") || stripped.startsWith("async def ")) && !previousDecorator));
                if (start && !current.toString().isBlank()) {
                    blocks.add(current.toString());
                    current.setLength(0);
                }
                current.append(line);
                if (!line.isBlank()) {
                    previousDecorator = lineIndent == indent && stripped.startsWith("@");
                }
            }
            if (!current.isEmpty()) {
                blocks.add(current.toString());
            }
            return blocks;
        }
        if (!BRACE_TARGETS.contains(target)) {
            return List.of(part);
        }
        int[] depth = depths(part);
        int start = 0;
        for (int i = 0; i < part.length(); i++) {
            if (part.charAt(i) == '}' && depth[i] == 1 && !inCommentOrString(part, depth, i)) {
                int end = part.indexOf('\n', i);
                end = end < 0 ? part.length() : end + 1;
                blocks.add(part.substring(start, end));
                start = end;
                i = end - 1;
            }
        }
        if (start < part.length()) {
            blocks.add(part.substring(start));
        }
        List<String> flat = new ArrayList<>();
        for (String block : blocks) {
            flat.addAll(openWrapper(block, target));
        }
        return flat;
    }

    /** A class the model wrapped around its members becomes header, member blocks and closing brace. */
    private static List<String> openWrapper(String block, String target) {
        String first = firstCodeLine(block);
        if (first == null || !TYPE_WRAPPER.matcher(first).matches()) {
            return List.of(block);
        }
        int open = block.indexOf('{', block.indexOf(first));
        int headEnd = block.indexOf('\n', open);
        int close = block.lastIndexOf('}');
        if (headEnd < 0 || close <= headEnd) {
            return List.of(block);
        }
        int closeLine = block.lastIndexOf('\n', close) + 1;
        if (closeLine <= headEnd) {
            return List.of(block);
        }
        List<String> parts = new ArrayList<>();
        parts.add(block.substring(0, headEnd + 1));
        parts.addAll(blocks(block.substring(headEnd + 1, closeLine), target));
        parts.add(block.substring(closeLine));
        return parts;
    }

    /**
     * Adds one converted part to the code converted so far. When {@code intoType} is set the part holds members of
     * the last class in {@code converted}: they are inserted inside it (before its closing brace / block end), a
     * repeated class declaration is unwrapped, and code after a stray class-closing brace is moved after the class.
     * Otherwise the part is appended. Import lines are moved to the top of the file either way.
     */
    public static String merge(String targetLanguageId, String converted, String part, boolean intoType) {
        String target = targetLanguageId == null ? "" : targetLanguageId.toLowerCase(Locale.ROOT);
        Set<String> imports = new java.util.LinkedHashSet<>();
        String body = takeImports(part, imports);
        String acc = addImports(converted, imports);
        int at = intoType ? insertionPoint(acc, target) : -1;
        if (at < 0) {
            String[] split = BRACE_TARGETS.contains(target) ? splitStrayClosers(body) : new String[] {body, ""};
            String appended = (split[0].stripTrailing() + "\n\n" + split[1].strip()).strip();
            return appended.isEmpty() ? acc : acc.stripTrailing() + "\n\n" + dropLeadingBlankLines(appended);
        }
        body = unwrap(body, target);
        String newTypes = "";
        if (BRACE_TARGETS.contains(target)) {
            String continued = lastTypeName(acc);
            body = withoutNestedRepeat(body, continued);
            int start = newTypeStart(body, continued);
            if (start >= 0) {
                newTypes = body.substring(start);
                body = body.substring(0, start);
            }
        }
        String[] split;
        if (target.equals("python")) {
            split = splitPythonMembers(body);
        } else if (BRACE_TARGETS.contains(target)) {
            split = splitStrayClosers(body);
        } else {
            split = new String[] {body, ""};
        }
        if (target.equals("javascript") || target.equals("typescript")) {
            split = moveTopLevelCode(split, target);
        }
        if (!newTypes.isBlank()) {
            split = new String[] {split[0], (split[1].strip() + "\n\n" + dedent(newTypes.strip())).strip()};
        }
        String members = dropLeadingBlankLines(split[0].stripTrailing());
        String after = acc.substring(at);
        String result = acc.substring(0, at).stripTrailing()
                + (members.isBlank() ? "" : "\n\n" + members) + "\n"
                + (target.equals("python") && !after.isBlank() ? "\n\n" : "") + after;
        if (!split[1].isBlank()) {
            result = result.stripTrailing() + "\n\n" + split[1].strip();
        }
        return result;
    }

    private static String lastTypeName(String code) {
        var m = TOP_LEVEL_TYPE.matcher(code);
        String name = null;
        while (m.find()) {
            var n = Pattern.compile("\\b(?:class|object|impl)\\s+(\\w+)").matcher(m.group());
            if (n.find()) {
                name = n.group(1);
            }
        }
        return name;
    }

    private static final Pattern NEW_TYPE_LINE = Pattern.compile("(?m)^[ \\t]*(?:export\\s+)?(?:default\\s+)?"
            + "(?:(?:public|final|abstract|sealed|data|open)\\s+)*(?:class|interface|enum|struct|object)\\s+(\\w+)");

    /**
     * Start of the line where a part declares another top-level type (the source closed the continued class and
     * began a new one), or -1. Nested ({@code static}) types are left in place.
     */
    private static int newTypeStart(String body, String continued) {
        int[] depth = depths(body);
        var m = NEW_TYPE_LINE.matcher(body);
        while (m.find()) {
            int at = body.indexOf(m.group(1), m.start());
            if (depth.length > 0 && depth[Math.min(at, depth.length - 1)] == 0 && !m.group(1).equals(continued)) {
                return m.start();
            }
        }
        return -1;
    }

    /** Removes a repeated declaration of the class being continued (and its closing brace) from inside a part. */
    private static String withoutNestedRepeat(String body, String className) {
        if (className == null) {
            return body;
        }
        Pattern decl = Pattern.compile("(?m)^[ \\t]*(?:export\\s+)?(?:default\\s+)?(?:(?:public|final|abstract)\\s+)*class\\s+"
                + Pattern.quote(className) + "\\b[^{;\\n]*\\{[ \\t]*\\r?\\n");
        String text = body;
        for (int guard = 0; guard < 10; guard++) {
            var m = decl.matcher(text);
            if (!m.find()) {
                return text;
            }
            int[] depth = depths(text);
            int open = text.lastIndexOf('{', m.end() - 1);
            int close = -1;
            for (int i = open + 1; i < text.length(); i++) {
                if (text.charAt(i) == '}' && depth[i] == depth[open] + 1 && !inCommentOrString(text, depth, i)) {
                    close = i;
                    break;
                }
            }
            String inner = close < 0 ? text.substring(m.end()) : text.substring(m.end(), close);
            String rest = close < 0 ? "" : text.substring(close + 1);
            text = text.substring(0, m.start()) + inner + rest;
        }
        return text;
    }

    private static final Pattern JS_TOP_LEVEL = Pattern.compile(
            "^(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?function\\b|^(?:const|let|var)\\s+\\w+\\s*=|^(?:await\\s+|new\\s+)?[\\w.]+\\s*\\(.*\\);?$");

    /** JavaScript/TypeScript blocks that are not class members (free functions, variables, calls) go after the class. */
    private static String[] moveTopLevelCode(String[] split, String target) {
        StringBuilder members = new StringBuilder();
        StringBuilder outside = new StringBuilder();
        for (String block : blocks(split[0], target)) {
            String first = firstCodeLine(block);
            (first != null && JS_TOP_LEVEL.matcher(first).find() ? outside : members).append(block);
        }
        String tail = outside.toString().strip();
        if (!split[1].isBlank()) {
            tail = (tail + "\n\n" + split[1].strip()).strip();
        }
        return new String[] {members.toString(), dedent(tail)};
    }

    private static String firstCodeLine(String block) {
        for (String line : block.split("\n")) {
            String s = line.strip();
            if (!s.isEmpty() && !s.startsWith("//") && !s.startsWith("/*") && !s.startsWith("*")) {
                return s;
            }
        }
        return null;
    }

    private static String dedent(String text) {
        int indent = Integer.MAX_VALUE;
        String[] lines = text.split("\n", -1);
        for (String line : lines) {
            if (!line.isBlank()) {
                indent = Math.min(indent, line.length() - line.stripLeading().length());
            }
        }
        if (indent == 0 || indent == Integer.MAX_VALUE) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i].isBlank() ? "" : lines[i].substring(indent)).append(i + 1 < lines.length ? "\n" : "");
        }
        return sb.toString();
    }

    private static final Pattern JS_MAIN_FUNCTION = Pattern.compile("(?m)^\\s*(?:export\\s+)?(?:async\\s+)?function\\s+main\\s*\\(");
    private static final Pattern JS_STATIC_MAIN = Pattern.compile("(?m)^\\s*(?:public\\s+)?static\\s+(?:async\\s+)?main\\s*\\(");
    private static final Pattern PY_MAIN_FUNCTION = Pattern.compile("(?m)^def\\s+main\\s*\\(");
    private static final Pattern PY_STATIC_MAIN = Pattern.compile("(?m)^\\s+def\\s+main\\s*\\(\\s*(?:args[^)]*)?\\)");

    /**
     * When the source has an entry point but the converted code only defines {@code main} without calling it, adds
     * the call so running the file behaves like running the source (JavaScript, TypeScript and Python).
     */
    public static String ensureMainCall(String targetLanguageId, String code) {
        String target = targetLanguageId == null ? "" : targetLanguageId.toLowerCase(Locale.ROOT);
        String body = code.stripTrailing();
        if (target.equals("javascript") || target.equals("typescript")) {
            if (JS_MAIN_FUNCTION.matcher(body).find()) {
                return Pattern.compile("(?m)^\\s*(?:await\\s+)?main\\s*\\(").matcher(body).find()
                        ? code : body + "\n\nmain();\n";
            }
            String owner = mainOwner(body, JS_STATIC_MAIN);
            return owner == null || body.contains(owner + ".main(") ? code : body + "\n\n" + owner + ".main();\n";
        }
        if (target.equals("python") && !body.contains("__main__")) {
            if (PY_MAIN_FUNCTION.matcher(body).find()) {
                return Pattern.compile("(?m)^main\\s*\\(").matcher(body).find()
                        ? code : body + "\n\n\nif __name__ == \"__main__\":\n    main()\n";
            }
            String owner = mainOwner(body, PY_STATIC_MAIN);
            if (owner != null && !body.contains(owner + ".main(")) {
                boolean takesArgs = Pattern.compile("(?m)^\\s+def\\s+main\\s*\\(\\s*args").matcher(body).find();
                return body + "\n\n\nif __name__ == \"__main__\":\n    " + owner + ".main(" + (takesArgs ? "[]" : "")
                        + ")\n";
            }
        }
        return code;
    }

    /** Name of the class whose body declares the main method matched by {@code pattern}, or null. */
    private static String mainOwner(String code, Pattern pattern) {
        var main = pattern.matcher(code);
        if (!main.find()) {
            return null;
        }
        String owner = null;
        var type = Pattern.compile("(?m)^(?:export\\s+)?(?:default\\s+)?class\\s+(\\w+)").matcher(code);
        while (type.find() && type.start() < main.start()) {
            owner = type.group(1);
        }
        return owner;
    }

    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^(?:export\\s+)?(?:default\\s+)?(?:(?:public|internal|private|open|final|abstract|sealed|data|static|partial)\\s+)*"
                    + "(?:class|object|impl)\\s+[\\w<>, :]+[^{;\\n]*\\{[ \\t]*$");

    /** Index of the line where members of the last top-level class should be inserted, or -1 when there is none. */
    static int insertionPoint(String code, String target) {
        if (code == null || code.isEmpty()) {
            return -1;
        }
        if (target.equals("python")) {
            var m = Pattern.compile("(?m)^class\\s+\\w+[^\\n]*:[ \\t]*$").matcher(code);
            int classLine = -1;
            while (m.find()) {
                classLine = m.end();
            }
            if (classLine < 0) {
                return -1;
            }
            var next = Pattern.compile("(?m)^[^\\s#\\n]").matcher(code);
            return next.find(classLine) ? next.start() : code.length();
        }
        if (target.equals("ruby")) {
            var m = Pattern.compile("(?m)^(?:class|module)\\s+\\w+").matcher(code);
            int start = -1;
            while (m.find()) {
                start = m.start();
            }
            if (start < 0) {
                return -1;
            }
            var end = Pattern.compile("(?m)^end[ \\t]*$").matcher(code);
            int last = -1;
            while (end.find(last < 0 ? start : last + 1)) {
                last = end.start();
            }
            return last;
        }
        if (!CLASS_BODY_TARGETS.contains(target) && !target.equals("rust")) {
            return -1;
        }
        int[] depth = depths(code);
        var m = TOP_LEVEL_TYPE.matcher(code);
        int open = -1;
        while (m.find()) {
            int brace = code.lastIndexOf('{', m.end() - 1);
            if (brace >= 0 && depth[brace] == 0) {
                open = brace;
            }
        }
        if (open < 0) {
            return -1;
        }
        for (int i = open + 1; i < code.length(); i++) {
            if (code.charAt(i) == '}' && depth[i] == 1 && !inCommentOrString(code, depth, i)) {
                return code.lastIndexOf('\n', i) + 1;
            }
        }
        return -1;
    }

    /** Splits a part at the first '}' that has no opening brace in the part: {members, code after it}. */
    private static String[] splitStrayClosers(String body) {
        int[] depth = depths(body);
        StringBuilder tail = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '}' && depth[i] == 0 && !inCommentOrString(body, depth, i)) {
                String rest = body.substring(i + 1);
                String[] more = splitStrayClosers(rest);
                tail.append(more[0]);
                if (!more[1].isBlank()) {
                    tail.append("\n\n").append(more[1]);
                }
                return new String[] {body.substring(0, i), tail.toString()};
            }
        }
        return new String[] {body, ""};
    }

    /** Python members must be indented; un-indented defs are indented and trailing top-level code split off. */
    private static String[] splitPythonMembers(String body) {
        String[] lines = body.split("\n", -1);
        boolean indentAll = false;
        for (String line : lines) {
            if (!line.isBlank() && !line.strip().startsWith("#")) {
                indentAll = !Character.isWhitespace(line.charAt(0))
                        && (line.startsWith("def ") || line.startsWith("async def ") || line.startsWith("@"));
                break;
            }
        }
        StringBuilder members = new StringBuilder();
        int i = 0;
        for (; i < lines.length; i++) {
            String line = lines[i];
            boolean topLevel = !line.isBlank() && !Character.isWhitespace(line.charAt(0));
            boolean memberStart = line.startsWith("def ") || line.startsWith("async def ") || line.startsWith("@")
                    || line.startsWith("#");
            if (topLevel && !(indentAll && memberStart)) {
                break;
            }
            members.append(indentAll && !line.isBlank() ? "    " + line : line).append('\n');
        }
        String tail = String.join("\n", java.util.Arrays.asList(lines).subList(i, lines.length));
        return new String[] {members.toString(), tail};
    }

    private static String dropLeadingBlankLines(String text) {
        return text.replaceFirst("^(?:[ \\t]*\\n)+", "");
    }

    private static final Pattern IMPORT_LINE = Pattern.compile(
            "^\\s*(?:import\\s|package\\s|using\\s+[\\w.]+\\s*;|use\\s+[\\w:\\\\{}, ]+;|#include\\s|require(?:_relative|_once)?[\\s(]|from\\s+\\S+\\s+import\\s).*");
    private static final Pattern TYPE_WRAPPER = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:(?:public|internal|private|open|final|abstract|sealed|data|static|partial)\\s+)*"
                    + "(?:class|object|struct|impl|module)\\s+\\w+[^{;]*\\{\\s*$");
    private static final Pattern PYTHON_WRAPPER = Pattern.compile("^class\\s+\\w+.*:\\s*$");
    private static final Pattern RUBY_WRAPPER = Pattern.compile("^(?:class|module)\\s+\\w+.*$");

    /** Moves leading import/package lines of a continuation part into {@code imports}. */
    private static String takeImports(String part, Set<String> imports) {
        String[] lines = part.split("\n", -1);
        int i = 0;
        while (i < lines.length && (lines[i].isBlank() || IMPORT_LINE.matcher(lines[i]).matches())) {
            if (!lines[i].isBlank() && !lines[i].strip().startsWith("package ")) {
                imports.add(lines[i].strip());
            }
            i++;
        }
        return String.join("\n", java.util.Arrays.asList(lines).subList(i, lines.length));
    }

    private static String addImports(String first, Set<String> imports) {
        List<String> lines = new ArrayList<>(List.of(first.split("\n", -1)));
        int lastImport = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (IMPORT_LINE.matcher(line).matches()) {
                lastImport = i;
                imports.remove(line.strip());
            } else if (!line.isBlank() && lastImport >= 0) {
                break;
            }
        }
        if (imports.isEmpty()) {
            return first;
        }
        lines.addAll(lastImport + 1, imports);
        return String.join("\n", lines);
    }

    /** Removes a class/type declaration the model repeated around continuation members, keeping what follows it. */
    private static String unwrap(String part, String target) {
        String[] lines = part.split("\n", -1);
        int first = 0;
        while (first < lines.length && (lines[first].isBlank() || lines[first].strip().startsWith("//")
                || lines[first].strip().startsWith("#") || lines[first].strip().startsWith("*")
                || lines[first].strip().startsWith("/*"))) {
            first++;
        }
        if (first >= lines.length) {
            return part;
        }
        String declaration = lines[first];
        if (target.equals("python")) {
            if (!PYTHON_WRAPPER.matcher(declaration).matches()) {
                return part;
            }
            return String.join("\n", java.util.Arrays.asList(lines).subList(first + 1, lines.length));
        }
        if (target.equals("ruby")) {
            if (!RUBY_WRAPPER.matcher(declaration).matches()) {
                return part;
            }
            for (int end = lines.length - 1; end > first; end--) {
                if (lines[end].equals("end")) {
                    List<String> kept = new ArrayList<>(java.util.Arrays.asList(lines).subList(first + 1, end));
                    kept.addAll(java.util.Arrays.asList(lines).subList(end + 1, lines.length));
                    return String.join("\n", kept);
                }
            }
            return part;
        }
        if (!TYPE_WRAPPER.matcher(declaration).matches()) {
            return part;
        }
        String body = String.join("\n", java.util.Arrays.asList(lines).subList(first + 1, lines.length));
        int[] depth = depths(body);
        for (int i = 0; i < body.length(); i++) {
            if (body.charAt(i) == '}' && depth[i] == 0 && !inCommentOrString(body, depth, i)) {
                return body.substring(0, i).stripTrailing() + body.substring(i + 1);
            }
        }
        return body;
    }

    /** Number of unclosed '{' outside strings and comments. */
    static int braceBalance(String code) {
        int[] depth = depths(code);
        if (code.isEmpty()) {
            return 0;
        }
        int last = code.length() - 1;
        int d = depth[last];
        char c = code.charAt(last);
        if (c == '{') {
            d++;
        } else if (c == '}') {
            d = Math.max(0, d - 1);
        }
        return d;
    }

    private static List<int[]> braceCuts(String code) {
        int[] depth = depths(code);
        List<int[]> cuts = new ArrayList<>();
        char lastSignificant = 0;
        int depthAfterLast = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\n') {
                if ((lastSignificant == '}' || lastSignificant == ';') && depthAfterLast <= 1) {
                    cuts.add(new int[] {i + 1, depthAfterLast});
                }
                continue;
            }
            if (!Character.isWhitespace(c) && !inCommentOrString(code, depth, i)) {
                lastSignificant = c;
                depthAfterLast = c == '{' ? depth[i] + 1 : c == '}' ? Math.max(0, depth[i] - 1) : depth[i];
            }
        }
        return cuts;
    }

    private static List<int[]> lineCuts(String code, String languageId) {
        Pattern pattern = languageId.equals("ruby") ? RUBY_CUT : PYTHON_CUT;
        List<int[]> cuts = new ArrayList<>();
        int pos = 0;
        boolean previousBlank = false;
        while (pos < code.length()) {
            int next = code.indexOf('\n', pos);
            int end = next < 0 ? code.length() : next + 1;
            String line = code.substring(pos, end);
            var m = pattern.matcher(line);
            if (pos > 0 && m.find()) {
                cuts.add(new int[] {pos, m.group(1).isEmpty() ? 0 : 1});
            } else if (pos > 0 && previousBlank && !line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
                cuts.add(new int[] {pos, 0});
            }
            previousBlank = line.isBlank();
            pos = end;
        }
        return cuts;
    }

    private static List<Chunk> pack(String code, List<int[]> cuts, int maxChars) {
        List<Chunk> chunks = new ArrayList<>();
        int start = 0;
        int lastGood = -1;
        int lastGoodDepth = 0;
        int startDepth = 0;
        int memberLimit = maxChars * OVERSIZED_MEMBER_FACTOR;
        for (int[] cut : cuts) {
            int pos = cut[0];
            if (pos <= start) {
                continue;
            }
            if (pos - start > maxChars && lastGood > start) {
                chunks.add(new Chunk(code.substring(start, lastGood), startDepth > 0));
                start = lastGood;
                startDepth = lastGoodDepth;
            }
            while (pos - start > memberLimit) {
                int hard = hardCut(code, start, maxChars);
                chunks.add(new Chunk(code.substring(start, hard), startDepth > 0));
                start = hard;
            }
            lastGood = pos;
            lastGoodDepth = cut[1];
        }
        if (code.length() - start > maxChars && lastGood > start) {
            chunks.add(new Chunk(code.substring(start, lastGood), startDepth > 0));
            start = lastGood;
            startDepth = lastGoodDepth;
        }
        while (code.length() - start > memberLimit) {
            int hard = hardCut(code, start, maxChars);
            chunks.add(new Chunk(code.substring(start, hard), startDepth > 0));
            start = hard;
        }
        if (start < code.length()) {
            chunks.add(new Chunk(code.substring(start), startDepth > 0));
        }
        return withoutEmptyParts(chunks);
    }

    /** Parts holding only blank lines or closing braces have nothing to convert; they are joined to a neighbour. */
    private static List<Chunk> withoutEmptyParts(List<Chunk> chunks) {
        List<Chunk> out = new ArrayList<>();
        String pending = "";
        for (Chunk chunk : chunks) {
            boolean empty = chunk.text().replaceAll("[\\s};]", "").isEmpty();
            if (empty && !out.isEmpty()) {
                Chunk last = out.remove(out.size() - 1);
                out.add(new Chunk(last.text() + chunk.text(), last.continuesType()));
            } else if (empty) {
                pending += chunk.text();
            } else {
                out.add(new Chunk(pending + chunk.text(), pending.isEmpty() ? chunk.continuesType() : false));
                pending = "";
            }
        }
        if (out.isEmpty()) {
            return List.of(new Chunk(pending, false));
        }
        return out;
    }

    /** Last line break before the limit; a member longer than the limit is split between lines. */
    private static int hardCut(String code, int start, int maxChars) {
        int limit = Math.min(code.length(), start + maxChars);
        int newline = code.lastIndexOf('\n', limit - 1);
        return newline > start ? newline + 1 : limit;
    }

    /** depth[i] = brace depth just before character i, ignoring braces in strings and comments. */
    private static int[] depths(String code) {
        int[] depth = new int[code.length()];
        int d = 0;
        int i = 0;
        while (i < code.length()) {
            char c = code.charAt(i);
            if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '/') {
                int end = code.indexOf('\n', i);
                end = end < 0 ? code.length() : end;
                fill(depth, i, end, d);
                i = end;
                continue;
            }
            if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '*') {
                int end = code.indexOf("*/", i + 2);
                end = end < 0 ? code.length() : end + 2;
                fill(depth, i, end, d);
                i = end;
                continue;
            }
            if (c == '"' && code.startsWith("\"\"\"", i)) {
                int end = code.indexOf("\"\"\"", i + 3);
                end = end < 0 ? code.length() : end + 3;
                fill(depth, i, end, d);
                i = end;
                continue;
            }
            if (c == '"' || c == '\'' || c == '`') {
                int end = i + 1;
                while (end < code.length() && code.charAt(end) != c && code.charAt(end) != '\n') {
                    end += code.charAt(end) == '\\' ? 2 : 1;
                }
                end = Math.min(code.length(), end + 1);
                fill(depth, i, end, d);
                i = end;
                continue;
            }
            depth[i] = d;
            if (c == '{') {
                d++;
            } else if (c == '}') {
                d = Math.max(0, d - 1);
            }
            i++;
        }
        return depth;
    }

    private static boolean inCommentOrString(String code, int[] depth, int i) {
        char c = code.charAt(i);
        if (c != '}' && c != ';' && c != '{') {
            return false;
        }
        int before = depth[i];
        int after = i + 1 < code.length() ? depth[i + 1] : c == '{' ? before + 1 : Math.max(0, before - 1);
        if (c == '{') {
            return after != before + 1;
        }
        if (c == '}') {
            return before == 0 ? isMasked(code, i) : after != before - 1;
        }
        return isMasked(code, i);
    }

    /** True when position i is inside a string or comment (re-scans the line, good enough for ';'). */
    private static boolean isMasked(String code, int i) {
        int lineStart = code.lastIndexOf('\n', i - 1) + 1;
        String line = code.substring(lineStart, i);
        int comment = line.indexOf("//");
        if (comment >= 0) {
            return true;
        }
        int quotes = 0;
        for (int k = 0; k < line.length(); k++) {
            if (line.charAt(k) == '"' && (k == 0 || line.charAt(k - 1) != '\\')) {
                quotes++;
            }
        }
        return quotes % 2 == 1;
    }

    private static void fill(int[] depth, int from, int to, int value) {
        for (int k = from; k < to && k < depth.length; k++) {
            depth[k] = value;
        }
    }

    private static String trim(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars) + "\n...";
    }
}
