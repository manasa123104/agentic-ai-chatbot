package com.agentic.chatbot.stage;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compiles generated Java in memory with the JDK's javac, so BOT can tell the user whether the code compiles.
 */
public final class JavaCompileCheck {

    /** {@code skipped} is true when no compiler is available (BOT running on a JRE). */
    public record Result(boolean ok, boolean skipped, List<String> errors) {

        public String summary(int files) {
            if (skipped) {
                return "Compile check skipped: no Java compiler available to BOT.";
            }
            if (ok) {
                return "Compile check: all " + files + " Java file" + (files == 1 ? "" : "s") + " compile with javac 17.";
            }
            return "Compile check: " + errors.size() + " error(s). First: " + errors.get(0);
        }
    }

    private JavaCompileCheck() {
    }

    /**
     * @param sources   relative path to Java source
     * @param classpath extra jars or folders (may be empty)
     */
    public static Result compile(Map<String, String> sources, List<Path> classpath) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new Result(false, true, List.of());
        }
        if (sources.isEmpty()) {
            return new Result(false, false, List.of("No Java sources found."));
        }
        Path out = null;
        try {
            out = Files.createTempDirectory("bot-stage-javac");
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            List<JavaFileObject> units = new ArrayList<>();
            sources.forEach((path, code) -> units.add(new Source(path, code)));
            List<String> options = new ArrayList<>(List.of("--release", "17", "-proc:none", "-nowarn",
                    "-encoding", "UTF-8", "-d", out.toString()));
            if (!classpath.isEmpty()) {
                options.add("-classpath");
                options.add(classpath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator)));
            }
            try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                boolean ok = compiler.getTask(null, fm, diagnostics, options, null, units).call();
                List<String> errors = diagnostics.getDiagnostics().stream()
                        .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                        .map(JavaCompileCheck::describe)
                        .toList();
                return new Result(ok && errors.isEmpty(), false, errors);
            }
        } catch (Exception e) {
            return new Result(false, false, List.of("javac failed: " + e.getMessage()));
        } finally {
            deleteQuietly(out);
        }
    }

    private static String describe(Diagnostic<? extends JavaFileObject> d) {
        String file = d.getSource() == null ? "" : ((Source) d.getSource()).path;
        return file + ":" + d.getLineNumber() + ": " + d.getMessage(Locale.ROOT).lines().findFirst().orElse("");
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (Exception ignored) {
            // temp folder; the OS cleans it up eventually
        }
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String path;
        private final String code;

        Source(String path, String code) {
            super(URI.create("string:///" + path.replace('\\', '/').replace(" ", "_")), Kind.SOURCE);
            this.path = path;
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
