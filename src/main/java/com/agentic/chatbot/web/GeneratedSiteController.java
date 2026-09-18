package com.agentic.chatbot.web;

import com.agentic.chatbot.generator.StoryAnalyzer;
import com.agentic.chatbot.model.SiteStyle;
import com.agentic.chatbot.service.SiteStyleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Controller
public class GeneratedSiteController {

    private final SiteStyleService styleService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GeneratedSiteController(SiteStyleService styleService) {
        this.styleService = styleService;
    }

    @GetMapping(value = "/site/{page}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String showPage(@PathVariable String page, HttpSession session) throws Exception {
        Path template = styleService.outputDir().resolve("src/main/resources/templates/" + page + ".html");
        if (!Files.exists(template)) {
            return missingPage(page);
        }
        String html = Files.readString(template, StandardCharsets.UTF_8);
        Object user = session.getAttribute("googleUser");
        Object email = session.getAttribute("googleEmail");
        return preparePreviewHtml(html, page, null, user, email);
    }

    @PostMapping(value = "/site/{page}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String submitPage(
            @PathVariable String page,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field2,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            HttpSession session) throws Exception {
        String redirect = readRedirectPath();
        if (redirect == null) {
            Object story = session.getAttribute("lastUserStory");
            if (story instanceof String s) {
                redirect = StoryAnalyzer.analyze(s).redirectPath();
            }
        }

        Path loginTemplate = styleService.outputDir().resolve("src/main/resources/templates/" + page + ".html");
        String raw = Files.exists(loginTemplate) ? Files.readString(loginTemplate, StandardCharsets.UTF_8) : "";
        boolean looksLikeLogin = raw.toLowerCase().contains("password") || page.equalsIgnoreCase("login")
                || page.equalsIgnoreCase("register");

        if (looksLikeLogin && (field1 == null || field1.isBlank() || field2 == null || field2.isBlank())) {
            return preparePreviewHtml(raw, page, "Username and password are required", null, null);
        }

        // Contact form posts to success page
        String dest;
        if ("contact".equalsIgnoreCase(page) && !looksLikeLogin) {
            dest = "contact-success";
        } else if (redirect != null && !redirect.isBlank() && !redirect.equalsIgnoreCase("/" + page)) {
            dest = redirect.startsWith("/") ? redirect.substring(1) : redirect;
        } else {
            Path success = styleService.outputDir().resolve("src/main/resources/templates/" + page + "-success.html");
            dest = Files.exists(success) ? page + "-success" : page;
        }

        return """
                <!DOCTYPE html>
                <html><head>
                  <meta charset="UTF-8"/>
                  <meta http-equiv="refresh" content="0;url=/site/%s"/>
                  <title>Opening your webpage...</title>
                </head>
                <body>
                  <p>Opening generated webpage <a href="/site/%s">/site/%s</a>...</p>
                  <script>window.location.replace('/site/%s');</script>
                </body></html>
                """.formatted(dest, dest, dest, dest);
    }

    private String readRedirectPath() {
        try {
            Path meta = styleService.outputDir().resolve("SITE_META.json");
            if (!Files.exists(meta)) {
                return null;
            }
            JsonNode node = mapper.readTree(Files.readString(meta));
            JsonNode redirect = node.get("redirectPath");
            return redirect == null || redirect.isNull() ? null : redirect.asText();
        } catch (Exception e) {
            return null;
        }
    }

    private String preparePreviewHtml(String html, String page, String error, Object googleUser, Object googleEmail) {
        SiteStyle style = styleService.load();

        String result = html
                .replace("th:action=\"@{/" + page + "}\"", "action=\"/site/" + page + "\"")
                .replace("th:action=\"@{/" + page + "/}\"", "action=\"/site/" + page + "\"")
                .replaceAll("th:action=\"@\\{/[^\\\"]+}\"", "action=\"/site/" + page + "\"")
                .replaceAll("th:object=\"\\$\\{form}\"", "")
                .replaceAll("th:field=\"\\*\\{field1}\"", "name=\"field1\"")
                .replaceAll("th:field=\"\\*\\{field2}\"", "name=\"field2\"")
                .replaceAll("th:field=\"\\*\\{field3}\"", "name=\"field3\"")
                .replaceAll("th:field=\"\\*\\{field4}\"", "name=\"field4\"")
                .replaceAll("th:value=\"\\*\\{field3}\"", "name=\"field3\"")
                .replaceAll("th:value=\"\\*\\{field4}\"", "name=\"field4\"")
                .replaceAll("th:text=\"\\$\\{title}\"", "")
                .replaceAll("th:text=\"\\$\\{story}\"", "")
                .replaceAll("th:text=\"\\$\\{message}\"", "")
                .replaceAll("th:text=\"\\$\\{error}\"", "")
                .replaceAll("th:href=\"@\\{/[^\\\"]+}\"", "href=\"/results\"")
                .replace("xmlns:th=\"http://www.thymeleaf.org\"", "");

        // Do not overwrite page titles from SITE_STYLE — that turned dashboards into "Sign in"
        if (style.getSubtitle() != null && !style.getSubtitle().isBlank()
                && result.contains("class=\"subtitle\"")) {
            result = result.replaceFirst(
                    "(?s)(<p class=\"subtitle\"[^>]*>)(.*?)(</p>)",
                    "$1" + escape(style.getSubtitle()) + "$3");
        }

        if (error != null) {
            result = result.replace("class=\"error\" th:if=\"${error}\"", "class=\"error\"");
            result = result.replace(
                    "<div class=\"error\" th:if=\"${error}\" th:text=\"${error}\"></div>",
                    "<div class=\"error\">" + escape(error) + "</div>");
            if (!result.contains(error)) {
                result = result.replace("<div class=\"error\"></div>", "<div class=\"error\">" + escape(error) + "</div>");
            }
        } else {
            result = result.replaceAll("(?s)<div class=\"error\"[^>]*>.*?</div>", "");
        }

        String themeCss = buildThemeCss(style);
        String extras = buildExtras(style, page, googleUser, googleEmail);

        if (result.contains("</head>")) {
            result = result.replace("</head>", themeCss + "</head>");
        } else {
            result = themeCss + result;
        }

        String banner = """
                <div style="position:fixed;top:12px;right:12px;z-index:9999;display:flex;gap:.5rem;font:13px Roboto,Arial,sans-serif">
                  <a href="/edit/%s" style="background:#1a73e8;color:#fff;padding:.45rem .75rem;border-radius:999px;text-decoration:none;box-shadow:0 1px 3px rgba(0,0,0,.2)">Edit site</a>
                  <a href="/" style="background:#fff;color:#1a73e8;border:1px solid #dadce0;padding:.45rem .75rem;border-radius:999px;text-decoration:none">New story</a>
                </div>
                """.formatted(page);

        int bodyIdx = result.toLowerCase().indexOf("<body");
        if (bodyIdx >= 0) {
            int close = result.indexOf('>', bodyIdx);
            result = result.substring(0, close + 1) + banner + extras + result.substring(close + 1);
        } else {
            result = banner + extras + result;
        }

        // Inject Google button + footer before </body> if markers missing
        if (!result.contains("google-btn") && style.isShowGoogleSignIn()
                && (page.equalsIgnoreCase("login") || page.equalsIgnoreCase("register") || result.toLowerCase().contains("password"))) {
            String google = googleButtonHtml(page);
            result = result.replaceFirst("(?i)</form>", "</form>" + google);
        }
        if (style.isShowFooter() && !result.contains("site-footer")) {
            result = result.replaceFirst("(?i)</body>", footerHtml(style) + "</body>");
        }

        return result;
    }

    private String buildThemeCss(SiteStyle s) {
        String bgImage = (s.getBackgroundImageUrl() != null && !s.getBackgroundImageUrl().isBlank())
                ? "body{background-image:linear-gradient(rgba(255,255,255,.72),rgba(255,255,255,.72)),url('"
                + s.getBackgroundImageUrl() + "')!important;background-size:cover!important;background-position:center!important;}"
                : "";
        // Only set CSS variables so Google/company page layouts keep their structure
        return """
                <style id="site-theme">
                  :root {
                    --bg: %s;
                    --card: %s;
                    --accent: %s;
                    --text: %s;
                    --ink: %s;
                    --footer: %s;
                  }
                  body { font-family: %s; font-size: %s; color: %s; }
                  %s
                  .hero-img, .logo-img { max-width: 100%%; display: block; margin: 0 auto 1rem; border-radius: 12px; }
                  .logo-img { max-height: 64px; width: auto; }
                </style>
                """.formatted(
                s.getBackgroundColor(), s.getCardColor(), s.getAccentColor(), s.getTextColor(), s.getTextColor(),
                s.getFooterColor(), s.getFontFamily(), s.getFontSize(), s.getTextColor(), bgImage
        );
    }

    private String buildExtras(SiteStyle style, String page, Object googleUser, Object googleEmail) {
        StringBuilder sb = new StringBuilder();
        if (style.getLogoUrl() != null && !style.getLogoUrl().isBlank()) {
            sb.append("<div style=\"text-align:center;padding:1rem 1rem 0\"><img class=\"logo-img\" src=\"")
                    .append(escape(style.getLogoUrl())).append("\" alt=\"Logo\"/></div>");
        }
        if (style.getHeroImageUrl() != null && !style.getHeroImageUrl().isBlank()) {
            sb.append("<div style=\"max-width:720px;margin:0 auto;padding:0 1rem\"><img class=\"hero-img\" src=\"")
                    .append(escape(style.getHeroImageUrl())).append("\" alt=\"Hero\"/></div>");
        }
        if (googleUser != null) {
            sb.append("<div style=\"max-width:420px;margin:.5rem auto;padding:.6rem 1rem;background:#ecfdf5;color:#065f46;border-radius:8px;text-align:center\">")
                    .append("Signed in with Google as <strong>").append(escape(String.valueOf(googleUser))).append("</strong>");
            if (googleEmail != null) {
                sb.append(" (").append(escape(String.valueOf(googleEmail))).append(")");
            }
            sb.append("</div>");
        }
        return sb.toString();
    }

    private String googleButtonHtml(String page) {
        return """
                <div class="divider">or</div>
                <a class="google-btn" href="/auth/google?page=%s">
                  <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
                    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                  </svg>
                  Sign in with Google
                </a>
                """.formatted(page);
    }

    private String footerHtml(SiteStyle style) {
        return """
                <footer class="site-footer">%s</footer>
                """.formatted(escape(style.getFooterText() == null ? "" : style.getFooterText()));
    }

    private String missingPage(String page) {
        return """
                <!DOCTYPE html><html><body style="font-family:Segoe UI,sans-serif;padding:2rem">
                <h1>Page not found</h1>
                <p>No generated template for <code>%s</code>.</p>
                <p><a href="/">Back to chatbot</a> · <a href="/edit">Edit site</a></p>
                </body></html>
                """.formatted(page);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
