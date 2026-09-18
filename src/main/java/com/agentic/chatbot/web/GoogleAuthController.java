package com.agentic.chatbot.web;

import com.agentic.chatbot.generator.StoryAnalyzer;
import com.agentic.chatbot.service.SiteStyleService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Google / Gmail sign-in entry.
 * With GOOGLE_CLIENT_ID set, redirects to Google OAuth.
 * Otherwise uses a demo Gmail sign-in that completes locally (for local preview).
 */
@Controller
public class GoogleAuthController {

    private final SiteStyleService styleService;
    private final String clientId;
    private final String clientSecret;

    public GoogleAuthController(
            SiteStyleService styleService,
            @Value("${agentic.google.client-id:}") String clientId,
            @Value("${agentic.google.client-secret:}") String clientSecret) {
        this.styleService = styleService;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    @GetMapping("/auth/google")
    public String startGoogle(
            @RequestParam(defaultValue = "login") String page,
            HttpSession session) {
        session.setAttribute("oauthReturnPage", page);

        if (!clientId.isBlank() && !clientSecret.isBlank()) {
            String redirectUri = "http://localhost:8080/auth/google/callback";
            String url = "https://accounts.google.com/o/oauth2/v2/auth"
                    + "?client_id=" + clientId
                    + "&redirect_uri=" + java.net.URLEncoder.encode(redirectUri, java.nio.charset.StandardCharsets.UTF_8)
                    + "&response_type=code"
                    + "&scope=" + java.net.URLEncoder.encode("openid email profile", java.nio.charset.StandardCharsets.UTF_8)
                    + "&access_type=online"
                    + "&prompt=select_account";
            return "redirect:" + url;
        }

        // Demo Gmail login (no Google Cloud project required)
        return "redirect:/auth/google/demo";
    }

    @GetMapping("/auth/google/demo")
    public String demoLoginPage(HttpSession session) {
        return "google-demo";
    }

    @GetMapping("/auth/google/demo/complete")
    public String completeDemo(
            @RequestParam(defaultValue = "Demo User") String name,
            @RequestParam(defaultValue = "user@gmail.com") String email,
            HttpSession session) {
        session.setAttribute("googleUser", name);
        session.setAttribute("googleEmail", email);
        String dest = resolveRedirect(session);
        return "redirect:/site/" + dest;
    }

    @GetMapping("/auth/google/callback")
    public String callback(@RequestParam(required = false) String code, HttpSession session) {
        // Full token exchange can be added with client secret; for now mark signed-in
        if (code != null && !code.isBlank()) {
            session.setAttribute("googleUser", "Gmail User");
            session.setAttribute("googleEmail", "signed-in@gmail.com");
        }
        String dest = resolveRedirect(session);
        return "redirect:/site/" + dest;
    }

    private String resolveRedirect(HttpSession session) {
        try {
            var meta = styleService.outputDir().resolve("SITE_META.json");
            if (java.nio.file.Files.exists(meta)) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                        java.nio.file.Files.readString(meta));
                var redirect = node.get("redirectPath");
                if (redirect != null && !redirect.isNull() && !redirect.asText().isBlank()) {
                    String p = redirect.asText();
                    return p.startsWith("/") ? p.substring(1) : p;
                }
                var pagePath = node.get("pagePath");
                if (pagePath != null && !pagePath.isNull()) {
                    String p = pagePath.asText();
                    // After Google sign-in, land on the generated story page (not a blank workspace)
                    return p.startsWith("/") ? p.substring(1) : p;
                }
            }
        } catch (Exception ignored) {
        }
        Object returnPage = session.getAttribute("oauthReturnPage");
        if (returnPage instanceof String s && !s.isBlank()) {
            return s;
        }
        Object story = session.getAttribute("lastUserStory");
        if (story instanceof String s) {
            var a = StoryAnalyzer.analyze(s);
            if (a.redirectPath() != null) {
                String r = a.redirectPath();
                return r.startsWith("/") ? r.substring(1) : r;
            }
            return a.pagePath().startsWith("/") ? a.pagePath().substring(1) : a.pagePath();
        }
        return "dashboard";
    }
}