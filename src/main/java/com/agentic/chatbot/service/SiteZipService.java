package com.agentic.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Packs what BOT generated into one download: the user stories, the code in the language that was used for the run,
 * and the website itself (plain HTML pages that open from the unzipped folder, plus the uploaded images).
 *
 * <pre>
 * bot-&lt;brand&gt;-stories-code-site.zip
 *   README.md
 *   stories/   USER_STORY.md, WORKFLOW.md
 *   code/      the project in the chosen language (Spring Boot for Java, server/&lt;language&gt; for the others)
 *   site/      index.html + every page, uploads/, SITE_*.json
 * </pre>
 */
@Service
public class SiteZipService {

    private static final Set<String> SKIP_DIRS = Set.of("target", "node_modules", ".git", ".runs", "stages", "conversions");

    private final SiteStyleService styles;

    public SiteZipService(SiteStyleService styles) {
        this.styles = styles;
    }

    /** True when there is something to download: a generated website or a user story. */
    public boolean hasContent() {
        return hasContent(styles.outputDir());
    }

    public static boolean hasContent(Path root) {
        return SiteExportService.hasSite(root) || Files.isRegularFile(root.resolve("USER_STORY.md"));
    }

    /** {@code bot-<brand>-stories-code-site.zip}. */
    public String fileName() {
        return fileName(styles.outputDir());
    }

    public static String fileName(Path root) {
        String brand = SiteExportService.brand(root);
        return "bot-" + SiteExportService.slug("site".equals(brand) ? "" : brand) + "-stories-code-site.zip";
    }

    public byte[] build() {
        return build(styles.outputDir());
    }

    /** The language recorded for the site ("java" when none): lowercase id such as java, python, go. */
    public static String language(Path root) {
        try {
            Path meta = root.resolve("SITE_META.json");
            if (Files.exists(meta)) {
                JsonNode n = new ObjectMapper().readTree(Files.readString(meta, StandardCharsets.UTF_8));
                String lang = n.path("language").asText("").trim().toLowerCase(Locale.ROOT);
                if (!lang.isEmpty()) {
                    return lang;
                }
            }
        } catch (IOException ignored) {
            // falls back to Java
        }
        return "java";
    }

    public static byte[] build(Path root) {
        if (!hasContent(root)) {
            throw new IllegalArgumentException("There is nothing to download yet. Describe a website to BOT first.");
        }
        String wanted = language(root);
        Path serverDir = root.resolve("server").resolve(wanted);
        boolean otherLanguage = !wanted.equals("java") && Files.isDirectory(serverDir);
        String language = otherLanguage ? wanted : "java";

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            List<String> stories = new ArrayList<>();
            for (String name : List.of("USER_STORY.md", "WORKFLOW.md")) {
                if (add(zip, root.resolve(name), "stories/" + name)) {
                    stories.add(name);
                }
            }
            int codeFiles = 0;
            if (otherLanguage) {
                codeFiles += addTree(zip, serverDir, "code/" + language + "/");
            } else {
                codeFiles += add(zip, root.resolve("pom.xml"), "code/pom.xml") ? 1 : 0;
                codeFiles += addTree(zip, root.resolve("src"), "code/src/");
            }
            int siteFiles = addSite(zip, root);
            text(zip, "README.md", readme(root, language, otherLanguage, stories, codeFiles, siteFiles));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the ZIP: " + e.getMessage(), e);
        }
        return bytes.toByteArray();
    }

    // ------------------------------------------------------------------ the website part

    private static int addSite(ZipOutputStream zip, Path root) throws IOException {
        int count = 0;
        Path templates = root.resolve("src/main/resources/templates");
        if (Files.isDirectory(templates)) {
            List<Path> pages;
            try (Stream<Path> files = Files.list(templates)) {
                pages = files.filter(p -> p.getFileName().toString().endsWith(".html")).sorted().toList();
            }
            for (Path page : pages) {
                String name = page.getFileName().toString();
                // pages sit next to uploads/, so /uploads/x.png becomes uploads/x.png
                String html = SiteExportService.staticHtml(Files.readString(page, StandardCharsets.UTF_8))
                        .replaceAll("([\"'(])/uploads/", "$1uploads/");
                text(zip, "site/" + name, html);
                count++;
                if (name.equals("home.html")) {
                    text(zip, "site/index.html", html);
                    count++;
                }
            }
        }
        count += addTree(zip, root.resolve("uploads"), "site/uploads/");
        for (String name : List.of("SITE_STYLE.json", "SITE_META.json", "SITE_AGENT.json")) {
            count += add(zip, root.resolve(name), "site/" + name) ? 1 : 0;
        }
        return count;
    }

    // ------------------------------------------------------------------ zip helpers

    private static boolean add(ZipOutputStream zip, Path file, String entry) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        zip.putNextEntry(new ZipEntry(entry));
        Files.copy(file, zip);
        zip.closeEntry();
        return true;
    }

    private static int addTree(ZipOutputStream zip, Path dir, String prefix) throws IOException {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        List<Path> files;
        try (Stream<Path> walk = Files.walk(dir)) {
            files = walk.filter(Files::isRegularFile).sorted().toList();
        }
        int count = 0;
        for (Path p : files) {
            Path rel = dir.relativize(p);
            if (skipped(rel)) {
                continue;
            }
            if (add(zip, p, prefix + rel.toString().replace('\\', '/'))) {
                count++;
            }
        }
        return count;
    }

    private static boolean skipped(Path rel) {
        for (Path part : rel) {
            if (SKIP_DIRS.contains(part.toString())) {
                return true;
            }
        }
        return rel.getFileName().toString().equals(".gitkeep");
    }

    private static void text(ZipOutputStream zip, String entry, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(entry));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readme(Path root, String language, boolean otherLanguage, List<String> stories,
                                 int codeFiles, int siteFiles) {
        String brand = SiteExportService.brand(root);
        String title = "site".equals(brand) ? "Website generated by BOT" : brand + " \u2014 generated by BOT";
        String run = otherLanguage
                ? "Open `code/" + language + "/README.md` for the exact run command."
                : "Run it with `cd code && mvn spring-boot:run`, then open http://localhost:8080/.";
        return "# " + title + "\n\n"
                + "Everything BOT generated, in one download.\n\n"
                + "| Folder | What is inside |\n|---|---|\n"
                + "| `stories/` | The user stories (" + String.join(", ", stories) + ") |\n"
                + "| `code/` | The code in **" + displayName(language) + "** (" + codeFiles + " files). " + run + " |\n"
                + "| `site/` | The website as plain HTML (" + siteFiles + " files): open `site/index.html` in a browser. "
                + "Images are in `site/uploads/`. |\n\n"
                + "The pages in `site/` are static copies, so the contact form shows the thank-you page without a server; "
                + "the code in `code/` is the version that runs a real backend.\n";
    }

    private static String displayName(String id) {
        return switch (id) {
            case "java" -> "Java (Spring Boot + Thymeleaf)";
            case "javascript" -> "JavaScript (Node)";
            case "typescript" -> "TypeScript";
            case "csharp" -> "C#";
            case "python" -> "Python";
            default -> id.isEmpty() ? "Java" : Character.toUpperCase(id.charAt(0)) + id.substring(1);
        };
    }
}
