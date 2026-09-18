package com.agentic.chatbot.web;

import com.agentic.chatbot.model.SiteStyle;
import com.agentic.chatbot.service.SiteStyleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class SiteEditorController {

    private final SiteStyleService styleService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String googleClientId;

    public SiteEditorController(
            SiteStyleService styleService,
            @Value("${agentic.google.client-id:}") String googleClientId) {
        this.styleService = styleService;
        this.googleClientId = googleClientId == null ? "" : googleClientId;
    }

    @GetMapping("/edit")
    public String editDefault() throws Exception {
        String page = readCurrentPage();
        return "redirect:/edit/" + page;
    }

    @GetMapping("/edit/{page}")
    public String edit(@PathVariable String page, Model model) {
        SiteStyle style = styleService.load();
        if (style.getHeading() == null || style.getHeading().isBlank()) {
            style.setHeading(capitalize(page));
        }
        model.addAttribute("style", style);
        model.addAttribute("page", page);
        model.addAttribute("previewUrl", "/site/" + page);
        model.addAttribute("googleConfigured", !googleClientId.isBlank());
        return "editor";
    }

    @PostMapping("/edit/{page}")
    public String save(
            @PathVariable String page,
            @ModelAttribute SiteStyle style,
            @RequestParam(required = false) MultipartFile logoFile,
            @RequestParam(required = false) MultipartFile heroFile,
            @RequestParam(required = false) MultipartFile backgroundFile,
            @RequestParam(value = "showGoogleSignIn", required = false) String showGoogle,
            @RequestParam(value = "showFooter", required = false) String showFooter,
            RedirectAttributes ra) throws Exception {
        SiteStyle existing = styleService.load();

        style.setShowGoogleSignIn(showGoogle != null);
        style.setShowFooter(showFooter != null);

        String logo = styleService.storeUpload(logoFile, "logo");
        if (logo != null) {
            style.setLogoUrl(logo);
        } else {
            style.setLogoUrl(existing.getLogoUrl());
        }
        String hero = styleService.storeUpload(heroFile, "hero");
        if (hero != null) {
            style.setHeroImageUrl(hero);
        } else if (style.getHeroImageUrl() == null || style.getHeroImageUrl().isBlank()) {
            style.setHeroImageUrl(existing.getHeroImageUrl());
        }
        String bg = styleService.storeUpload(backgroundFile, "bg");
        if (bg != null) {
            style.setBackgroundImageUrl(bg);
        } else if (style.getBackgroundImageUrl() == null || style.getBackgroundImageUrl().isBlank()) {
            style.setBackgroundImageUrl(existing.getBackgroundImageUrl());
        }

        // Keep URL fields if user typed them in the form (overwrite blank upload keep)
        if (style.getLogoUrl() == null) {
            style.setLogoUrl("");
        }

        styleService.save(style);
        ra.addFlashAttribute("saved", true);
        return "redirect:/edit/" + page;
    }

    private String readCurrentPage() throws Exception {
        Path meta = styleService.outputDir().resolve("SITE_META.json");
        if (Files.exists(meta)) {
            JsonNode node = mapper.readTree(Files.readString(meta));
            String path = node.path("pagePath").asText("/login");
            return path.startsWith("/") ? path.substring(1) : path;
        }
        return "login";
    }

    private static String capitalize(String s) {
        if (s == null || s.isBlank()) {
            return "Page";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
