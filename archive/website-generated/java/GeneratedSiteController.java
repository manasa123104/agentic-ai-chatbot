package com.agentic.chatbot.web;

import com.agentic.chatbot.generator.StoryAnalyzer;
import com.agentic.chatbot.model.SiteStyle;
import com.agentic.chatbot.service.SiteStyleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
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
    public String showPage(@PathVariable String page, HttpSession session, HttpServletRequest request) throws Exception {
        String html = loadTemplate(page);
        if (html == null) {
            return missingPage(page);
        }
        Object user = session.getAttribute("googleUser");
        Object email = session.getAttribute("googleEmail");
        return preparePreviewHtml(html, page, null, user, email, RequestOrigin.isHost(request));
    }

    @PostMapping(value = "/site/{page}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String submitPage(
            @PathVariable String page,
            @RequestParam(required = false) String field1,
            @RequestParam(required = false) String field2,
            @RequestParam(required = false) String field3,
            @RequestParam(required = false) String field4,
            HttpSession session,
            HttpServletRequest request) throws Exception {
        String redirect = readRedirectPath();
        if (redirect == null) {
            Object story = session.getAttribute("lastUserStory");
            if (story instanceof String s) {
                redirect = StoryAnalyzer.analyze(s).redirectPath();
            }
        }

        String loaded = loadTemplate(page);
        String raw = loaded == null ? "" : loaded;
        boolean looksLikeLogin = raw.toLowerCase().contains("password") || page.equalsIgnoreCase("login")
                || page.equalsIgnoreCase("register");

        if (looksLikeLogin && (field1 == null || field1.isBlank() || field2 == null || field2.isBlank())) {
            return preparePreviewHtml(raw, page, "Username and password are required", null, null, RequestOrigin.isHost(request));
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

    /**
     * The generated template for the page, or - for sign-in pages that were never generated - a built-in
     * login page that uses the same theme tokens as the rest of the site. Null when there is nothing to show.
     */
    private String loadTemplate(String page) throws Exception {
        Path template = styleService.outputDir().resolve("src/main/resources/templates/" + page + ".html");
        if (Files.exists(template)) {
            return Files.readString(template, StandardCharsets.UTF_8);
        }
        if (isAuthPage(page)) {
            return fallbackLoginPage(page);
        }
        return null;
    }

    private static boolean isAuthPage(String page) {
        String p = page == null ? "" : page.toLowerCase();
        return p.equals("login") || p.equals("signin") || p.equals("sign-in") || p.equals("register")
                || p.equals("signup") || p.equals("sign-up");
    }

    private String fallbackLoginPage(String page) {
        boolean register = page.toLowerCase().contains("regist") || page.toLowerCase().contains("sign-up")
                || page.equalsIgnoreCase("signup");
        String title = register ? "Create account" : "Sign in";
        String button = register ? "Create account" : "Next";
        String homeLink = Files.exists(styleService.outputDir().resolve("src/main/resources/templates/home.html"))
                ? "/site/home" : "/";
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>@@TITLE@@</title>
                  <meta name="bot-skin" content="home-warm-paper-teal-v3"/>
                  <link href="https://fonts.googleapis.com/css2?family=Source+Serif+4:opsz,wght@8..60,500;8..60,600&family=Public+Sans:wght@400;500;600;700&display=swap" rel="stylesheet"/>
                  <style>
                    :root { --bg:#ece8e0; --card:#fbf9f5; --accent:#2c5d68; --text:#1d2430; --muted:#5c636b; --line:#d8d3c8; --footer:#f6f3ee; }
                    * { box-sizing: border-box; }
                    body { margin:0; min-height:100vh; display:flex; flex-direction:column; background:var(--bg); color:var(--text);
                           font-family:'Public Sans','Segoe UI',system-ui,sans-serif; }
                    .wrap { flex:1; display:grid; place-items:center; padding:2rem 1rem 4rem; }
                    .card { width:min(440px,100%); background:var(--card); border:1px solid var(--line); border-radius:12px; padding:40px 36px 32px; }
                    h1 { margin:0; text-align:center; font:600 1.6rem 'Source Serif 4',Georgia,serif; }
                    .subtitle { text-align:center; color:var(--muted); margin:.5rem 0 1.5rem; }
                    .field { margin-bottom:1rem; }
                    label { display:block; font-size:.85rem; font-weight:600; color:var(--muted); margin-bottom:.3rem; }
                    input { width:100%; padding:.7rem .8rem; border:1px solid var(--line); border-radius:8px; font:inherit; background:#fff; color:var(--text); }
                    input:focus { outline:2px solid var(--accent); outline-offset:1px; border-color:var(--accent); }
                    .error { color:#9b2c2c; font-size:.88rem; margin:0 0 .75rem; }
                    .actions { display:flex; justify-content:space-between; align-items:center; gap:1rem; margin-top:1.5rem; }
                    .link { color:var(--accent); font-weight:600; text-decoration:none; font-size:.9rem; }
                    .link:hover { text-decoration:underline; }
                    button { background:var(--accent); color:#fff; border:1px solid var(--accent); border-radius:8px; padding:.6rem 1.3rem; font-family:inherit; font-weight:600; font-size:.95rem; cursor:pointer; }
                    .divider { display:flex; align-items:center; gap:.75rem; margin:1.4rem 0 .9rem; color:var(--muted); font-size:.8rem; }
                    .divider::before, .divider::after { content:""; flex:1; height:1px; background:var(--line); }
                    .google-btn { display:flex; align-items:center; justify-content:center; gap:.65rem; padding:.6rem; border:1px solid var(--line);
                                  border-radius:8px; background:#fff; color:var(--text); font-weight:600; text-decoration:none; }
                    .site-footer { padding:1rem 1.5rem; background:var(--footer); color:var(--muted); font-size:.8rem; border-top:1px solid var(--line); }
                  </style>
                </head>
                <body data-skin="bot-home">
                <div class="wrap">
                  <div class="card">
                    <h1>@@TITLE@@</h1>
                    <p class="subtitle">to continue to your workspace</p>
                    <div class="error"></div>
                    <form action="/site/@@PAGE@@" method="post">
                      <div class="field"><label for="field1">Email or username</label>
                        <input id="field1" name="field1" autocomplete="username"/></div>
                      <div class="field"><label for="field2">Password</label>
                        <input id="field2" name="field2" type="password" autocomplete="current-password"/></div>
                      <div class="actions"><a class="link" href="@@HOME@@">Back to home</a><button type="submit">@@BUTTON@@</button></div>
                    </form>
                  </div>
                </div>
                </body>
                </html>
                """.replace("@@TITLE@@", title).replace("@@PAGE@@", page.replaceAll("[^A-Za-z0-9_-]", ""))
                .replace("@@HOME@@", homeLink).replace("@@BUTTON@@", button);
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

    private String preparePreviewHtml(String html, String page, String error, Object googleUser, Object googleEmail,
                                      boolean host) {
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
        String extras = buildExtras(style, page, googleUser, googleEmail, result);

        if (result.contains("</head>")) {
            result = result.replace("</head>", themeCss + "</head>");
        } else {
            result = themeCss + result;
        }

        String banner = """
                <div style="position:fixed;top:12px;right:12px;z-index:9999;display:flex;flex-wrap:wrap;justify-content:flex-end;gap:.5rem;font:13px 'Public Sans',Segoe UI,Arial,sans-serif">
                  <a href="/code" style="background:#fbf9f5;color:#244a54;border:1px solid #d8d3c8;padding:.45rem .75rem;border-radius:8px;text-decoration:none">Website generated with code</a>
                  <a href="/edit/%s" style="background:#2c5d68;color:#fff;border:1px solid #2c5d68;padding:.45rem .75rem;border-radius:8px;text-decoration:none">Edit site</a>
                  <a href="/download/site.zip" style="background:#fbf9f5;color:#244a54;border:1px solid #d8d3c8;padding:.45rem .75rem;border-radius:8px;text-decoration:none">&#11015; Download ZIP</a>
                  <a href="#" data-site-push data-host="%s" style="background:#222a33;color:#fff;border:1px solid #222a33;padding:.45rem .75rem;border-radius:8px;text-decoration:none">&#11014; Push to GitHub</a>
                  <a href="/" style="background:#fbf9f5;color:#244a54;border:1px solid #d8d3c8;padding:.45rem .75rem;border-radius:8px;text-decoration:none">New story</a>
                </div>
                <script src="/js/sitepush.js" defer></script>
                """.formatted(page, host);

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
        // Override theme tokens so editor / story colors and fonts apply on generated pages
        return """
                <link href="https://fonts.googleapis.com/css2?family=Source+Serif+4:opsz,wght@8..60,500;8..60,600&family=Public+Sans:wght@400;500;600;700&display=swap" rel="stylesheet"/>
                <style id="site-theme">
                  :root {
                    --bg: %s !important;
                    --card: %s !important;
                    --accent: %s !important;
                    --text: %s !important;
                    --ink: %s !important;
                    --footer: %s !important;
                    --dark: %s !important;
                  }
                  body { font-family: %s !important; font-size: %s; color: %s !important; background: %s !important; }
                  .btn-primary, .cta, form button, .btn.btn-primary { background: %s !important; color: #fff !important; }
                  .btn-ghost, a.history-edit { color: %s !important; border-color: %s !important; }
                  a.card:hover, .nav a.active, .nav a:hover { color: %s !important; border-color: %s !important; }
                  .card .go { color: %s !important; }
                  %s
                  .hero-img, .logo-img { max-width: 100%%; display: block; margin: 0 auto 1rem; border-radius: 12px; }
                  .logo-img { max-height: 64px; width: auto; }
                </style>
                """.formatted(
                s.getBackgroundColor(), s.getCardColor(), s.getAccentColor(), s.getTextColor(), s.getTextColor(),
                s.getFooterColor(), s.getFooterColor(),
                s.getFontFamily(), s.getFontSize(), s.getTextColor(), s.getBackgroundColor(),
                s.getAccentColor(),
                s.getAccentColor(), s.getAccentColor(),
                s.getAccentColor(), s.getAccentColor(),
                s.getAccentColor(),
                bgImage
        );
    }

    private String buildExtras(SiteStyle style, String page, Object googleUser, Object googleEmail, String html) {
        StringBuilder sb = new StringBuilder();
        if (style.getLogoUrl() != null && !style.getLogoUrl().isBlank()) {
            sb.append("<div style=\"text-align:center;padding:1rem 1rem 0\"><img class=\"logo-img\" src=\"")
                    .append(escape(style.getLogoUrl())).append("\" alt=\"Logo\"/></div>");
        }
        boolean hasEmbedded = html != null && (html.contains("class=\"card-img\"") || html.contains("class=\"visual\"")
                || html.contains("card-media"));
        if (!hasEmbedded && style.getHeroImageUrl() != null && !style.getHeroImageUrl().isBlank()) {
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
        SiteStyle s = styleService.load();
        boolean hasHome = Files.exists(styleService.outputDir().resolve("src/main/resources/templates/home.html"));
        String home = hasHome ? "<a href=\"/site/home\">Back to home</a> · " : "";
        return """
                <!DOCTYPE html><html><head><meta charset="UTF-8"/>
                <meta name="viewport" content="width=device-width, initial-scale=1"/></head>
                <body style="margin:0;font-family:%s;background:%s;color:%s;padding:2.5rem 1.5rem">
                <div style="max-width:520px;margin:0 auto;background:%s;border:1px solid #d8d3c8;border-radius:12px;padding:1.75rem">
                <h1 style="margin:0 0 .5rem;font:600 1.5rem 'Source Serif 4',Georgia,serif">Page not found</h1>
                <p>No generated template for <code>%s</code>.</p>
                <p style="color:%s">%s<a href="/" style="color:%s">Back to chatbot</a> · <a href="/edit" style="color:%s">Edit site</a></p>
                </div></body></html>
                """.formatted(s.getFontFamily(), s.getBackgroundColor(), s.getTextColor(), s.getCardColor(),
                escape(page), s.getTextColor(), home, s.getAccentColor(), s.getAccentColor());
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
