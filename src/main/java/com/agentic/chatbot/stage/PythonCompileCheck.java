package com.agentic.chatbot.stage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Syntax-checks generated Python with {@code python -m py_compile}, when a Python interpreter is on the PATH.
 */
public final class PythonCompileCheck {

    private static final long TIMEOUT_SECONDS = 30;
    private static volatile String interpreter;

    private PythonCompileCheck() {
    }

    public static JavaCompileCheck.Result compile(Map<String, String> sources) {
        String python = interpreter();
        if (python == null) {
            return new JavaCompileCheck.Result(false, true, List.of());
        }
        Path dir = null;
        try {
            dir = Files.createTempDirectory("bot-stage-py");
            List<String> command = new ArrayList<>(List.of(python, "-m", "py_compile"));
            for (Map.Entry<String, String> e : sources.entrySet()) {
                Path file = dir.resolve(StageFiles.safeRelative(e.getKey()));
                Files.createDirectories(file.getParent());
                Files.writeString(file, e.getValue(), StandardCharsets.UTF_8);
                command.add(dir.relativize(file).toString());
            }
            Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new JavaCompileCheck.Result(false, false, List.of("py_compile timed out"));
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            if (process.exitValue() == 0) {
                return new JavaCompileCheck.Result(true, false, List.of());
            }
            List<String> errors = out.lines().filter(l -> !l.isBlank()).map(String::strip)
                    .filter(l -> l.contains("Error") || l.startsWith("File")).limit(6).toList();
            return new JavaCompileCheck.Result(false, false, errors.isEmpty() ? List.of(out) : errors);
        } catch (IOException e) {
            return new JavaCompileCheck.Result(false, true, List.of());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new JavaCompileCheck.Result(false, true, List.of());
        } finally {
            deleteQuietly(dir);
        }
    }

    public static String summary(JavaCompileCheck.Result result, int files) {
        if (result.skipped()) {
            return "Syntax check skipped: no Python interpreter on BOT's PATH. Run python -m py_compile or pytest yourself.";
        }
        if (result.ok()) {
            return "Syntax check: all " + files + " Python file" + (files == 1 ? "" : "s") + " compile with python -m py_compile.";
        }
        return "Syntax check: " + result.errors().size() + " error(s). First: " + result.errors().get(0);
    }

    private static String interpreter() {
        if (interpreter != null) {
            return interpreter.isEmpty() ? null : interpreter;
        }
        for (String candidate : List.of("python", "python3", "py")) {
            try {
                Process p = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
                if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0
                        && new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).startsWith("Python 3")) {
                    interpreter = candidate;
                    return candidate;
                }
            } catch (IOException e) {
                // not installed under this name
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        interpreter = "";
        return null;
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // temp folder; the OS cleans it up eventually
        }
    }
}
