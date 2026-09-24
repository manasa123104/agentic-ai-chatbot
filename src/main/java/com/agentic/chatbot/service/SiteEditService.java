package com.agentic.chatbot.service;

import com.agentic.chatbot.generator.ScreenCodeGenerator;
import com.agentic.chatbot.generator.SiteThemePack;
import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.model.SiteStyle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Applies natural-language edit instructions to the already-generated website,
 * using the previous user story as context.
 */
@Service
public class SiteEditService {

    private static final Pattern EDIT_PATTERN = Pattern.compile(
            "(?i)\\b(edit|change|update|modify|tweak|adjust|restyle|recolor|redesign)\\b"
                    + "|\\b(make it|make the|set the|switch to|use a|use the|apply)\\b"
                    + "|\\b(color|colour|font|background|accent|image|images|theme|dark mode|light mode)\\b"
                    + "|\\b(pink|blue|red|green|orange|purple|teal|yellow|gold)\\b"
                    + "|\\bi want (the )?(page|site|website|it) to be\\b");

    private static final Pattern NEW_SITE_PATTERN = Pattern.compile(
            "(?i)as a .+\\bi want\\b.+(website|page|login|dashboard|booking)");

    private static final Pattern COLOR_EDIT_PATTERN = Pattern.compile(
            "(?i)\\b(pink|blue|red|green|orange|purple|violet|teal|turquoise|yellow|gold|black|white)\\b"
                    + "|\\b(be|to|in|with)\\s+(a\\s+)?(pink|blue|red|green|orange|purple|teal|yellow|gold)\\b"
                    + "|\\bi want .{0,40}\\b(pink|blue|red|green|orange|purple|teal)\\b");


    private final SiteStyleService styleService;
    private final ScreenCodeGenerator screenCodeGenerator;
    private final Path outputDir;

    public SiteEditService(
            SiteStyleService styleService,
            ScreenCodeGenerator screenCodeGenerator,
            @Value("${agentic.output-dir:generated-app}") String outputDir) {
        this.styleService = styleService;
        this.screenCodeGenerator = screenCodeGenerator;
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
    }

    public boolean hasGeneratedSite() {
        return Files.exists(outputDir.resolve("SITE_META.json"))
                || Files.exists(outputDir.resolve("src/main/resources/templates/home.html"))
                || Files.exists(outputDir.resolve("src/main/resources/templates/login.html"));
    }

    public boolean looksLikeEdit(String message) {
        if (message == null || message.isBlank() || !hasGeneratedSite()) {
            return false;
        }
        String m = message.trim();

        // Full new site / page stories should generate, not edit
        if (NEW_SITE_PATTERN.matcher(m).find()
                && !Pattern.compile("(?i)\\b(edit|change|update|modify)\\s+(the\\s+)?(website|site|page|color|font|image)").matcher(m).find()) {
            return false;
        }

        // Explicit edit / restyle commands
        if (Pattern.compile("(?i)\\b(edit|change|update|modify|restyle|recolor|redesign)\\b").matcher(m).find()) {
            return true;
        }
        if (Pattern.compile("(?i)^(make it|make the|set the|switch to|use )\\b").matcher(m).find()) {
            return true;
        }
        // "I want the page to be pink" / "make it blue"
        if (COLOR_EDIT_PATTERN.matcher(m).find() && m.length() < 200 && !NEW_SITE_PATTERN.matcher(m).find()) {
            return true;
        }
        // Short style-only instructions
        return EDIT_PATTERN.matcher(m).find() && m.length() < 160 && !NEW_SITE_PATTERN.matcher(m).find();
    }

    public PipelineResponse apply(String instruction, String previousStory) {
        PipelineResponse response = new PipelineResponse();
        List<BotStepLog> steps = new ArrayList<>();
        long start = System.currentTimeMillis();

        try {
            String base = previousStory == null ? "" : previousStory.trim();
            SiteThemePack.Theme theme = resolveEditTheme(base, instruction);
            boolean imageEdit = SiteThemePack.requestsImageTheme(instruction) || extractImageUrl(instruction) != null;

            SiteStyle style = styleService.load();
            applyThemeToStyle(style, theme, instruction);
            styleService.save(style);

            boolean rebuild = needsRebuild(instruction) || imageEdit;
            List<String> files = new ArrayList<>();
            files.add("SITE_STYLE.json");

            if (rebuild) {
                String combined = base.isBlank() ? instruction : base + "\n\nEdit request: " + instruction;
                List<Path> written = screenCodeGenerator.generate(combined, outputDir);
                // Re-resolve so instruction images + colors win over regenerated defaults
                theme = resolveEditTheme(base, instruction);
                style = styleService.load();
                applyThemeToStyle(style, theme, instruction);
                styleService.save(style);
                patchGeneratedHtml(theme);
                patchImagesIntoBoxes(theme, instruction);
                for (Path p : written) {
                    files.add(outputDir.relativize(p).toString().replace('\\', '/'));
                }
            } else {
                patchGeneratedHtml(theme);
                patchImagesIntoBoxes(theme, instruction);
                files.add("src/main/resources/templates/*.html (colors + images in boxes)");
            }

            long ms = System.currentTimeMillis() - start;
            String note = " Accent " + theme.accent()
                    + (imageEdit ? "; theme images placed in every card box (" + theme.kind().name().toLowerCase() + ")." : ".");
            steps.add(new BotStepLog(
                    "Site editor — apply chat instruction",
                    true,
                    "Updated the website from your instruction." + note,
                    files,
                    ms
            ));

            String preview = Files.exists(outputDir.resolve("src/main/resources/templates/home.html"))
                    ? "/site/home"
                    : readPreviewFromMeta();

            response.setSuccess(true);
            response.setPreviewUrl(preview);
            response.setCodeUrl("/code");
            response.setOutputDirectory(outputDir.toString());
            response.setMessage("Applied your edit." + note + " Preview: " + preview + " · Code: /code");
            response.setSteps(steps);
            return response;
        } catch (Exception e) {
            steps.add(new BotStepLog(
                    "Site editor — apply chat instruction",
                    false,
                    "Could not apply edit: " + e.getMessage(),
                    List.of(),
                    System.currentTimeMillis() - start
            ));
            response.setSuccess(false);
            response.setMessage("Edit failed: " + e.getMessage());
            response.setSteps(steps);
            return response;
        }
    }

    /**
     * Colors/fonts from instruction; images from instruction theme when user asks for them,
     * otherwise keep previous site images.
     */
    private SiteThemePack.Theme resolveEditTheme(String baseStory, String instruction) {
        SiteThemePack.Theme context = SiteThemePack.fromStory(
                baseStory == null || baseStory.isBlank() ? instruction : baseStory);
        SiteThemePack.Theme styled = SiteThemePack.applyStyleOverrides(context, instruction);
        if (SiteThemePack.requestsImageTheme(instruction)) {
            SiteThemePack.Theme imageTheme = SiteThemePack.fromStory(instruction);
            // If instruction alone is too vague (TECH) but mentions a theme word, prefer combined
            if (imageTheme.kind() == SiteThemePack.Kind.TECH
                    && !instruction.toLowerCase(Locale.ROOT).contains("tech")) {
                imageTheme = SiteThemePack.fromStory(
                        (baseStory == null ? "" : baseStory) + "\n" + instruction);
            }
            styled = SiteThemePack.withImagesFrom(styled, imageTheme);
        }
        String customUrl = extractImageUrl(instruction);
        if (customUrl != null) {
            String[] cards = new String[]{customUrl, customUrl, customUrl, customUrl, customUrl, customUrl};
            styled = new SiteThemePack.Theme(
                    styled.kind(), styled.accent(), styled.soft(), styled.dark(), styled.bg(), styled.ink(),
                    styled.muted(), styled.fontFamily(), styled.googleFontsHref(),
                    customUrl, cards,
                    styled.homeHeadline(), styled.homeLead(), styled.servicesLabel(),
                    styled.serviceTitles(), styled.serviceBlurbs(), styled.serviceLinks()
            );
        }
        return styled;
    }

    private static String extractImageUrl(String instruction) {
        if (instruction == null) {
            return null;
        }
        var m = Pattern.compile("(?i)(?:image|photo|picture|url)\\s*[:=]?\\s*(https?://\\S+)").matcher(instruction);
        if (m.find()) {
            return m.group(1).replaceAll("[)\\],.]+$", "");
        }
        m = Pattern.compile("(https?://\\S+\\.(?:jpg|jpeg|png|webp|gif)(?:\\?\\S*)?)", Pattern.CASE_INSENSITIVE)
                .matcher(instruction);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private boolean needsRebuild(String instruction) {
        return SiteThemePack.requestsImageTheme(instruction)
                || containsAny(instruction.toLowerCase(Locale.ROOT),
                "redesign", "layout", "pages", "multi-page", "rebuild", "regenerate");
    }

    private void applyThemeToStyle(SiteStyle style, SiteThemePack.Theme theme, String instruction) {
        style.setAccentColor(theme.accent());
        style.setBackgroundColor(theme.bg());
        style.setTextColor(theme.ink());
        style.setFooterColor(theme.dark());
        style.setFontFamily(theme.fontFamily());
        style.setHeroImageUrl(theme.heroImage());
        if (style.getSubtitle() == null || style.getSubtitle().isBlank()
                || style.getSubtitle().toLowerCase(Locale.ROOT).contains("theme")) {
            style.setSubtitle("Edited from chat · " + theme.kind().name().toLowerCase());
        }
        // Font size cues
        String lower = instruction.toLowerCase(Locale.ROOT);
        if (lower.contains("larger font") || lower.contains("bigger font") || lower.contains("increase font")) {
            style.setFontSize("18px");
            style.setHeadingSize("2.4rem");
        } else if (lower.contains("smaller font") || lower.contains("reduce font")) {
            style.setFontSize("14px");
            style.setHeadingSize("1.6rem");
        }
    }

    private void patchGeneratedHtml(SiteThemePack.Theme theme) throws Exception {
        Path templates = outputDir.resolve("src/main/resources/templates");
        if (!Files.isDirectory(templates)) {
            return;
        }
        try (Stream<Path> stream = Files.list(templates)) {
            List<Path> htmlFiles = stream.filter(p -> p.getFileName().toString().endsWith(".html")).toList();
            for (Path file : htmlFiles) {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                String updated = html
                        .replaceAll("(--accent:)\\s*#[0-9a-fA-F]{3,8}", "$1" + theme.accent())
                        .replaceAll("(--bg:)\\s*#[0-9a-fA-F]{3,8}", "$1" + theme.bg())
                        .replaceAll("(--ink:)\\s*#[0-9a-fA-F]{3,8}", "$1" + theme.ink())
                        .replaceAll("(--soft:)\\s*#[0-9a-fA-F]{3,8}", "$1" + theme.soft())
                        .replaceAll("(--dark:)\\s*#[0-9a-fA-F]{3,8}", "$1" + theme.dark())
                        .replaceAll("(--muted:)\\s*#[0-9a-fA-F]{3,8}", "$1" + theme.muted());
                updated = updated.replaceAll(
                        "(body\\s*\\{[^}]*font-family:)[^;]+;",
                        "$1" + theme.fontFamily() + ";");
                if (!updated.equals(html)) {
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                }
            }
        }
    }

    /** Put theme (or custom) images into every card box + hero on all pages. */
    private void patchImagesIntoBoxes(SiteThemePack.Theme theme, String instruction) throws Exception {
        Path templates = outputDir.resolve("src/main/resources/templates");
        if (!Files.isDirectory(templates)) {
            return;
        }
        String[] imgs = theme.cardImages();
        if (imgs == null || imgs.length == 0) {
            return;
        }
        String hero = theme.heroImage();
        try (Stream<Path> stream = Files.list(templates)) {
            for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".html")).toList()) {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                String updated = html;

                if (!updated.contains(".card-media")) {
                    updated = updated.replace(
                            ".card-img {",
                            ".card-media{width:100%;height:180px;overflow:hidden;background:#e2e8f0}"
                                    + ".card-media img.card-img,img.card-img{width:100%;height:180px;object-fit:cover;object-position:center;display:block}"
                                    + ".card-img {");
                }

                updated = replaceCardImageSrcs(updated, imgs);

                if (hero != null && !hero.isBlank()) {
                    String safeHero = java.util.regex.Matcher.quoteReplacement(hero);
                    updated = updated.replaceAll(
                            "(<div class=\"visual\">\\s*<img\\s+src=\")[^\"]+(\")",
                            "$1" + safeHero + "$2");
                    updated = updated.replaceAll(
                            "(background-image:url\\(')[^']+('\\))",
                            "$1" + safeHero + "$2");
                }

                updated = updated.replaceAll(
                        "(<a class=\"card\"[^>]*>)\\s*(<img class=\"card-img\")",
                        "$1<div class=\"card-media\">$2");
                updated = updated.replaceAll(
                        "(class=\"card-img\"[^>]*>)\\s*(<div class=\"card-body\")",
                        "$1</div>$2");

                if (!updated.equals(html)) {
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                }
            }
        }
    }

    private static String replaceCardImageSrcs(String html, String[] imgs) {
        String result = html;
        // class before src
        result = replaceCycling(result,
                Pattern.compile("(<img[^>]*class=\"card-img\"[^>]*src=\")([^\"]+)(\")", Pattern.CASE_INSENSITIVE),
                imgs);
        // src before class
        result = replaceCycling(result,
                Pattern.compile("(<img[^>]*src=\")([^\"]+)(\"[^>]*class=\"card-img\")", Pattern.CASE_INSENSITIVE),
                imgs);
        return result;
    }

    private static String replaceCycling(String html, Pattern pattern, String[] imgs) {
        StringBuffer sb = new StringBuffer();
        java.util.regex.Matcher m = pattern.matcher(html);
        int i = 0;
        while (m.find()) {
            String src = imgs[Math.floorMod(i++, imgs.length)];
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group(1) + src + m.group(3)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String readPreviewFromMeta() {
        try {
            Path meta = outputDir.resolve("SITE_META.json");
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
        return "/site/home";
    }

    private static boolean containsAny(String s, String... words) {
        for (String w : words) {
            if (s.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
