package com.agentic.chatbot.web;

import com.agentic.chatbot.service.SiteStyleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Browse Java + HTML source for the website the user asked BOT to create.
 */
@Controller
public class CodeViewerController {

    private final SiteStyleService styleService;

    public CodeViewerController(SiteStyleService styleService) {
        this.styleService = styleService;
    }

    @GetMapping("/code")
    public String code(
            @RequestParam(value = "file", required = false) String file,
            Model model) throws Exception {
        Path root = styleService.outputDir().toAbsolutePath().normalize();
        List<String> files = listSourceFiles(root);
        model.addAttribute("files", files);
        model.addAttribute("hasCode", !files.isEmpty());
        model.addAttribute("previewUrl", resolvePreviewUrl(root));

        String userStoryText = "";
        Path storyPath = root.resolve("USER_STORY.md");
        if (Files.exists(storyPath)) {
            userStoryText = Files.readString(storyPath, StandardCharsets.UTF_8);
        }
        model.addAttribute("userStoryText", userStoryText);
        model.addAttribute("hasUserStory", userStoryText != null && !userStoryText.isBlank());

        String selected = file;
        if ((selected == null || selected.isBlank()) && !files.isEmpty()) {
            selected = files.stream()
                    .filter(f -> f.equalsIgnoreCase("USER_STORY.md"))
                    .findFirst()
                    .orElse(files.stream()
                            .filter(f -> f.endsWith(".java"))
                            .findFirst()
                            .orElse(files.get(0)));
        }

        if (selected != null && !selected.isBlank()) {
            Path resolved = root.resolve(selected).normalize();
            if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
                model.addAttribute("error", "File not found or outside generated project.");
                model.addAttribute("selected", null);
                model.addAttribute("content", "");
                model.addAttribute("language", "text");
            } else {
                String content = Files.readString(resolved, StandardCharsets.UTF_8);
                model.addAttribute("selected", selected);
                model.addAttribute("content", content);
                model.addAttribute("language", languageFor(selected));
                model.addAttribute("error", null);
            }
        } else {
            model.addAttribute("selected", null);
            model.addAttribute("content", "");
            model.addAttribute("language", "text");
            model.addAttribute("error", files.isEmpty()
                    ? "No generated website code yet. Ask BOT to create a site first."
                    : null);
        }
        return "code";
    }

    private static List<String> listSourceFiles(Path root) throws Exception {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".java") || name.endsWith(".html")
                                || name.endsWith(".css") || name.equals("pom.xml")
                                || name.equals("user_story.md") || name.equals("bot_model.json")
                                || name.equals("site_meta.json") || name.equals("site_style.json");
                    })
                    .filter(p -> {
                        String rel = root.relativize(p).toString().replace('\\', '/');
                        return !rel.contains("/target/") && !rel.startsWith("target/")
                                && !rel.contains("/uploads/");
                    })
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .sorted(Comparator
                            .comparing((String s) -> !s.endsWith(".java"))
                            .thenComparing(s -> !s.contains("/templates/"))
                            .thenComparing(String::compareToIgnoreCase))
                    .forEach(out::add);
        }
        return out;
    }

    private static String resolvePreviewUrl(Path root) {
        try {
            Path meta = root.resolve("SITE_META.json");
            if (Files.exists(meta)) {
                String json = Files.readString(meta);
                int idx = json.indexOf("\"previewUrl\"");
                if (idx >= 0) {
                    int q1 = json.indexOf('"', idx + 12);
                    int q2 = json.indexOf('"', q1 + 1);
                    if (q1 > 0 && q2 > q1) {
                        return json.substring(q1 + 1, q2);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        Path home = root.resolve("src/main/resources/templates/home.html");
        return Files.exists(home) ? "/site/home" : "/";
    }

    private static String languageFor(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".html")) {
            return "html";
        }
        if (lower.endsWith(".css")) {
            return "css";
        }
        if (lower.endsWith(".xml") || lower.endsWith("pom.xml")) {
            return "xml";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        return "text";
    }
}
