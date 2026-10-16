package com.agentic.chatbot.service;

import com.agentic.chatbot.generator.SiteBackends;
import com.agentic.chatbot.stage.StageLanguage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Finds what BOT generated for the current site and hands it over: the user stories, the code in the language that was
 * generated (Java, Python, Go ...), the site pages and the business-logic files, as a grouped list or as ZIP files.
 *
 * <p>The language is the one SITE_META.json records ("language"), which the home page picker or the prompt chose. Java
 * is the Spring Boot project itself; every other language is the server written to {@code server/<language>/}.
 */
@Service
public class SiteCodeService {

    public static final String STORIES_URL = "/code/download/stories";
    public static final String CODE_ZIP_URL = "/code/download/code.zip";
    public static final String SITE_ZIP_URL = "/code/download/site.zip";
    public static final String FILE_URL = "/code/download/file";

    private static final Set<String> SKIP_DIRS = Set.of("target", "node_modules", ".git", "conversions", ".runs",
            "stages", "training", ".gradle", "build", "__pycache__");

    private final SiteStyleService styles;

    public SiteCodeService(SiteStyleService styles) {
        this.styles = styles;
    }

    public Path root() {
        return styles.outputDir();
    }

    /** One titled group of files shown on /code. */
    public static final class Group {
        private final String id;
        private final String title;
        private final String hint;
        private final List<String> files;

        Group(String id, String title, String hint, List<String> files) {
            this.id = id;
            this.title = title;
            this.hint = hint;
            this.files = files;
        }

        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getHint() { return hint; }
        public List<String> getFiles() { return files; }
    }

    /** Everything the /code page and the chat need to know about the current generation. */
    public static final class Bundle {
        private final StageLanguage language;
        private final String brand;
        private final List<Group> groups;

        Bundle(StageLanguage language, String brand, List<Group> groups) {
            this.language = language;
            this.brand = brand;
            this.groups = groups;
        }

        public String getLanguageId() { return language.id(); }
        public String getLanguageLabel() { return language.label(); }
        public String getBrand() { return brand; }
        public List<Group> getGroups() { return groups; }
        public String getRunCommand() { return SiteBackends.runCommand(language); }

        /** Every listed file, in display order, without duplicates. */
        public List<String> allFiles() {
            Set<String> all = new LinkedHashSet<>();
            groups.forEach(g -> all.addAll(g.files));
            return new ArrayList<>(all);
        }

        public boolean isEmpty() {
            return groups.stream().allMatch(g -> g.files.isEmpty());
        }

        public Group group(String id) {
            return groups.stream().filter(g -> g.id.equals(id)).findFirst().orElse(null);
        }
    }

    // ------------------------------------------------------------------ language

    /** The language recorded for the generated site; Java when nothing says otherwise. */
    public StageLanguage language() {
        return language(root());
    }

    public static StageLanguage language(Path root) {
        try {
            Path meta = root.resolve("SITE_META.json");
            if (Files.isRegularFile(meta)) {
                JsonNode node = new ObjectMapper().readTree(Files.readString(meta, StandardCharsets.UTF_8));
                JsonNode lang = node.get("language");
                if (lang != null && !lang.asText().isBlank()) {
                    return StageLanguage.from(lang.asText());
                }
            }
        } catch (IOException ignored) {
            // fall back to the folder below
        }
        Path server = root.resolve("server");
        if (Files.isDirectory(server)) {
            try (Stream<Path> dirs = Files.list(server)) {
                var first = dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().findFirst();
                if (first.isPresent()) {
                    return StageLanguage.from(first.get());
                }
            } catch (IOException ignored) {
                // Java
            }
        }
        return StageLanguage.JAVA;
    }

    // ------------------------------------------------------------------ listing

    public Bundle describe() {
        return describe(root());
    }

    public static Bundle describe(Path root) {
        StageLanguage lang = language(root);
        boolean java = lang == StageLanguage.JAVA;
        String serverPrefix = "server/" + lang.id() + "/";

        List<String> all = allFiles(root);
        List<String> stories = new ArrayList<>();
        List<String> code = new ArrayList<>();
        List<String> reference = new ArrayList<>();
        List<String> site = new ArrayList<>();
        List<String> logic = new ArrayList<>();
        for (String rel : all) {
            String lower = rel.toLowerCase(Locale.ROOT);
            if (lower.equals("user_story.md")) {
                stories.add(rel);
            } else if (lower.equals("workflow.md") || lower.equals("bot_model.json")
                    || lower.equals("business_logic.md") || lower.endsWith(".business-logic.md")) {
                logic.add(rel);
            } else if (lower.startsWith("src/main/resources/templates/") || lower.startsWith("src/main/resources/static/")
                    || (lower.startsWith("server/") && lower.contains("/public/"))
                    || lower.startsWith("site_") || lower.endsWith("/site_style.json")) {
                site.add(rel);
            } else if (java) {
                code.add(rel);
            } else if (lower.startsWith(serverPrefix)) {
                code.add(rel);
            } else if (lower.startsWith("server/")) {
                // a server folder of another language than SITE_META names: still the user's code
                code.add(rel);
            } else {
                reference.add(rel);
            }
        }
        Comparator<String> order = Comparator.comparing((String s) -> !s.toLowerCase(Locale.ROOT).contains("readme"))
                .thenComparing(String::compareToIgnoreCase);
        code.sort(order);
        reference.sort(Comparator.comparing((String s) -> !s.endsWith(".java")).thenComparing(String::compareToIgnoreCase));
        site.sort(Comparator.comparing((String s) -> !s.toLowerCase(Locale.ROOT).endsWith("/home.html"))
                .thenComparing(String::compareToIgnoreCase));

        List<Group> groups = new ArrayList<>();
        groups.add(new Group("stories", "User stories", "Written by the Story bot from your prompt.", stories));
        groups.add(new Group("code", lang.label() + " code",
                java ? "The Spring Boot project BOT generated: controller, pom.xml and tests."
                        : "The " + lang.label() + " server BOT generated. Run: " + SiteBackends.runCommand(lang),
                code));
        groups.add(new Group("site", "Site pages and assets",
                "The HTML pages (and any CSS/JS) of the website, plus its colour and brand settings.", site));
        if (!java) {
            groups.add(new Group("reference", "Spring Boot preview app (Java)",
                    "The reference app behind the live preview at /site/home. The " + lang.label()
                            + " server above serves the same pages.", reference));
        }
        groups.add(new Group("logic", "Business logic", "How the bots worked, and the model trained from this run.", logic));
        groups.removeIf(g -> g.files.isEmpty());
        return new Bundle(lang, readBrand(root), groups);
    }

    private static String readBrand(Path root) {
        return SiteExportService.brand(root);
    }

    /** Every deliverable file of the generated project, as project-relative paths with forward slashes. */
    static List<String> allFiles(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = root.relativize(p).toString().replace('\\', '/');
                if (deliverable(rel)) {
                    out.add(rel);
                }
            });
        } catch (IOException e) {
            return List.of();
        }
        return out;
    }

    static boolean deliverable(String rel) {
        String[] parts = rel.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if (SKIP_DIRS.contains(parts[i])) {
                return false;
            }
        }
        String lower = rel.toLowerCase(Locale.ROOT);
        String name = parts[parts.length - 1].toLowerCase(Locale.ROOT);
        if (name.equals(".gitkeep") || name.endsWith(".class") || name.endsWith(".jar")) {
            return false;
        }
        if (parts.length == 1) {
            return name.equals("user_story.md") || name.equals("workflow.md") || name.equals("bot_model.json")
                    || name.equals("pom.xml") || name.equals("business_logic.md")
                    || name.equals("site_meta.json") || name.equals("site_style.json") || name.equals("site_agent.json");
        }
        if (lower.startsWith("src/") || lower.startsWith("server/")) {
            return true;
        }
        return false;
    }

    /** True when {@code rel} is one of the files listed for the site (so it may be viewed or downloaded). */
    public boolean isDeliverable(String rel) {
        return rel != null && !rel.isBlank() && allFiles(root()).contains(rel.replace('\\', '/'));
    }

    // ------------------------------------------------------------------ downloads

    /** A name for downloads: {@code briar-labs-python}. */
    public String baseName() {
        Bundle b = describe();
        return SiteExportService.slug(b.getBrand()) + "-" + b.getLanguageId();
    }

    /** The user stories as one Markdown file (USER_STORY.md); null when none were written. */
    public String storiesMarkdown() throws IOException {
        Path story = root().resolve("USER_STORY.md");
        return Files.isRegularFile(story) ? Files.readString(story, StandardCharsets.UTF_8) : null;
    }

    /**
     * ZIP of the code in the generated language: the stories, the code (for Java with its templates, for the other
     * languages with the pages their server serves) and the business-logic files.
     */
    public byte[] codeZip() throws IOException {
        Bundle b = describe();
        List<String> files = new ArrayList<>();
        for (String id : List.of("stories", "code", "logic")) {
            Group g = b.group(id);
            if (g != null) {
                files.addAll(g.files);
            }
        }
        Group site = b.group("site");
        if (site != null) {
            String keep = b.getLanguageId().equals("java") ? "src/" : "server/";
            site.files.stream().filter(f -> f.startsWith(keep)).forEach(files::add);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        String top = SiteExportService.slug(b.getBrand()) + "-" + b.getLanguageId() + "/";
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            Set<String> written = new LinkedHashSet<>();
            addText(zip, written, top + "README.md", readme(b));
            for (String rel : files) {
                add(zip, written, top + rel, root().resolve(rel));
            }
        }
        return bytes.toByteArray();
    }
    private static void add(ZipOutputStream zip, Set<String> written, String name, Path file) throws IOException {
        if (!Files.isRegularFile(file) || !written.add(name)) {
            return;
        }
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static void addText(ZipOutputStream zip, Set<String> written, String name, String text) throws IOException {
        if (!written.add(name)) {
            return;
        }
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readme(Bundle b) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(b.getBrand()).append(" - ").append(b.getLanguageLabel()).append(" code\n\n");
        sb.append("Generated by BOT. Language: **").append(b.getLanguageLabel()).append("**.\n\n");
        sb.append("## What is in this ZIP\n\n");
        for (Group g : b.getGroups()) {
            if (!g.id.equals("reference") && !g.id.equals("site")) {
                sb.append("- **").append(g.title).append("** - ").append(g.hint).append('\n');
            }
        }
        sb.append("\n## Run it\n\n```\n").append(b.getRunCommand()).append("\n```\n\n");
        sb.append("Then open http://localhost:8090/site/home\n");
        return sb.toString();
    }
}