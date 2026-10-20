package com.agentic.chatbot.service;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.ConversionJob;
import com.agentic.chatbot.model.ConversionJob.FileResult;
import com.agentic.chatbot.model.LanguageCatalog;
import com.agentic.chatbot.model.ProgrammingLanguage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Converts a whole project (Git repository, local folder, uploaded files or ZIP) file by file in the background.
 * Files are converted dependencies-first so each one can import the already-converted modules it uses; results are
 * saved under {@code data/conversion-jobs/<id>/output} where they can be viewed, edited, downloaded or pushed to Git.
 */
@Service
public class RepositoryConversionService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryConversionService.class);

    static final Set<String> SKIP_DIRS = Set.of(".git", ".svn", ".hg", "target", "build", "out", "bin", "obj",
            "node_modules", ".idea", ".vscode", ".gradle", "dist", "__pycache__", "vendor", ".venv", "venv", ".next");
    static final Set<String> BUILD_FILES = Set.of("pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle",
            "settings.gradle.kts", "gradlew", "gradlew.bat", "mvnw", "mvnw.cmd", "package.json", "package-lock.json",
            "yarn.lock", "tsconfig.json", "go.mod", "go.sum", "cargo.toml", "cargo.lock", "requirements.txt",
            "pyproject.toml", "setup.py", "setup.cfg", "composer.json", "composer.lock", "gemfile", "gemfile.lock");
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    public record Options(String sourceLanguage, String targetLanguage, String instructions, boolean addComments,
                          boolean explainLogic) {
    }

    private final CodeConversionService converter;
    private final GitService git;
    private final Path jobsDir;
    private final int maxFiles;
    private final long maxFileBytes;
    private final boolean allowLocalPaths;
    private final ObjectMapper json = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final Map<String, ConversionJob> jobs = new ConcurrentHashMap<>();
    private ChatHistoryService history;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "repo-converter");
        t.setDaemon(true);
        return t;
    });

    public RepositoryConversionService(CodeConversionService converter, GitService git,
                                       @Value("${agentic.converter.jobs-dir:data/conversion-jobs}") String jobsDir,
                                       @Value("${agentic.converter.max-files:300}") int maxFiles,
                                       @Value("${agentic.converter.max-file-kb:2048}") int maxFileKb,
                                       @Value("${agentic.converter.allow-local-paths:true}") boolean allowLocalPaths) {
        this.converter = converter;
        this.git = git;
        this.jobsDir = Paths.get(jobsDir).toAbsolutePath().normalize();
        this.maxFiles = maxFiles;
        this.maxFileBytes = maxFileKb * 1024L;
        this.allowLocalPaths = allowLocalPaths;
    }

    @Autowired(required = false)
    void setChatHistoryService(ChatHistoryService history) {
        this.history = history;
    }

    @PostConstruct
    void loadJobs() {
        if (!Files.isDirectory(jobsDir)) {
            return;
        }
        try (var dirs = Files.list(jobsDir)) {
            for (Path dir : (Iterable<Path>) dirs::iterator) {
                Path file = dir.resolve("job.json");
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    ConversionJob job = json.readValue(file.toFile(), ConversionJob.class);
                    if (job.getStatus() == ConversionJob.Status.RUNNING || job.getStatus() == ConversionJob.Status.QUEUED) {
                        // the uploaded / cloned source is still on disk, so the job can continue where it stopped
                        job.setStatus(ConversionJob.Status.STOPPED);
                        job.setMessage("Stopped because BOT restarted. Press Resume to continue with the remaining files.");
                    }
                    jobs.put(job.getId(), job);
                } catch (IOException e) {
                    log.warn("Skipping unreadable conversion job {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Could not read conversion jobs: {}", e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public List<ConversionJob> list() {
        return jobs.values().stream().sorted(Comparator.comparing(ConversionJob::getCreatedAt).reversed())
                .map(this::snapshot).toList();
    }

    public Optional<ConversionJob> get(String id) {
        return Optional.ofNullable(id == null ? null : jobs.get(id));
    }

    /** Stamps the job with the visitor who started it (a hash of their anonymous cookie). */
    public void assignOwner(ConversionJob job, String owner) {
        update(job, j -> j.setOwner(owner));
    }

    /**
     * Visitors only see their own jobs. The host machine (a direct localhost request, not through a tunnel) sees
     * everything, including jobs from before owners existed.
     */
    public static boolean canAccess(ConversionJob job, String owner, boolean host) {
        return host || (owner != null && !owner.isEmpty() && owner.equals(job.getOwner()));
    }

    public List<ConversionJob> listFor(String owner, boolean host) {
        return jobs.values().stream().filter(j -> canAccess(j, owner, host))
                .sorted(Comparator.comparing(ConversionJob::getCreatedAt).reversed())
                .map(this::snapshot).toList();
    }

    /** The job if this visitor may see it; empty (shown as 404) for unknown jobs and other people's jobs. */
    public Optional<ConversionJob> getFor(String id, String owner, boolean host) {
        return get(id).filter(j -> canAccess(j, owner, host));
    }

    /** A copy that is safe to serialise while the job is still being updated. */
    public ConversionJob snapshot(ConversionJob job) {
        synchronized (job) {
            return json.convertValue(job, ConversionJob.class);
        }
    }

    public ConversionJob startFromGit(String url, String branch, String subfolder, Options options) {
        GitService.validateRepoUrl(url);
        if (!allowLocalPaths && url.trim().toLowerCase(Locale.ROOT).startsWith("file:")) {
            throw new IllegalArgumentException("file:// repositories are disabled on this server - use a GitHub https URL or upload.");
        }
        ConversionJob job = newJob(options, url.trim() + (branch == null || branch.isBlank() ? "" : " (" + branch.trim() + ")"), subfolder);
        submit(job, () -> {
            update(job, j -> j.setMessage("Cloning " + url.trim() + " ..."));
            Path source = sourceDir(job.getId());
            Files.deleteIfExists(source);
            git.cloneRepo(url, branch, source);
            deleteTree(source.resolve(".git"));
        });
        return job;
    }

    public ConversionJob startFromLocal(String folder, String subfolder, Options options) {
        if (!allowLocalPaths) {
            throw new IllegalArgumentException("Local folders are disabled on this server. Upload the files or a ZIP instead.");
        }
        Path path = Paths.get(folder == null ? "" : folder.trim().replaceAll("^\"|\"$", "")).toAbsolutePath().normalize();
        if (folder == null || folder.isBlank() || !Files.isDirectory(path)) {
            throw new IllegalArgumentException("Folder not found on the BOT machine: " + folder);
        }
        ConversionJob job = newJob(options, path.toString(), subfolder);
        submit(job, () -> {
            update(job, j -> j.setMessage("Reading " + path + " ..."));
            copyProject(path, sourceDir(job.getId()));
        });
        return job;
    }

    /** Files keep their relative names (browser folder uploads send them); ZIP files are extracted. */
    public ConversionJob startFromUploads(List<MultipartFile> files, String subfolder, Options options) throws IOException {
        if (files == null || files.isEmpty() || files.stream().allMatch(MultipartFile::isEmpty)) {
            throw new IllegalArgumentException("Choose files, a folder or a ZIP to upload.");
        }
        String label = files.size() == 1 ? files.get(0).getOriginalFilename() : files.size() + " uploaded files";
        ConversionJob job = newJob(options, label, subfolder);
        Path source = sourceDir(job.getId());
        Files.createDirectories(source);
        try {
            for (MultipartFile file : files) {
                String name = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
                try (InputStream in = file.getInputStream()) {
                    if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                        unzip(in, source);
                    } else {
                        Path dest = safeResolve(source, name);
                        Files.createDirectories(dest.getParent());
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            jobs.remove(job.getId());
            deleteTree(jobDir(job.getId()));
            throw e;
        }
        submit(job, () -> { });
        return job;
    }

    /** Large pasted code is converted as a one-file project so it gets progress, chunking and file actions. */
    public ConversionJob startFromText(String fileName, String code, Options options) throws IOException {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Paste some code to convert.");
        }
        String name = fileName == null || fileName.isBlank() ? guessFileName(code, options.sourceLanguage()) : fileName.trim();
        ConversionJob job = newJob(options, "Pasted code (" + name + ")", "");
        Path dest = safeResolve(sourceDir(job.getId()), name);
        Files.createDirectories(dest.getParent());
        Files.writeString(dest, code.replace("\r\n", "\n"), StandardCharsets.UTF_8);
        submit(job, () -> { });
        return job;
    }

    private String guessFileName(String code, String sourceLanguage) {
        String id = sourceLanguage == null || sourceLanguage.isBlank() || sourceLanguage.equalsIgnoreCase("auto")
                ? CodeConversionService.detectLanguage(code) : sourceLanguage;
        ProgrammingLanguage language = converter.library().language(id == null ? "java" : id)
                .orElse(converter.library().language("java").orElseThrow());
        var type = java.util.regex.Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Z]\\w*)").matcher(code);
        return (type.find() ? type.group(1) : "Program") + "." + language.extension();
    }

    /** "Folder x does not exist" plus the folders that do exist at the deepest level that was found. */
    static String missingFolderMessage(Path sourceDir, String requested) {
        Path existing = sourceDir;
        String shown = "";
        for (String part : requested.split("/")) {
            Path next = existing.resolve(part);
            if (part.isEmpty() || !Files.isDirectory(next)) {
                break;
            }
            existing = next;
            shown = shown.isEmpty() ? part : shown + "/" + part;
        }
        List<String> names = new ArrayList<>();
        try (var children = Files.list(existing)) {
            children.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                    .filter(n -> !n.startsWith(".")).sorted().limit(40).forEach(names::add);
        } catch (IOException e) {
            // fall through with an empty list
        }
        String where = shown.isEmpty() ? "the repository root" : "\"" + shown + "\"";
        return "Sub-folder \"" + requested + "\" does not exist in the project. "
                + (names.isEmpty() ? "There are no folders in " + where + " - leave the sub-folder blank."
                : "Folders in " + where + ": " + String.join(", ", names) + ". Pick one of these or leave it blank.");
    }

    private ConversionJob newJob(Options options, String label, String subfolder) {
        ProgrammingLanguage target = converter.resolveLanguage(options.targetLanguage())
                .orElseThrow(() -> new IllegalArgumentException("Choose a target language."));
        String folder = subfolder == null ? "" : subfolder.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
        if (folder.contains("..")) {
            throw new IllegalArgumentException("Sub-folder must be inside the project.");
        }
        ConversionJob job = new ConversionJob();
        job.setId(LocalDateTime.now().format(ID_TIME) + "-" + UUID.randomUUID().toString().substring(0, 6));
        job.setCreatedAt(LocalDateTime.now().toString());
        job.setSourceLabel(label);
        job.setSourceFolder(folder);
        job.setSourceLanguage(options.sourceLanguage() == null || options.sourceLanguage().isBlank()
                ? "auto" : options.sourceLanguage());
        job.setTargetLanguageId(target.id());
        job.setTargetLanguage(target.name());
        job.setInstructions(options.instructions());
        job.setAddComments(options.addComments());
        job.setExplainLogic(options.explainLogic());
        job.setModel(converter.llmName().orElse("built-in samples only (no AI model)"));
        jobs.put(job.getId(), job);
        save(job);
        remember(job);
        return job;
    }

    private static long elapsedMs(ConversionJob j) {
        try {
            String from = j.getStartedAt() != null ? j.getStartedAt() : j.getCreatedAt();
            return Math.max(0, java.time.Duration.between(LocalDateTime.parse(from), LocalDateTime.now()).toMillis());
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private interface Step {
        void run() throws Exception;
    }

    private void submit(ConversionJob job, Step prepare) {
        submit(job, prepare, false);
    }

    private void submit(ConversionJob job, Step prepare, boolean resume) {
        executor.submit(() -> {
            try {
                update(job, j -> {
                    j.setStatus(ConversionJob.Status.RUNNING);
                    if (!resume || j.getStartedAt() == null) {
                        j.setStartedAt(LocalDateTime.now().toString());
                    }
                    j.setFinishedAt(null);
                });
                prepare.run();
                process(job, resume);
            } catch (Exception e) {
                log.warn("Conversion job {} failed", job.getId(), e);
                update(job, j -> {
                    j.setStatus(ConversionJob.Status.FAILED);
                    j.setMessage(e.getMessage() == null ? e.toString() : e.getMessage());
                    j.setCurrentFile(null);
                    j.setFinishedAt(LocalDateTime.now().toString());
                    j.setDurationMs(elapsedMs(j));
                });
            }
        });
    }

    // ------------------------------------------------------------------ Stop / Resume

    private final java.util.Set<String> stops = ConcurrentHashMap.newKeySet();

    /** Stop button: the job ends after the file it is converting now and keeps everything done so far. */
    public ConversionJob stop(String id) {
        ConversionJob job = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown conversion job."));
        if (job.getStatus() == ConversionJob.Status.RUNNING || job.getStatus() == ConversionJob.Status.QUEUED) {
            stops.add(id);
            update(job, j -> j.setMessage("Stopping after the current file..."));
        }
        return snapshot(job);
    }

    /** Resume button: converts the files that are not done yet, reusing the finished ones. */
    public ConversionJob resume(String id) {
        ConversionJob job = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown conversion job."));
        synchronized (job) {
            if (job.getStatus() != ConversionJob.Status.STOPPED) {
                throw new IllegalStateException("Only a stopped job can be resumed.");
            }
            if (!Files.isDirectory(sourceDir(id))) {
                throw new IllegalStateException("The source files of this job are gone, so it cannot be resumed.");
            }
            stops.remove(id);
            update(job, j -> {
                j.setStatus(ConversionJob.Status.QUEUED);
                j.setMessage("Resuming with the remaining files");
            });
        }
        submit(job, () -> { }, true);
        return snapshot(job);
    }

    private void process(ConversionJob job) throws IOException {
        process(job, false);
    }

    private void process(ConversionJob job, boolean resume) throws IOException {
        // a Stop pressed while the job was still queued or cloning is kept: it is honoured before the first file
        // (resume() and a new job both start with no pending stop)
        Path sourceDir = sourceDir(job.getId());
        Path root = job.getSourceFolder().isEmpty() ? sourceDir : safeResolve(sourceDir, job.getSourceFolder());
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException(missingFolderMessage(sourceDir, job.getSourceFolder()));
        }
        Path output = outputDir(job.getId());
        boolean keepDone = resume && !job.getFiles().isEmpty() && Files.isDirectory(output);
        if (!keepDone) {
            deleteTree(output);
            update(job, j -> {
                j.getFiles().clear();
                j.setDoneFiles(0);
            });
        }
        Files.createDirectories(output);

        ProgrammingLanguage target = converter.library().language(job.getTargetLanguageId())
                .or(() -> converter.resolveLanguage(job.getTargetLanguage()))
                .orElseThrow(() -> new IllegalArgumentException("Unknown target language " + job.getTargetLanguage()));
        String onlyLanguage = job.getSourceLanguage().equalsIgnoreCase("auto") ? null
                : converter.library().language(job.getSourceLanguage()).map(ProgrammingLanguage::id).orElse(null);

        List<ProjectModuleMapper.SourceFile> codeFiles = new ArrayList<>();
        List<String> sameLanguage = new ArrayList<>();
        List<String> otherFiles = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Path file : walk(root)) {
            String rel = root.relativize(file).toString().replace('\\', '/');
            String name = file.getFileName().toString();
            long size = Files.size(file);
            if (size > maxFileBytes) {
                skipped.add(rel + " - larger than " + (maxFileBytes / 1024) + " KB");
                continue;
            }
            if (BUILD_FILES.contains(name.toLowerCase(Locale.ROOT))) {
                skipped.add(rel + " - build file for the original language (create one for " + target.name() + ")");
                continue;
            }
            Optional<ProgrammingLanguage> language = languageOf(file);
            if (language.isEmpty() || isBinary(file) || (onlyLanguage != null && !language.get().id().equals(onlyLanguage))) {
                otherFiles.add(rel);
            } else if (language.get().id().equals(target.id())) {
                sameLanguage.add(rel);
            } else if (codeFiles.size() >= maxFiles) {
                skipped.add(rel + " - over the " + maxFiles + " file limit");
            } else {
                codeFiles.add(new ProjectModuleMapper.SourceFile(rel,
                        Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"), language.get()));
            }
        }
        if (codeFiles.isEmpty()) {
            throw new IllegalArgumentException(sameLanguage.isEmpty()
                    ? "No source code files were found to convert (supported: " + supportedExtensions() + ")."
                    : "All code is already " + target.name() + ", so there is nothing to convert.");
        }

        ProjectModuleMapper mapper = new ProjectModuleMapper(target);
        List<ProjectModuleMapper.Module> plan = mapper.plan(codeFiles);
        Map<String, ProjectModuleMapper.Module> bySource = new HashMap<>();
        plan.forEach(m -> bySource.put(m.source().path(), m));
        update(job, j -> {
            j.setTotalFiles(plan.size());
            j.setSkipped(skipped);
            j.setMessage("Converting " + plan.size() + " files to " + target.name() + " (dependencies first)");
        });

        Optional<LlmClient> llm = converter.activeLlm();
        Map<String, String> converted = new LinkedHashMap<>();
        java.util.Set<String> alreadyDone = new java.util.HashSet<>();
        if (keepDone) {
            for (FileResult f : job.getFiles()) {
                if (!"failed".equals(f.status())) {
                    alreadyDone.add(f.sourcePath());
                }
            }
            // failed files are tried again; finished ones stay and give context to the next files
            update(job, j -> {
                j.getFiles().removeIf(f -> "failed".equals(f.status()));
                j.setDoneFiles(j.getFiles().size());
            });
        }
        for (ProjectModuleMapper.Module module : plan) {
            if (Thread.currentThread().isInterrupted()) {
                throw new IllegalStateException("Conversion was stopped.");
            }
            if (alreadyDone.contains(module.source().path())) {
                try {
                    converted.put(module.source().path(),
                            Files.readString(safeResolve(output, module.targetPath()), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // context only
                }
                continue;
            }
            if (stops.remove(job.getId())) {
                int done = job.getDoneFiles();
                update(job, j -> {
                    j.setStatus(ConversionJob.Status.STOPPED);
                    j.setCurrentFile(null);
                    j.setDurationMs(elapsedMs(j));
                    j.setMessage("Stopped after " + done + " of " + plan.size() + " files. Press Resume to convert the rest.");
                });
                return;
            }
            update(job, j -> j.setCurrentFile(module.source().path()));
            long fileStart = System.currentTimeMillis();
            FileResult result = convertFile(job, module, target, mapper, bySource, converted, llm, output)
                    .withDuration(System.currentTimeMillis() - fileStart);
            update(job, j -> {
                j.getFiles().add(result);
                j.setDoneFiles(j.getDoneFiles() + 1);
            });
        }

        update(job, j -> {
            j.setCurrentFile(null);
            j.setMessage("Copying other files and writing reports");
        });
        for (String rel : sameLanguage) {
            copyIfAbsent(root.resolve(rel), output, rel);
            String path = rel;
            update(job, j -> j.getFiles().add(new FileResult(path, path, target.name(), "copied", "copied", 0,
                    "Already " + target.name() + "; copied unchanged.", null, null)));
        }
        for (String rel : otherFiles) {
            copyIfAbsent(root.resolve(rel), output, rel);
        }
        List<String> generated = new ArrayList<>();
        for (Map.Entry<String, String> entry : mapper.packageFiles(plan).entrySet()) {
            Path dest = safeResolve(output, entry.getKey());
            if (!Files.exists(dest)) {
                Files.createDirectories(dest.getParent());
                Files.writeString(dest, entry.getValue(), StandardCharsets.UTF_8);
                generated.add(entry.getKey());
            }
        }

        if (job.isExplainLogic()) {
            String summary = projectSummary(job, llm);
            update(job, j -> j.setProjectSummary(summary));
            Files.writeString(output.resolve("BUSINESS_LOGIC.md"), businessReport(job), StandardCharsets.UTF_8);
            generated.add("BUSINESS_LOGIC.md");
        }
        generated.add("CONVERSION_REPORT.md");
        update(job, j -> j.setGeneratedFiles(generated));
        Files.writeString(output.resolve("CONVERSION_REPORT.md"), conversionReport(job, otherFiles.size()), StandardCharsets.UTF_8);

        long failed = job.getFiles().stream().filter(f -> f.status().equals("failed")).count();
        update(job, j -> {
            j.setStatus(ConversionJob.Status.DONE);
            j.setFinishedAt(LocalDateTime.now().toString());
            j.setDurationMs(elapsedMs(j));
            j.setMessage("Converted " + (plan.size() - failed) + " of " + plan.size() + " files to " + target.name()
                    + (failed > 0 ? " (" + failed + " failed - see the report)" : "") + ".");
        });
    }

    private FileResult convertFile(ConversionJob job, ProjectModuleMapper.Module module, ProgrammingLanguage target,
                                   ProjectModuleMapper mapper, Map<String, ProjectModuleMapper.Module> bySource,
                                   Map<String, String> converted, Optional<LlmClient> llm, Path output) {
        ProjectModuleMapper.SourceFile source = module.source();
        String code = CodeConversionService.normalizeCode(source.content());
        String outCode;
        String mode;
        String notes;
        int parts = 1;
        try {
            var sample = converter.library().match(code, source.language().id())
                    .filter(m -> m.sample().code().containsKey(target.id()))
                    .filter(m -> !job.isAddComments() || llm.isEmpty());
            if (code.isBlank()) {
                outCode = "";
                mode = "copied";
                notes = "Empty file.";
            } else if (sample.isPresent()) {
                outCode = sample.get().sample().code().get(target.id());
                mode = "sample-library";
                notes = "Matched the built-in \"" + sample.get().sample().title() + "\" sample.";
            } else if (llm.isEmpty()) {
                return new FileResult(source.path(), module.targetPath(), source.language().name(), "failed", "none", 0,
                        null, "No AI model is available. Start Ollama with a coding model or set OPENAI_API_KEY.", null);
            } else {
                String context = mapper.context(module, bySource, converted);
                CodeConversionService.Translation translation = converter.translate(llm.get(), code, source.language(),
                        target, job.getInstructions(), job.isAddComments(), context, (part, total) ->
                                update(job, j -> j.setCurrentFile(source.path() + " (part " + part + " of " + total + ")")));
                outCode = translation.code();
                notes = translation.notes();
                parts = translation.parts();
                mode = "llm";
            }
            Path dest = safeResolve(output, module.targetPath());
            Files.createDirectories(dest.getParent());
            Files.writeString(dest, outCode.endsWith("\n") ? outCode : outCode + "\n", StandardCharsets.UTF_8);
            converted.put(source.path(), outCode);
        } catch (Exception e) {
            return new FileResult(source.path(), module.targetPath(), source.language().name(), "failed", "llm", 0,
                    null, e.getMessage(), null);
        }
        String businessLogic = null;
        if (job.isExplainLogic() && !code.isBlank()) {
            StringBuilder related = new StringBuilder();
            for (FileResult done : job.getFiles()) {
                if (done.businessLogic() != null && module.dependencies().contains(done.sourcePath())
                        && related.length() < 6_000) {
                    related.append(done.sourcePath()).append(":\n")
                            .append(CodeConversionService.keyPoints(done.businessLogic(), 700)).append("\n\n");
                }
            }
            businessLogic = llm.map(client -> {
                try {
                    return converter.explainWith(client, code, source.language().name(), related.toString());
                } catch (RuntimeException e) {
                    return "Could not extract the business logic: " + e.getMessage();
                }
            }).orElseGet(() -> converter.explainBusinessLogic(code, source.language()));
        }
        return new FileResult(source.path(), module.targetPath(), source.language().name(), "converted", mode, parts,
                notes, null, businessLogic);
    }

    private String projectSummary(ConversionJob job, Optional<LlmClient> llm) {
        List<FileResult> explained = job.getFiles().stream().filter(f -> f.businessLogic() != null).toList();
        if (explained.isEmpty()) {
            return null;
        }
        if (explained.size() == 1 || llm.isEmpty()) {
            return explained.get(0).businessLogic();
        }
        StringBuilder notes = new StringBuilder();
        for (FileResult file : explained) {
            if (notes.length() > 14_000) {
                notes.append("(more files omitted)\n");
                break;
            }
            notes.append("File ").append(file.sourcePath()).append(":\n")
                    .append(CodeConversionService.keyPoints(file.businessLogic(), 900)).append("\n\n");
        }
        try {
            return converter.summarize(llm.get(), notes.toString());
        } catch (RuntimeException e) {
            return "Could not summarise the project: " + e.getMessage();
        }
    }

    private static String businessReport(ConversionJob job) {
        StringBuilder sb = new StringBuilder("# What the code does in everyday language\n\n")
                .append("Project: ").append(job.getSourceLabel()).append("\n\n");
        if (job.getProjectSummary() != null) {
            sb.append("## Whole project\n\n").append(job.getProjectSummary()).append("\n\n");
        }
        for (FileResult file : job.getFiles()) {
            if (file.businessLogic() != null) {
                sb.append("## ").append(file.sourcePath()).append("\n\n").append(file.businessLogic()).append("\n\n");
            }
        }
        return sb.toString();
    }

    private static String conversionReport(ConversionJob job, int otherFiles) {
        StringBuilder sb = new StringBuilder("# Conversion report\n\n")
                .append("- Source: ").append(job.getSourceLabel()).append('\n')
                .append(job.getSourceFolder().isEmpty() ? "" : "- Folder: " + job.getSourceFolder() + "\n")
                .append("- Target language: ").append(job.getTargetLanguage()).append('\n')
                .append("- Model: ").append(job.getModel()).append('\n')
                .append("- Comments added: ").append(job.isAddComments() ? "yes" : "no").append('\n')
                .append("- Files converted in dependency order (each file is converted after the files it uses).\n\n")
                .append("| Source file | Converted file | Status | How | Parts | Notes |\n")
                .append("|---|---|---|---|---|---|\n");
        for (FileResult f : job.getFiles()) {
            String note = f.error() != null ? f.error() : f.notes() == null ? "" : f.notes();
            note = note.replace("\n", " ").replace("|", "/");
            sb.append("| ").append(f.sourcePath()).append(" | ").append(f.targetPath()).append(" | ").append(f.status())
                    .append(" | ").append(f.mode()).append(" | ").append(f.parts()).append(" | ")
                    .append(note.length() > 200 ? note.substring(0, 200) + "..." : note).append(" |\n");
        }
        if (!job.getGeneratedFiles().isEmpty()) {
            sb.append("\n## Generated project files\n\n");
            job.getGeneratedFiles().forEach(f -> sb.append("- ").append(f).append('\n'));
        }
        if (otherFiles > 0) {
            sb.append("\n").append(otherFiles).append(" non-code files (resources, docs, config) were copied unchanged.\n");
        }
        if (!job.getSkipped().isEmpty()) {
            sb.append("\n## Skipped\n\n");
            job.getSkipped().forEach(s -> sb.append("- ").append(s).append('\n'));
        }
        sb.append("\nReview and test the converted code before using it in production.\n");
        return sb.toString();
    }

    // ---- file access ----------------------------------------------------------------------------------------

    public Path sourceDir(String id) {
        return jobDir(id).resolve("source");
    }

    public Path outputDir(String id) {
        return jobDir(id).resolve("output");
    }

    private Path jobDir(String id) {
        if (id == null || !id.matches("[\\w-]+")) {
            throw new IllegalArgumentException("Unknown conversion job.");
        }
        return jobsDir.resolve(id);
    }

    /** Relative paths of all files on one side ("output" or "source"). */
    public List<String> listFiles(String id, String side) throws IOException {
        Path base = sideDir(id, side);
        if (!Files.isDirectory(base)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        for (Path file : walk(base)) {
            files.add(base.relativize(file).toString().replace('\\', '/'));
        }
        return files;
    }

    public Path resolveFile(String id, String side, String path) {
        Path file = safeResolve(sideDir(id, side), path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File not found: " + path);
        }
        return file;
    }

    public String readFile(String id, String side, String path) throws IOException {
        Path file = resolveFile(id, side, path);
        if (isBinary(file)) {
            return "(binary file - download it to view)";
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public void saveFile(String id, String path, String content) throws IOException {
        ConversionJob job = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown conversion job."));
        if (job.getStatus() == ConversionJob.Status.RUNNING) {
            throw new IllegalStateException("Wait for the conversion to finish before editing.");
        }
        Path file = safeResolve(outputDir(id), path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content == null ? "" : content.replace("\r\n", "\n"), StandardCharsets.UTF_8);
    }

    /**
     * ZIP of the converted output. It also carries the business logic: a {@code Foo.business-logic.md} next to every
     * converted file that has a description, and BUSINESS_LOGIC.md (the project report, or a note saying it was not requested).
     */
    public void writeZip(String id, OutputStream out) throws IOException {
        Path base = outputDir(id);
        ConversionJob job = get(id).orElse(null);
        Set<String> names = new java.util.LinkedHashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Path file : walk(base)) {
                String name = base.relativize(file).toString().replace('\\', '/');
                names.add(name);
                zip.putNextEntry(new ZipEntry(name));
                Files.copy(file, zip);
                zip.closeEntry();
            }
            if (job == null) {
                return;
            }
            List<FileResult> files;
            synchronized (job) {
                files = new ArrayList<>(job.getFiles());
            }
            for (FileResult f : files) {
                if (f.businessLogic() == null || f.businessLogic().isBlank() || !names.contains(f.targetPath())) {
                    continue;
                }
                String name = BusinessLogicFiles.companionName(f.targetPath(), names);
                names.add(name);
                addText(zip, name, BusinessLogicFiles.fileMarkdown(job, f, f.targetPath()));
            }
            if (!names.contains(BusinessLogicFiles.PROJECT_FILE)) {
                addText(zip, BusinessLogicFiles.PROJECT_FILE, BusinessLogicFiles.projectNote(job));
            }
        }
    }

    private static void addText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Business logic of one converted file as Markdown (a short note when none was generated). */
    public String logicMarkdown(String id, String outputPath) {
        ConversionJob job = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown conversion job."));
        FileResult file;
        synchronized (job) {
            file = job.getFiles().stream().filter(f -> f.targetPath().equals(outputPath)).findFirst().orElse(null);
        }
        return BusinessLogicFiles.fileMarkdown(job, file, outputPath);
    }

    public ConversionJob.PushRecord push(String id, String repoUrl, String branch, String baseBranch, String folder,
                                         String commitMessage) throws IOException {
        return push(id, repoUrl, branch, baseBranch, folder, commitMessage, null, false);
    }

    /** {@code userToken} is a visitor's own GitHub token (used instead of the server's, never logged); {@code pull} starts from the remote branch. */
    public ConversionJob.PushRecord push(String id, String repoUrl, String branch, String baseBranch, String folder,
                                         String commitMessage, String userToken, boolean pull) throws IOException {
        ConversionJob job = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown conversion job."));
        if (job.getStatus() != ConversionJob.Status.DONE) {
            throw new IllegalStateException("The conversion has not finished yet.");
        }
        String targetFolder = folder == null ? "converted/" + job.getTargetLanguageId() : folder;
        Path work = jobDir(id).resolve("push-" + System.currentTimeMillis());
        try {
            GitService.PushResult result = git.pushDirectory(outputDir(id), repoUrl, branch, baseBranch, targetFolder,
                    commitMessage == null || commitMessage.isBlank()
                            ? "Convert " + job.getSourceLabel() + " to " + job.getTargetLanguage() + " (BOT)" : commitMessage,
                    work, userToken, pull);
            ConversionJob.PushRecord record = new ConversionJob.PushRecord(repoUrl.trim(), result.branch(),
                    result.baseBranch(), targetFolder, result.branchUrl(), result.compareUrl(), result.commit(),
                    LocalDateTime.now().toString());
            update(job, j -> j.getPushes().add(record));
            return record;
        } finally {
            deleteTree(work);
        }
    }

    private Path sideDir(String id, String side) {
        ConversionJob job = get(id).orElseThrow(() -> new IllegalArgumentException("Unknown conversion job."));
        if ("source".equalsIgnoreCase(side)) {
            return job.getSourceFolder().isEmpty() ? sourceDir(id) : safeResolve(sourceDir(id), job.getSourceFolder());
        }
        return outputDir(id);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private Optional<ProgrammingLanguage> languageOf(Path file) {
        return converter.library().languageOfFile(file.getFileName().toString(), () -> {
            try {
                return Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException | java.io.UncheckedIOException e) {
                return "";
            }
        });
    }

    private static String supportedExtensions() {
        return String.join(", ", LanguageCatalog.extensions());
    }

    /** All regular files under {@code base}, skipping build output and tool folders, sorted by path. */
    static List<Path> walk(Path base) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(base, new java.nio.file.SimpleFileVisitor<>() {
            @Override
            public java.nio.file.FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) {
                return !dir.equals(base) && SKIP_DIRS.contains(dir.getFileName().toString())
                        ? java.nio.file.FileVisitResult.SKIP_SUBTREE : java.nio.file.FileVisitResult.CONTINUE;
            }

            @Override
            public java.nio.file.FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    files.add(file);
                }
                return java.nio.file.FileVisitResult.CONTINUE;
            }
        });
        files.sort(Comparator.comparing(p -> base.relativize(p).toString().replace('\\', '/')));
        return files;
    }

    private void copyProject(Path from, Path to) throws IOException {
        for (Path file : walk(from)) {
            if (Files.size(file) > maxFileBytes * 4) {
                continue;
            }
            Path dest = safeResolve(to, from.relativize(file).toString());
            Files.createDirectories(dest.getParent());
            Files.copy(file, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyIfAbsent(Path file, Path output, String rel) throws IOException {
        Path dest = safeResolve(output, rel);
        if (!Files.exists(dest)) {
            Files.createDirectories(dest.getParent());
            Files.copy(file, dest);
        }
    }

    void unzip(InputStream in, Path destination) throws IOException {
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path dest = safeResolve(destination, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(dest);
                    continue;
                }
                Files.createDirectories(dest.getParent());
                total += Files.copy(zip, dest, StandardCopyOption.REPLACE_EXISTING);
                if (total > 500L * 1024 * 1024) {
                    throw new IllegalArgumentException("The ZIP expands to more than 500 MB.");
                }
            }
        }
    }

    /** Resolves a user-supplied relative path, rejecting anything that escapes {@code base}. */
    static Path safeResolve(Path base, String relative) {
        String cleaned = relative == null ? "" : relative.replace('\\', '/').replaceAll("^[A-Za-z]:", "")
                .replaceAll("^/+", "");
        Path normalizedBase = base.toAbsolutePath().normalize();
        Path resolved = normalizedBase.resolve(cleaned).normalize();
        if (cleaned.isEmpty() || !resolved.startsWith(normalizedBase) || resolved.equals(normalizedBase)) {
            throw new IllegalArgumentException("Invalid file path: " + relative);
        }
        return resolved;
    }

    private static boolean isBinary(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(8192);
            for (byte b : head) {
                if (b == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                path.toFile().setWritable(true);
                Files.deleteIfExists(path);
            }
        }
    }

    private void update(ConversionJob job, java.util.function.Consumer<ConversionJob> change) {
        ConversionJob.Status before;
        ConversionJob.Status after;
        synchronized (job) {
            before = job.getStatus();
            change.accept(job);
            after = job.getStatus();
            save(job);
        }
        if (before != after && (after == ConversionJob.Status.DONE || after == ConversionJob.Status.FAILED
                || after == ConversionJob.Status.STOPPED)) {
            remember(job);
        }
    }

    private void remember(ConversionJob job) {
        if (history == null || job == null) {
            return;
        }
        try {
            history.saveConversionJob(snapshot(job));
        } catch (RuntimeException e) {
            log.warn("Could not save conversion job {} to previous results: {}", job.getId(), e.getMessage());
        }
    }

    private void save(ConversionJob job) {
        synchronized (job) {
            try {
                Path dir = jobDir(job.getId());
                Files.createDirectories(dir);
                json.writeValue(dir.resolve("job.json").toFile(), job);
            } catch (IOException e) {
                log.warn("Could not save conversion job {}: {}", job.getId(), e.getMessage());
            }
        }
    }
}
