package com.agentic.chatbot.service;

import com.agentic.chatbot.model.SiteStyle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SiteStyleService {

    private final Path outputDir;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public SiteStyleService(@Value("${agentic.output-dir:generated-app}") String outputDir) {
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
    }

    public Path styleFile() {
        return outputDir.resolve("SITE_STYLE.json");
    }

    private static final Pattern HEX = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

    public SiteStyle load() {
        try {
            Path f = styleFile();
            if (Files.exists(f)) {
                SiteStyle s = mapper.readValue(Files.readString(f), SiteStyle.class);
                if (isLegacyBlue(s)) {
                    // Old Google-blue defaults: move to the homepage palette so login looks like home.
                    return matchHomepage(s);
                }
                return s;
            }
        } catch (Exception ignored) {
        }
        return matchHomepage(new SiteStyle());
    }

    private static boolean isLegacyBlue(SiteStyle s) {
        String accent = s.getAccentColor() == null ? "" : s.getAccentColor().trim().toLowerCase(Locale.ROOT);
        String bg = s.getBackgroundColor() == null ? "" : s.getBackgroundColor().trim().toLowerCase(Locale.ROOT);
        return accent.equals("#1a73e8") && (bg.equals("#f0f4f9") || bg.equals("#f8fafc") || bg.equals("#ffffff"));
    }

    /**
     * Applies the colours (and font) of the generated homepage to the style. Reads the :root tokens of
     * templates/home.html when it exists; otherwise falls back to the BOT homepage palette (style.css).
     */
    public SiteStyle matchHomepage(SiteStyle s) {
        SiteStyle bot = new SiteStyle();
        String bg = bot.getBackgroundColor();
        String card = bot.getCardColor();
        String accent = bot.getAccentColor();
        String text = bot.getTextColor();
        String footer = bot.getFooterColor();
        String font = bot.getFontFamily();
        try {
            Path home = outputDir.resolve("src/main/resources/templates/home.html");
            if (Files.exists(home)) {
                String html = Files.readString(home, StandardCharsets.UTF_8);
                Matcher root = Pattern.compile(":root\\s*\\{([^}]*)\\}").matcher(html);
                if (root.find()) {
                    String block = root.group(1);
                    bg = token(block, "bg", bg);
                    card = token(block, "card", card);
                    accent = token(block, "accent", accent);
                    text = firstToken(block, text, "ink", "text");
                    footer = firstToken(block, footer, "footer", "dark");
                }
                Matcher ff = Pattern.compile("body\\s*\\{[^}]*font-family:\\s*([^;}]+)").matcher(html);
                if (ff.find()) {
                    font = ff.group(1).trim().replace("\"", "'");
                }
            }
        } catch (Exception ignored) {
        }
        s.setBackgroundColor(bg);
        s.setCardColor(card);
        s.setAccentColor(accent);
        s.setTextColor(text);
        s.setFooterColor(footer);
        s.setFontFamily(font);
        return s;
    }

    private static String firstToken(String block, String fallback, String... names) {
        for (String n : names) {
            String v = token(block, n, null);
            if (v != null) {
                return v;
            }
        }
        return fallback;
    }

    private static String token(String block, String name, String fallback) {
        Matcher m = Pattern.compile("--" + Pattern.quote(name) + "\\s*:\\s*(#[0-9a-fA-F]{3,6})\\b").matcher(block);
        if (m.find() && HEX.matcher(m.group(1)).matches()) {
            return m.group(1);
        }
        return fallback;
    }

    public void save(SiteStyle style) throws IOException {
        Files.createDirectories(outputDir);
        mapper.writeValue(styleFile().toFile(), style);
    }

    public String storeUpload(MultipartFile file, String kind) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Path uploads = outputDir.resolve("uploads");
        Files.createDirectories(uploads);
        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        String name = kind + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = uploads.resolve(name);
        file.transferTo(dest.toFile());
        return "/uploads/" + name;
    }

    public Path uploadsDir() {
        return outputDir.resolve("uploads");
    }

    public Path outputDir() {
        return outputDir;
    }
}
