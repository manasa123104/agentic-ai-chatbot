package com.agentic.chatbot.web;

import com.agentic.chatbot.service.SiteCodeService;
import com.agentic.chatbot.service.SiteStyleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * "User stories + code": the user stories and the generated project in the language that was generated, with the site
 * pages and business-logic files, and download links (stories, code ZIP, whole-site ZIP, single files).
 */
@Controller
public class CodeViewerController {

    private final SiteStyleService styleService;
    private final SiteCodeService siteCode;

    public CodeViewerController(SiteStyleService styleService, SiteCodeService siteCode) {
        this.styleService = styleService;
        this.siteCode = siteCode;
    }

    @GetMapping("/code")
    public String code(
            @RequestParam(value = "file", required = false) String file,
            Model model,
            jakarta.servlet.http.HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) throws Exception {
        model.addAttribute("isHost", RequestOrigin.isHost(request));
        Object last = session.getAttribute("lastPipelineResponse");
        if (last instanceof com.agentic.chatbot.model.PipelineResponse pr && pr.getDurationMs() != null) {
            model.addAttribute("lastRunMs", pr.getDurationMs());
        }
        Path root = styleService.outputDir().toAbsolutePath().normalize();
        SiteCodeService.Bundle bundle = SiteCodeService.describe(root);
        List<String> files = bundle.allFiles();
        model.addAttribute("bundle", bundle);
        model.addAttribute("groups", bundle.getGroups());
        model.addAttribute("files", files);
        model.addAttribute("hasCode", !files.isEmpty());
        model.addAttribute("siteLanguage", bundle.getLanguageLabel());
        model.addAttribute("zipLanguage", bundle.getLanguageLabel());
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
            // open the main file of the generated language first: its own code, not the stories
            SiteCodeService.Group codeGroup = bundle.group("code");
            selected = codeGroup != null && !codeGroup.getFiles().isEmpty()
                    ? codeGroup.getFiles().stream().filter(f -> !f.toLowerCase(Locale.ROOT).contains("readme")
                            && !f.equals("pom.xml")).findFirst().orElse(codeGroup.getFiles().get(0))
                    : files.get(0);
        }

        if (selected != null && !selected.isBlank()) {
            Path resolved = root.resolve(selected).normalize();
            if (!resolved.startsWith(root) || !Files.isRegularFile(resolved) || !files.contains(selected.replace('\\', '/'))) {
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
        if (lower.endsWith(".py")) {
            return "python";
        }
        if (lower.endsWith(".js")) {
            return "javascript";
        }
        if (lower.endsWith(".ts")) {
            return "typescript";
        }
        if (lower.endsWith(".go")) {
            return "go";
        }
        if (lower.endsWith(".kt")) {
            return "kotlin";
        }
        if (lower.endsWith(".cs")) {
            return "csharp";
        }
        if (lower.endsWith(".rs")) {
            return "rust";
        }
        if (lower.endsWith(".php")) {
            return "php";
        }
        if (lower.endsWith(".rb")) {
            return "ruby";
        }
        return "text";
    }
}
