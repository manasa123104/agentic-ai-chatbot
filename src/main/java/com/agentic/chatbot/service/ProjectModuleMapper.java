package com.agentic.chatbot.service;

import com.agentic.chatbot.model.ProgrammingLanguage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Plans how a multi-file project maps onto the target language: where each converted file goes, which package or
 * namespace it uses, how other files import it, which files it depends on, and the package files (such as
 * {@code __init__.py}, {@code go.mod} or {@code mod.rs}) needed so the converted modules link together.
 */
public final class ProjectModuleMapper {

    public record SourceFile(String path, String content, ProgrammingLanguage language) {
    }

    /**
     * @param namespace    package / namespace / module path in the target ("" when the language has none)
     * @param symbols      types and functions declared in the source file
     * @param dependencies source paths of project files this file uses
     */
    public record Module(SourceFile source, String targetPath, String namespace, List<String> symbols,
                         List<String> dependencies) {

        public String primarySymbol() {
            return symbols.isEmpty() ? baseName(source.path()) : symbols.get(0);
        }
    }

    private static final Pattern TYPE_DECL = Pattern.compile(
            "\\b(?:class|interface|enum|record|struct|trait|object|protocol|module)\\s+([A-Z]\\w*)");
    private static final Pattern FUNCTION_DECL = Pattern.compile(
            "(?m)^(?:export\\s+)?(?:pub\\s+)?(?:async\\s+)?(?:def|func|fn|function|fun|sub)\\s+(\\w{4,})");
    private static final Pattern JAVA_PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern CSHARP_NAMESPACE = Pattern.compile("(?m)^\\s*namespace\\s+([\\w.]+)");
    static final Pattern MAIN_ENTRY = Pattern.compile(
            "\\bstatic\\s+void\\s+[Mm]ain\\s*\\(|\\bfun\\s+main\\s*\\(|\\bfunc\\s+main\\s*\\(|\\bfn\\s+main\\s*\\(|"
                    + "\\bint\\s+main\\s*\\(|__name__\\s*==\\s*['\"]__main__['\"]");
    private static final Pattern SIGNATURE_LINE = Pattern.compile(
            "^\\s*(?:@\\w+.*|(?:export\\s+|pub(?:\\(crate\\))?\\s+|public\\s+|internal\\s+|open\\s+|abstract\\s+|"
                    + "final\\s+|static\\s+|data\\s+|sealed\\s+)*(?:class|interface|enum|record|struct|trait|object|"
                    + "protocol|def|func|fn|function|fun|module|type|namespace|package)\\b.*"
                    + "|^\\s*(?:public|protected|internal)\\s+[\\w<>\\[\\],.? ]+\\s+\\w+\\s*\\(.*)$");
    private static final List<String> SOURCE_ROOTS = List.of(
            "src/main/java/", "src/main/kotlin/", "src/main/scala/", "src/main/", "src/test/java/", "src/test/kotlin/",
            "src/test/", "src/", "lib/", "app/");

    private final ProgrammingLanguage target;

    public ProjectModuleMapper(ProgrammingLanguage target) {
        this.target = target;
    }

    /** Builds the module plan, ordered so that every file comes after the project files it depends on. */
    public List<Module> plan(List<SourceFile> files) {
        Map<String, List<String>> symbols = new LinkedHashMap<>();
        for (SourceFile file : files) {
            symbols.put(file.path(), declaredSymbols(file));
        }
        Map<String, Module> modules = new LinkedHashMap<>();
        for (SourceFile file : files) {
            List<String> deps = new ArrayList<>();
            String body = stripComments(file.content());
            for (SourceFile other : files) {
                if (other == file) {
                    continue;
                }
                for (String symbol : symbols.get(other.path())) {
                    if (Pattern.compile("\\b" + Pattern.quote(symbol) + "\\b").matcher(body).find()) {
                        deps.add(other.path());
                        break;
                    }
                }
            }
            modules.put(file.path(), new Module(file, targetPath(file), namespaceFor(file),
                    symbols.get(file.path()), deps));
        }
        return dependencyOrder(modules);
    }

    /** Instructions for the model about this file's new location and how to import the files it uses. */
    public String context(Module module, Map<String, Module> bySourcePath, Map<String, String> convertedBySourcePath) {
        StringBuilder sb = new StringBuilder()
                .append("This file is part of a project being converted file by file. It is ")
                .append(module.source().path()).append(" and will be saved as ").append(module.targetPath()).append(".\n");
        String declaration = namespaceDeclaration(module);
        if (!declaration.isEmpty()) {
            sb.append("Start it with: ").append(declaration).append('\n');
        }
        String note = languageNote();
        if (!note.isEmpty()) {
            sb.append(note).append('\n');
        }
        if (module.dependencies().isEmpty()) {
            return sb.toString();
        }
        sb.append("It uses these project files, which are converted separately; reference them with the import shown, "
                + "and do not redefine them:\n");
        for (String depPath : module.dependencies()) {
            Module dep = bySourcePath.get(depPath);
            if (dep == null) {
                continue;
            }
            sb.append("- ").append(String.join(", ", dep.symbols().isEmpty() ? List.of(dep.primarySymbol()) : dep.symbols()))
                    .append(" -> ").append(dep.targetPath());
            String importLine = importStatement(dep, module);
            if (!importLine.isEmpty()) {
                sb.append("; import with: ").append(importLine);
            }
            sb.append('\n');
            String converted = convertedBySourcePath.get(depPath);
            if (converted != null) {
                String signatures = signatures(converted);
                if (!signatures.isEmpty()) {
                    sb.append("  Converted declarations:\n").append(signatures.indent(4));
                }
            }
        }
        return sb.toString();
    }

    /** Extra files the target needs so the converted modules resolve each other (path -> content). */
    public Map<String, String> packageFiles(List<Module> modules) {
        Map<String, String> files = new LinkedHashMap<>();
        Set<String> dirs = new TreeSet<>();
        for (Module module : modules) {
            String dir = parent(module.targetPath());
            while (!dir.isEmpty()) {
                dirs.add(dir);
                dir = parent(dir);
            }
        }
        switch (target.id()) {
            case "python" -> dirs.forEach(dir -> files.put(dir + "/__init__.py", ""));
            case "go" -> files.put("go.mod", "module converted\n\ngo 1.21\n");
            case "rust" -> {
                files.put("Cargo.toml", "[package]\nname = \"converted\"\nversion = \"0.1.0\"\nedition = \"2021\"\n");
                Map<String, Set<String>> children = new LinkedHashMap<>();
                for (Module module : modules) {
                    String path = module.targetPath();
                    String parent = parent(path);
                    children.computeIfAbsent(parent, k -> new TreeSet<>()).add(stripExtension(fileName(path)));
                    while (!parent.isEmpty()) {
                        String up = parent(parent);
                        children.computeIfAbsent(up, k -> new TreeSet<>()).add(fileName(parent));
                        parent = up;
                    }
                }
                children.forEach((dir, names) -> {
                    names.remove("main");
                    names.remove("lib");
                    StringBuilder mods = new StringBuilder();
                    names.forEach(name -> mods.append("pub mod ").append(name).append(";\n"));
                    files.put(dir.equals("src") ? "src/lib.rs" : dir + "/mod.rs", mods.toString());
                });
                files.remove("/mod.rs");
            }
            case "typescript" -> files.put("tsconfig.json", "{\n  \"compilerOptions\": {\n    \"target\": \"ES2022\",\n"
                    + "    \"module\": \"NodeNext\",\n    \"moduleResolution\": \"NodeNext\",\n    \"strict\": true,\n"
                    + "    \"outDir\": \"dist\"\n  }\n}\n");
            case "javascript" -> files.put("package.json", "{\n  \"name\": \"converted\",\n  \"type\": \"module\"\n}\n");
            default -> {
            }
        }
        return files;
    }

    String targetPath(SourceFile file) {
        String relative = file.path().replace('\\', '/');
        boolean test = relative.contains("src/test/") || relative.startsWith("test/") || relative.startsWith("tests/");
        for (String root : SOURCE_ROOTS) {
            if (relative.startsWith(root)) {
                relative = relative.substring(root.length());
                break;
            }
        }
        String dir = parent(relative);
        String base = baseName(relative);
        boolean entry = MAIN_ENTRY.matcher(file.content()).find();
        String ext = target.extension();
        return switch (target.id()) {
            case "java" -> join(test ? "src/test/java" : "src/main/java", lower(dir), pascal(base) + ".java");
            case "kotlin" -> join(test ? "src/test/kotlin" : "src/main/kotlin", lower(dir), pascal(base) + ".kt");
            case "csharp" -> join(pascalPath(dir), pascal(base) + ".cs");
            case "python" -> join(snakePath(dir), snake(base) + ".py");
            case "ruby" -> join("lib", snakePath(dir), snake(base) + ".rb");
            case "go" -> join(lower(dir), snake(base) + ".go");
            case "rust" -> entry ? "src/main.rs" : join("src", snakePath(dir), snake(base) + ".rs");
            case "php" -> join("src", pascalPath(dir), pascal(base) + ".php");
            case "swift" -> join("Sources/Converted", dir, pascal(base) + ".swift");
            case "cpp" -> join("src", dir, snake(base) + (entry ? ".cpp" : ".hpp"));
            case "c" -> join("src", dir, snake(base) + (entry ? ".c" : ".h"));
            case "cobol" -> base.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "") + ".cbl";
            case "plsql" -> join("sql", snake(base) + ".sql");
            case "typescript", "javascript" -> join("src", dir, base + "." + ext);
            default -> join(dir, base + "." + ext);
        };
    }

    private String namespaceFor(SourceFile file) {
        String dir = parent(targetPath(file));
        return switch (target.id()) {
            case "java" -> dir.replaceFirst("^src/(main|test)/java/?", "").replace('/', '.');
            case "kotlin" -> dir.replaceFirst("^src/(main|test)/kotlin/?", "").replace('/', '.');
            case "csharp" -> dir.isEmpty() ? "Converted" : dir.replace('/', '.');
            case "php" -> "App" + (dir.equals("src") ? "" : "\\" + dir.replaceFirst("^src/", "").replace('/', '\\'));
            case "python" -> dir.replace('/', '.');
            case "go" -> dir.isEmpty() ? "main" : fileName(dir);
            case "rust" -> dir.replaceFirst("^src/?", "").replace('/', ':').replace(":", "::");
            default -> "";
        };
    }

    private String namespaceDeclaration(Module module) {
        String ns = module.namespace();
        return switch (target.id()) {
            case "java" -> ns.isEmpty() ? "" : "package " + ns + ";";
            case "kotlin" -> ns.isEmpty() ? "" : "package " + ns;
            case "csharp" -> "namespace " + ns + ";";
            case "php" -> "<?php namespace " + ns + ";";
            case "go" -> "package " + (MAIN_ENTRY.matcher(module.source().content()).find() ? "main" : ns);
            default -> "";
        };
    }

    private String languageNote() {
        return switch (target.id()) {
            case "go" -> "The Go module is named \"converted\"; each directory is one package. Capitalize names other "
                    + "packages use so they are exported.";
            case "rust" -> "Mark items used by other modules as pub. Module files (mod.rs, lib.rs) are generated for you.";
            case "python" -> "Packages get __init__.py files automatically. Use absolute imports from the project root.";
            case "cpp", "c" -> "Files without main are headers: add include guards and keep definitions inline.";
            case "javascript", "typescript" -> "Use ES modules: export what other files import.";
            default -> "";
        };
    }

    String importStatement(Module dep, Module importer) {
        String symbol = dep.primarySymbol();
        String names = String.join(", ", dep.symbols().isEmpty() ? List.of(symbol) : dep.symbols());
        return switch (target.id()) {
            case "java", "kotlin" -> dep.namespace().equals(importer.namespace()) ? "(same package, no import needed)"
                    : "import " + (dep.namespace().isEmpty() ? "" : dep.namespace() + ".") + symbol
                            + (target.id().equals("java") ? ";" : "");
            case "csharp" -> dep.namespace().equals(importer.namespace()) ? "(same namespace, no using needed)"
                    : "using " + dep.namespace() + ";";
            case "python" -> "from " + stripExtension(dep.targetPath()).replace('/', '.') + " import " + names;
            case "javascript" -> "import { " + names + " } from '" + relative(importer, dep, true) + "';";
            case "typescript" -> "import { " + names + " } from '" + relative(importer, dep, false) + "';";
            case "ruby" -> "require_relative '" + relative(importer, dep, false) + "'";
            case "go" -> parent(dep.targetPath()).equals(parent(importer.targetPath())) ? "(same package, no import needed)"
                    : "import \"converted/" + parent(dep.targetPath()) + "\"";
            case "rust" -> "use " + (importer.targetPath().equals("src/main.rs") ? "converted::" : "crate::")
                    + stripExtension(dep.targetPath()).replaceFirst("^src/", "").replace("/", "::") + "::" + symbol + ";";
            case "php" -> "require_once __DIR__ . '/" + relative(importer, dep, true).replaceFirst("^\\./", "")
                    + "'; use " + dep.namespace() + "\\" + symbol + ";";
            case "cpp", "c" -> "#include \"" + relative(importer, dep, true).replaceFirst("^\\./", "") + "\"";
            case "swift" -> "(same module, no import needed)";
            case "cobol" -> "CALL '" + stripExtension(dep.targetPath()) + "'";
            default -> "";
        };
    }

    static String signatures(String converted) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : converted.split("\n")) {
            if (count >= 25) {
                break;
            }
            if (SIGNATURE_LINE.matcher(line).matches() && !line.strip().startsWith("//")) {
                String sig = line.strip();
                int brace = sig.indexOf('{');
                sb.append(brace > 0 ? sig.substring(0, brace).strip() : sig).append('\n');
                count++;
            }
        }
        return sb.toString().strip();
    }

    static List<String> declaredSymbols(SourceFile file) {
        Set<String> names = new LinkedHashSet<>();
        String body = stripComments(file.content());
        Matcher types = TYPE_DECL.matcher(body);
        while (types.find()) {
            names.add(types.group(1));
        }
        if (names.isEmpty() || !Set.of("java", "csharp", "kotlin", "swift").contains(
                file.language() == null ? "" : file.language().id())) {
            Matcher functions = FUNCTION_DECL.matcher(body);
            while (functions.find() && names.size() < 30) {
                String name = functions.group(1);
                if (!name.equals("main") && !name.equals("__init__")) {
                    names.add(name);
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static List<Module> dependencyOrder(Map<String, Module> modules) {
        List<Module> ordered = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String path : modules.keySet()) {
            visit(path, modules, done, visiting, ordered);
        }
        return ordered;
    }

    private static void visit(String path, Map<String, Module> modules, Set<String> done, Set<String> visiting,
                              List<Module> ordered) {
        if (done.contains(path) || visiting.contains(path)) {
            return;
        }
        visiting.add(path);
        for (String dep : modules.get(path).dependencies()) {
            visit(dep, modules, done, visiting, ordered);
        }
        visiting.remove(path);
        done.add(path);
        ordered.add(modules.get(path));
    }

    private String relative(Module from, Module to, boolean keepExtension) {
        List<String> fromDir = segments(parent(from.targetPath()));
        List<String> toPath = segments(to.targetPath());
        int common = 0;
        while (common < fromDir.size() && common < toPath.size() - 1 && fromDir.get(common).equals(toPath.get(common))) {
            common++;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = common; i < fromDir.size(); i++) {
            sb.append("../");
        }
        if (sb.isEmpty()) {
            sb.append("./");
        }
        String rest = String.join("/", toPath.subList(common, toPath.size()));
        return sb + (keepExtension ? rest : stripExtension(rest));
    }

    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ").replaceAll("(?m)^\\s*#(?!include).*$", " ");
    }

    private static List<String> segments(String path) {
        return path.isEmpty() ? List.of() : Arrays.asList(path.split("/"));
    }

    static String parent(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    private static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static String baseName(String path) {
        return stripExtension(fileName(path.replace('\\', '/')));
    }

    private static String stripExtension(String path) {
        int dot = path.lastIndexOf('.');
        return dot > path.lastIndexOf('/') ? path.substring(0, dot) : path;
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(part);
        }
        return sb.toString();
    }

    static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_").toLowerCase(Locale.ROOT);
    }

    static String pascal(String name) {
        StringBuilder sb = new StringBuilder();
        for (String part : name.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.isEmpty() ? "Module" : sb.toString();
    }

    private static String snakePath(String dir) {
        return dir.isEmpty() ? "" : String.join("/", segments(dir).stream().map(ProjectModuleMapper::snake).toList());
    }

    private static String pascalPath(String dir) {
        return dir.isEmpty() ? "" : String.join("/", segments(dir).stream().map(ProjectModuleMapper::pascal).toList());
    }

    private static String lower(String dir) {
        return dir.toLowerCase(Locale.ROOT);
    }
}
