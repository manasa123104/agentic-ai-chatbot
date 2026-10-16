package com.agentic.chatbot.stage;

import com.agentic.chatbot.service.GitService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipFile;

/**
 * Brings user stories into the Business Analyst stage from uploaded files, a folder, a ZIP, a GitHub repository or (host
 * only) a folder on the BOT machine. The files are stored under {@code generated-app/stages/<run>/stories-input/} and
 * their text is combined for the stage's input box.
 */
@Service
public class StoryImportService {

    /** Result of an import: the run that holds the files, what was read and the combined text for the input box. */
    public record Imported(String runId, List<String> files, List<String> skipped, String content, boolean truncated,
                           int totalFiles) {
    }

    static final String DIR = "stories-input";
    private static final Set<String> TEXT_EXTENSIONS = Set.of("md", "markdown", "txt", "csv", "tsv", "feature", "story",
            "adoc", "rst", "docx", "json");
    private static final Set<String> SKIP_DIRS = Set.of(".git", ".github", "node_modules", "target", "build", "dist", ".idea",
            ".vscode", "__pycache__", "vendor", ".venv", "venv");
    private static final int MAX_FILES = 1000;
    private static final long MAX_ZIP_BYTES = 100L * 1024 * 1024;
    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    private final StageService stages;
    private final GitService git;

    public StoryImportService(StageService stages, GitService git) {
        this.stages = stages;
        this.git = git;
    }

    /** Uploaded files keep their relative names (folder uploads); ZIP files are extracted. */
    public Imported fromUploads(List<MultipartFile> files, String runId) throws IOException {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new IllegalArgumentException("Choose story files, a folder or a ZIP to upload.");
        }
        Path target = freshTarget(runId);
        try {
            for (MultipartFile file : files) {
                if (file.isEmpty()) {
                    continue;
                }
                String name = file.getOriginalFilename() == null ? "stories.txt" : file.getOriginalFilename();
                try (InputStream in = file.getInputStream()) {
                    if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        unzip(in, target);
                    } else {
                        Path dest = safeResolve(target, name);
                        Files.createDirectories(dest.getParent());
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            GitService.deleteQuietly(target);
            throw e;
        }
        return read(runIdOf(target), target);
    }

    /** Clones a repository (optionally with the visitor's own token for private repos) and keeps one sub-folder. */
    public Imported fromGit(String repoUrl, String branch, String subfolder, String userToken, String runId,
                            boolean httpsOnly) throws IOException {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw new IllegalArgumentException("Enter a GitHub repository URL such as https://github.com/owner/repo.");
        }
        if (httpsOnly && !repoUrl.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalArgumentException("Only https:// repository URLs are available here - use a GitHub https URL or upload.");
        }
        String folder = cleanFolder(subfolder);
        Path target = freshTarget(runId);
        Path clone = target.resolveSibling(DIR + "-clone");
        try {
            GitService.deleteQuietly(clone);
            git.cloneRepo(repoUrl.trim(), branch, clone, userToken);
            Path from = folder.isEmpty() ? clone : clone.resolve(folder).normalize();
            if (!from.startsWith(clone) || !Files.isDirectory(from)) {
                throw new IllegalArgumentException("Sub-folder \"" + folder + "\" does not exist in the repository.");
            }
            copyTexts(from, target);
        } catch (IOException | RuntimeException e) {
            GitService.deleteQuietly(target);
            throw e;
        } finally {
            GitService.deleteQuietly(clone);
        }
        return read(runIdOf(target), target);
    }

    /** A folder on the BOT machine; the controller only allows this for the host. */
    public Imported fromLocal(String folder, String runId) throws IOException {
        Path from = Path.of(folder == null ? "" : folder.trim().replaceAll("^\"|\"$", "")).toAbsolutePath().normalize();
        if (folder == null || folder.isBlank() || !Files.isDirectory(from)) {
            throw new IllegalArgumentException("Folder not found on the BOT machine: " + folder);
        }
        Path target = freshTarget(runId);
        try {
            copyTexts(from, target);
        } catch (IOException | RuntimeException e) {
            GitService.deleteQuietly(target);
            throw e;
        }
        return read(runIdOf(target), target);
    }

    // ---------------------------------------------------------------- reading

    /** Reads every supported file under {@code target} into one text, capped to the stage's input limit. */
    Imported read(String runId, Path target) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(target)) {
            walk.filter(Files::isRegularFile).sorted(Comparator.comparing(p -> target.relativize(p).toString().toLowerCase(Locale.ROOT)))
                    .forEach(files::add);
        }
        int limit = StageService.MAX_INPUT_CHARS - 5_000;
        StringBuilder text = new StringBuilder();
        List<String> used = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        boolean truncated = false;
        int supported = 0;
        for (Path file : files) {
            String rel = target.relativize(file).toString().replace('\\', '/');
            String ext = extension(rel);
            if (!TEXT_EXTENSIONS.contains(ext)) {
                skipped.add(rel + " (unsupported type)");
                continue;
            }
            supported++;
            if (Files.size(file) > MAX_FILE_BYTES) {
                skipped.add(rel + " (larger than " + MAX_FILE_BYTES / 1024 / 1024 + " MB)");
                continue;
            }
            String content = normalise(ext.equals("docx") ? docxText(file) : Files.readString(file, StandardCharsets.UTF_8)).strip();
            if (content.isEmpty()) {
                skipped.add(rel + " (empty)");
                continue;
            }
            if (truncated || text.length() + content.length() + rel.length() + 20 > limit) {
                truncated = true;
                skipped.add(rel + " (over the " + limit + " character input limit; still saved in the run folder)");
                continue;
            }
            text.append("<!-- source: ").append(rel).append(" -->\n").append(content).append("\n\n");
            used.add(rel);
        }
        if (used.isEmpty()) {
            GitService.deleteQuietly(target);
            throw new IllegalArgumentException("No story files found. Supported types: .md, .txt, .docx, .csv, .feature, .json "
                    + "(or a ZIP / folder of them).");
        }
        return new Imported(runId, used, skipped, text.toString().strip() + "\n", truncated, supported);
    }

    static String docxText(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            if (entry == null) {
                return "";
            }
            String xml = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            return xml.replaceAll("</w:p>", "\n").replaceAll("<w:tab/>", "\t").replaceAll("<w:br/>", "\n")
                    .replaceAll("<[^>]+>", "")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                    .replace("&amp;", "&");
        } catch (java.util.zip.ZipException e) {
            throw new IllegalArgumentException(file.getFileName() + " is not a valid .docx file.");
        }
    }

    private static String normalise(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "");
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    // ---------------------------------------------------------------- files

    /** An empty stories-input folder in the run (a new run when {@code runId} is blank). */
    private Path freshTarget(String runId) throws IOException {
        String id = stages.resolveRunId(runId);
        Path target = stages.runDir(id).resolve(DIR);
        GitService.deleteQuietly(target);
        Files.createDirectories(target);
        return target;
    }

    private static String runIdOf(Path target) {
        return target.getParent().getFileName().toString();
    }

    private static String cleanFolder(String subfolder) {
        String folder = subfolder == null ? "" : subfolder.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
        if (folder.contains("..")) {
            throw new IllegalArgumentException("Sub-folder must be inside the repository.");
        }
        return folder;
    }

    private static void copyTexts(Path from, Path to) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(from, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes a) {
                return !dir.equals(from) && SKIP_DIRS.contains(dir.getFileName().toString())
                        ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes a) {
                if (a.isRegularFile() && TEXT_EXTENSIONS.contains(extension(file.getFileName().toString()))
                        && files.size() < MAX_FILES) {
                    files.add(file);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        for (Path file : files) {
            Path dest = safeResolve(to, from.relativize(file).toString());
            Files.createDirectories(dest.getParent());
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void unzip(InputStream in, Path destination) throws IOException {
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entry.isDirectory() || SKIP_DIRS.stream().anyMatch(d -> entryName.contains(d + "/"))) {
                    continue;
                }
                if (++count > MAX_FILES) {
                    throw new IllegalArgumentException("The ZIP has more than " + MAX_FILES + " files.");
                }
                Path dest = safeResolve(destination, entryName);
                Files.createDirectories(dest.getParent());
                total += Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                if (total > MAX_ZIP_BYTES) {
                    throw new IllegalArgumentException("The ZIP expands to more than " + MAX_ZIP_BYTES / 1024 / 1024 + " MB.");
                }
            }
        }
    }

    /** Resolves a user-supplied relative path, rejecting anything that escapes {@code base}. */
    static Path safeResolve(Path base, String relative) {
        String cleaned = relative == null ? "" : relative.replace('\\', '/').replaceAll("^[A-Za-z]:", "").replaceAll("^/+", "");
        Path root = base.toAbsolutePath().normalize();
        Path resolved = root.resolve(cleaned).normalize();
        if (cleaned.isEmpty() || !resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("Invalid file path: " + relative);
        }
        return resolved;
    }

    /** Pattern used by tests to count the story headings in a combined text. */
    static final Pattern STORY_HEADING = Pattern.compile("(?im)^\\s*(?:#{1,4}\\s*story\\b|as an?\\s)");
}
