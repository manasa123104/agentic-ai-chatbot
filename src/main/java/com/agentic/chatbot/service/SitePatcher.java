package com.agentic.chatbot.service;

import com.agentic.chatbot.generator.SiteAgent;
import com.agentic.chatbot.llm.LlmClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Edits the website that was already generated, in place: the chat says "make the hero shorter" or "rename Plans to
 * Pricing" and the HTML under {@code src/main/resources/templates} is changed on disk, so everything else on the site
 * (earlier edits, colours, pictures) stays exactly as it was and successive edits stack.
 *
 * <p>Every change is also written to {@code SITE_EDITS.json}. When a change needs a structural rebuild (a chatbot agent,
 * a new page), the generator rewrites the files; {@link #snapshotLook()} / {@link #restoreLook} put the theme tokens,
 * fonts, hero layout and pictures back, and {@link #replay()} re-applies the recorded edits that still fit.
 *
 * <p>Plain requests are recognised by patterns. Anything else is handed to the connected language model (when there is
 * one), which answers with exact find/replace operations that are checked against the file before they are applied.
 */
@Service
public class SitePatcher {

    private static final Logger log = LoggerFactory.getLogger(SitePatcher.class);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final String EDITS_FILE = "SITE_EDITS.json";
    private static final String CSS_ID = "bot-edits";

    /** What an edit request did. {@code clarify} is a question to ask instead (nothing changed). */
    public record Outcome(boolean handled, List<String> changes, List<String> files, String clarify, String page, String lead) {
        static Outcome none() {
            return new Outcome(false, List.of(), List.of(), null, null, null);
        }

        public boolean changed() {
            return !changes.isEmpty();
        }
    }

    /** The look of the site before a rebuild: theme tokens, fonts, hero layout, pictures, per page. */
    public record Look(Map<String, String> tokens, String fontsLink, String styleJson, Map<String, PageLook> pages) {
    }

    public record PageLook(String heroClass, String heroImage, List<String> cardImages, String logo) {
    }

    private final Path outputDir;
    private final LlmClient llm;

    public SitePatcher(@Value("${agentic.output-dir:generated-app}") String outputDir, LlmClient llm) {
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
        this.llm = llm;
    }

    private Path templates() {
        return outputDir.resolve("src/main/resources/templates");
    }

    /** True when there are generated pages to edit. */
    public boolean hasPages() {
        return !pages().isEmpty();
    }

    private List<Path> pages() {
        if (!Files.isDirectory(templates())) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(templates())) {
            return s.filter(p -> p.getFileName().toString().endsWith(".html")).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static String read(Path p) throws IOException {
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    private static void write(Path p, String s) throws IOException {
        Files.writeString(p, s, StandardCharsets.UTF_8);
    }

    private static String pageName(Path p) {
        String n = p.getFileName().toString();
        return n.substring(0, n.length() - ".html".length());
    }

    // ------------------------------------------------------------------ facts about the current site

    /** Brand of the site on disk ("" when unknown). */
    public String brand() {
        try {
            Path meta = outputDir.resolve("SITE_META.json");
            if (Files.exists(meta)) {
                String b = JSON.readTree(read(meta)).path("brand").asText("").trim();
                if (!b.isEmpty()) {
                    return b;
                }
            }
        } catch (IOException ignored) {
            // falls through
        }
        return "";
    }

    /** Labels of the menu of the home page, in order. */
    public List<String> menuLabels() {
        List<String> out = new ArrayList<>();
        try {
            Path home = templates().resolve("home.html");
            if (Files.exists(home)) {
                Matcher nav = Pattern.compile("(?is)<nav class=\"nav\">(.*?)</nav>").matcher(read(home));
                if (nav.find()) {
                    Matcher a = Pattern.compile("(?is)<a\\b[^>]*>(.*?)</a>").matcher(nav.group(1));
                    while (a.find()) {
                        String t = a.group(1).replaceAll("<[^>]+>", "").trim();
                        if (!t.isEmpty()) {
                            out.add(t);
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // no menu known
        }
        return out;
    }

    /**
     * The request the current site was built from, rebuilt from what is on disk (USER_STORY.md): the original wording
     * without the generator's own notes, plus the brand. "" when there is no generated site.
     */
    public String storedBrief() {
        try {
            Path story = outputDir.resolve("USER_STORY.md");
            if (!Files.exists(story) || !hasPages()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (String para : read(story).split("\\R\\s*\\R")) {
                String p = para.strip();
                if (p.isEmpty() || p.startsWith("#")) {
                    continue;
                }
                if (p.matches("(?s)^(Brand|Service cards|Menu|Page files)\\b.*")) {
                    break;
                }
                if (p.startsWith("Edit request:") || p.startsWith("Edits applied")) {
                    continue;
                }
                sb.append(sb.length() == 0 ? "" : "\n\n").append(p);
            }
            String brief = sb.toString().strip();
            String brand = brand();
            if (!brand.isEmpty() && !brief.contains(brand)) {
                brief += (brief.isEmpty() ? "" : "\n\n") + "The website is called " + brand + ".";
            }
            return brief;
        } catch (IOException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ the edit log

    private List<Map<String, String>> loadLog() {
        try {
            Path f = outputDir.resolve(EDITS_FILE);
            if (Files.exists(f)) {
                return new ArrayList<>(JSON.readValue(read(f), new TypeReference<List<Map<String, String>>>() {
                }));
            }
        } catch (IOException e) {
            log.debug("Could not read {}: {}", EDITS_FILE, e.toString());
        }
        return new ArrayList<>();
    }

    private void saveLog(List<Map<String, String>> edits) throws IOException {
        Path f = outputDir.resolve(EDITS_FILE);
        if (edits.isEmpty()) {
            Files.deleteIfExists(f);
        } else {
            write(f, JSON.writeValueAsString(edits));
        }
    }

    /** A brand-new site starts with a clean slate: edits of the old one must not be replayed onto it. */
    public synchronized void resetEdits() {
        try {
            Files.deleteIfExists(outputDir.resolve(EDITS_FILE));
        } catch (IOException ignored) {
            // nothing to reset
        }
    }

    private static Map<String, String> op(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ applying a request

    private static final class Work {
        final List<String> changes = new ArrayList<>();
        final Set<String> files = new LinkedHashSet<>();
        final List<String> clarify = new ArrayList<>();
        final List<Map<String, String>> log;
        String page;
        String lead;
        boolean consumed;

        Work(List<Map<String, String>> log) {
            this.log = log;
        }
    }

    private static final Pattern STYLE_CUE = Pattern.compile(
            "(?i)\\b(colou?rs?|fonts?|typeface|background|accent|theme|palette|dark mode|light mode|size)\\b");
    private static final Pattern COLOUR = Pattern.compile(
            "(?i)(?:pink|blue|red|green|orange|purple|violet|teal|turquoise|yellow|gold|black|white|dark|light|grey|gray|brown)");
    private static final Pattern PAGE_WORD = Pattern.compile(
            "(?i)\\b(?:to|on|in|at|for)\\s+(?:the\\s+)?([a-z][a-z0-9-]{2,20})\\s+page\\b");
    private static final Pattern STRUCTURAL = Pattern.compile(
            "(?i)\\b(rebuild|regenerate|from scratch|redesign|backend|database|new page|another page|extra page|"
                    + "(?:add|create|make)\\s+(?:a|an|the)?\\s*[a-z-]+\\s+pages?)\\b");

    /**
     * Applies an edit request to the generated pages. {@code allowModel} lets the connected language model try requests
     * the patterns do not know. Not handled means "nothing was changed": the caller can rebuild instead.
     */
    public synchronized Outcome apply(String message, boolean allowModel) {
        if (message == null || message.isBlank() || !hasPages()) {
            return Outcome.none();
        }
        String m = message.strip().replace('\u201c', '"').replace('\u201d', '"').replace('\u2018', '\'').replace('\u2019', '\'');
        Work w = new Work(loadLog());
        try {
            brandRename(m, w);
            if (!w.consumed) {
                headline(m, w);
            }
            if (!w.consumed) {
                heroText(m, w);
            }
            if (!w.consumed) {
                cta(m, w);
            }
            if (!w.consumed) {
                heroSize(m, w);
            }
            if (!w.consumed) {
                removeFromMenu(m, w);
            }
            if (!w.consumed) {
                renameLabel(m, w);
            }
            if (!w.consumed) {
                contactForm(m, w);
            }
            if (w.changes.isEmpty() && w.clarify.isEmpty() && allowModel && llm != null && llm.isAvailable()
                    && !STRUCTURAL.matcher(m).find() && !SiteAgent.wantsAgent(m)) {
                modelEdit(m, w);
            }
            if (!w.changes.isEmpty()) {
                saveLog(w.log);
                noteInStory(message);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Site edit failed: {}", e.toString());
            return Outcome.none();
        }
        if (!w.changes.isEmpty()) {
            return new Outcome(true, w.changes, new ArrayList<>(w.files), null, w.page == null ? "home" : w.page, w.lead);
        }
        if (!w.clarify.isEmpty()) {
            return new Outcome(true, List.of(), List.of(), String.join("\n\n", w.clarify), null, null);
        }
        return Outcome.none();
    }

    // ---- brand

    private static final Pattern BRAND_RENAME = Pattern.compile(
            "(?i)\\b(?:rename|call|change|set|update)\\s+(?:the\\s+|this\\s+|my\\s+|our\\s+)?"
                    + "(?:site|web\\s?site|brand|company|business|shop|store|bakery|restaurant|name)(?:\\s+name)?\\s+"
                    + "(?:to|as|into)\\s+(.{2,60})$");

    private void brandRename(String m, Work w) throws IOException {
        Matcher b = BRAND_RENAME.matcher(m);
        if (!b.find()) {
            return;
        }
        String fresh = value(b.group(1));
        if (COLOUR.matcher(fresh).matches() || fresh.matches("(?i)^(be|use|look|have|match|a|an|the)\\b.*")) {
            return; // "make the site be blue" is a look change, not a new name
        }
        w.consumed = true;
        String old = brand();
        if (old.isEmpty() || fresh.isEmpty()) {
            w.clarify.add("I couldn't tell which name the site has now. Open the [site](/site/home) and tell me, for example: "
                    + "*rename the site to \"Golden Crust\"*.");
            return;
        }
        if (old.equals(fresh)) {
            w.clarify.add("The site is already called **" + old + "**.");
            return;
        }
        int hits = renameBrand(old, fresh, w);
        if (hits > 0) {
            w.log.add(op("kind", "brand", "find", old, "replace", fresh));
            w.changes.add("renamed **" + old + "** to **" + fresh + "** (" + hits + " places)");
        }
    }

    private int renameBrand(String old, String fresh, Work w) throws IOException {
        int hits = 0;
        List<Path> targets = new ArrayList<>(pages());
        for (String f : List.of("SITE_META.json", "SITE_STYLE.json", "SITE_AGENT.json", "USER_STORY.md")) {
            Path p = outputDir.resolve(f);
            if (Files.exists(p)) {
                targets.add(p);
            }
        }
        for (Path p : targets) {
            String text = read(p);
            int n = count(text, old);
            if (n > 0) {
                write(p, text.replace(old, p.toString().endsWith(".html") ? escape(fresh) : fresh));
                hits += n;
                w.files.add(outputDir.relativize(p).toString().replace('\\', '/'));
            }
        }
        return hits;
    }

    // ---- hero headline / text / cta

    private static final Pattern HEADLINE = Pattern.compile(
            "(?i)\\b(?:change|set|update|make|replace|rewrite|edit)\\s+(?:the\\s+)?(?:hero\\s+|main\\s+|home\\s*(?:page)?\\s+)?"
                    + "(?:title|headline|heading)\\s+(?:to|as|say|says|into|:)\\s*(.{2,140})$");
    private static final Pattern HEADLINE_WORD = Pattern.compile(
            "(?i)\\b(?:change|set|update|rewrite|edit|replace)\\s+(?:the\\s+)?(?:hero\\s+|main\\s+|home\\s*(?:page)?\\s+)?"
                    + "(?:title|headline|heading)\\b");

    private void headline(String m, Work w) throws IOException {
        Matcher h = HEADLINE.matcher(m);
        if (h.find()) {
            w.consumed = true;
            String text = value(h.group(1));
            Path home = templates().resolve("home.html");
            if (!Files.exists(home)) {
                return;
            }
            Matcher old = Pattern.compile("(<h1[^>]*>)([^<]*)(</h1>)").matcher(read(home));
            if (!old.find()) {
                w.clarify.add("I couldn't find the main headline on the home page.");
                return;
            }
            String find = old.group();
            int n = replaceText("home", find, old.group(1) + escape(text) + old.group(3), w);
            if (n > 0) {
                w.log.add(op("kind", "text", "page", "home", "find", find, "replace", old.group(1) + escape(text) + old.group(3)));
                w.changes.add("updated the hero headline to \u201c" + text + "\u201d");
                w.page = "home";
            }
        } else if (HEADLINE_WORD.matcher(m).find() && !STYLE_CUE.matcher(m).find()) {
            w.consumed = true;
            w.clarify.add("What should the headline say? For example: *change the headline to \"Fresh bread, baked every morning\"*.");
        }
    }

    private static final Pattern HERO_TEXT = Pattern.compile(
            "(?i)\\b(?:change|set|update|make|replace|rewrite|edit)\\s+(?:the\\s+)?(?:hero\\s+|home\\s*(?:page)?\\s+)?"
                    + "(?:subtitle|sub-?heading|tagline|intro|lead|hero text|description)\\s+(?:to|as|say|says|into|:)\\s*(.{2,240})$");

    private void heroText(String m, Work w) throws IOException {
        Matcher h = HERO_TEXT.matcher(m);
        if (!h.find()) {
            return;
        }
        w.consumed = true;
        String text = value(h.group(1));
        Path home = templates().resolve("home.html");
        if (!Files.exists(home)) {
            return;
        }
        Matcher old = Pattern.compile("(?s)(<h1[^>]*>[^<]*</h1>\\s*<p[^>]*>)([^<]*)(</p>)").matcher(read(home));
        if (!old.find()) {
            w.clarify.add("I couldn't find the hero text on the home page.");
            return;
        }
        String find = old.group();
        String replace = old.group(1) + escape(text) + old.group(3);
        if (replaceText("home", find, replace, w) > 0) {
            w.log.add(op("kind", "text", "page", "home", "find", find, "replace", replace));
            w.changes.add("rewrote the hero text");
            w.page = "home";
        }
    }

    private static final Pattern CTA = Pattern.compile(
            "(?i)\\b(?:change|set|rename|update)\\s+(?:the\\s+)?(?:cta|call[- ]to[- ]action|top(?:[- ]right)?\\s+button|header\\s+button)"
                    + "(?:\\s+(?:text|label))?\\s+(?:to|as|say|says|into|:)\\s*(.{2,40})$");

    private void cta(String m, Work w) throws IOException {
        Matcher c = CTA.matcher(m);
        if (!c.find()) {
            return;
        }
        w.consumed = true;
        String text = value(c.group(1));
        Path home = templates().resolve("home.html");
        if (!Files.exists(home)) {
            return;
        }
        Matcher old = Pattern.compile("(<a class=\"cta\"[^>]*>)([^<]*)(</a>)").matcher(read(home));
        if (!old.find()) {
            w.clarify.add("I couldn't find the top button on this site.");
            return;
        }
        String find = old.group();
        String replace = old.group(1) + escape(text) + old.group(3);
        int n = replaceText("*", find, replace, w);
        if (n > 0) {
            w.log.add(op("kind", "text", "page", "*", "find", find, "replace", replace));
            w.changes.add("changed the top button to \u201c" + text + "\u201d");
        }
    }

    // ---- hero size (stacking levels)

    private static final Pattern HERO_WORD = Pattern.compile("(?i)\\b(hero|banner|top section|landing section)\\b");
    private static final Pattern SHORTER = Pattern.compile(
            "(?i)\\b(shorter|smaller|less tall|compact|reduce|shrink|slimmer|thinner|lower|tighter)\\b");
    private static final Pattern TALLER = Pattern.compile(
            "(?i)\\b(taller|bigger|larger|more space|increase|expand|enlarge|roomier)\\b");

    private void heroSize(String m, Work w) throws IOException {
        if (!HERO_WORD.matcher(m).find()) {
            return;
        }
        boolean shorter = SHORTER.matcher(m).find();
        boolean taller = TALLER.matcher(m).find();
        if (shorter == taller) {
            return;
        }
        Map<String, String> entry = null;
        for (Map<String, String> e : w.log) {
            if ("css".equals(e.get("kind")) && "hero-size".equals(e.get("key"))) {
                entry = e;
            }
        }
        int level = entry == null ? 0 : Integer.parseInt(entry.get("value"));
        int next = Math.max(-2, Math.min(2, level + (shorter ? -1 : 1)));
        w.consumed = true;
        if (next == level) {
            w.clarify.add("The hero is already as " + (shorter ? "short" : "tall") + " as I'll make it. "
                    + "Tell me if you want something else, like a different headline.");
            return;
        }
        w.log.removeIf(e -> "css".equals(e.get("kind")) && "hero-size".equals(e.get("key")));
        if (next != 0) {
            w.log.add(op("kind", "css", "key", "hero-size", "value", String.valueOf(next), "css", heroCss(next)));
        }
        writeCss(w.log, w);
        w.changes.add("updated the hero \u2014 it's " + (shorter ? "shorter" : "taller") + " now"
                + (shorter ? ", with less padding and a smaller headline" : ", with more room and a bigger headline"));
        w.page = "home";
    }

    private static String heroCss(int level) {
        return switch (level) {
            case -2 -> ".hero{padding:1.25rem 5vw 1rem !important;gap:1rem !important}"
                    + ".hero h1{font-size:clamp(1.4rem,3vw,2rem) !important;margin-bottom:.4rem !important}"
                    + ".hero p{margin-bottom:.75rem !important;font-size:.95rem !important}"
                    + ".visual,.visual img,.hero.center .visual{min-height:110px !important;max-height:150px !important}";
            case -1 -> ".hero{padding:2rem 5vw 1.5rem !important;gap:1.25rem !important}"
                    + ".hero h1{font-size:clamp(1.6rem,3.4vw,2.3rem) !important;margin-bottom:.6rem !important}"
                    + ".hero p{margin-bottom:1rem !important}"
                    + ".visual,.visual img,.hero.center .visual{min-height:170px !important;max-height:230px !important}";
            case 1 -> ".hero{padding:5.5rem 5vw 4.5rem !important}"
                    + ".hero h1{font-size:clamp(2.4rem,6vw,4rem) !important}"
                    + ".visual,.visual img,.hero.center .visual{min-height:440px !important}";
            case 2 -> ".hero{padding:8rem 5vw 6.5rem !important}"
                    + ".hero h1{font-size:clamp(2.8rem,7vw,4.8rem) !important}"
                    + ".visual,.visual img,.hero.center .visual{min-height:560px !important}";
            default -> "";
        };
    }

    /** Rewrites the single {@code <style id="bot-edits">} block of every page from the css entries of the log. */
    private void writeCss(List<Map<String, String>> edits, Work w) throws IOException {
        StringBuilder css = new StringBuilder();
        for (Map<String, String> e : edits) {
            if ("css".equals(e.get("kind")) && e.get("css") != null) {
                css.append(e.get("css"));
            }
        }
        String block = css.length() == 0 ? "" : "<style id=\"" + CSS_ID + "\">" + css + "</style>";
        Pattern old = Pattern.compile("(?s)<style id=\"" + CSS_ID + "\">.*?</style>");
        for (Path p : pages()) {
            String html = read(p);
            String without = old.matcher(html).replaceAll("");
            String updated = block.isEmpty() ? without
                    : without.contains("</head>") ? without.replace("</head>", block + "</head>") : block + without;
            if (!updated.equals(html)) {
                write(p, updated);
                if (w != null) {
                    w.files.add("src/main/resources/templates/" + p.getFileName());
                }
            }
        }
    }

    // ---- menu

    private static final String LABEL = "(?:\"([^\"]{1,40})\"|'([^']{1,40})'|([A-Za-z0-9&][A-Za-z0-9&' -]{0,38}?))";
    private static final Pattern RENAME = Pattern.compile(
            "(?i)\\b(?:rename|relabel|call|change|retitle)\\s+(?:the\\s+)?(?:menu\\s+|nav(?:igation)?\\s+)?"
                    + "(?:item\\s+|link\\s+|tab\\s+|label\\s+|button\\s+|page\\s+)?(?:called\\s+|named\\s+)?" + LABEL
                    + "\\s+(?:to|into|as|->)\\s+(?:\"([^\"]{1,40})\"|'([^']{1,40})'|([^.,;!\\n\"']{1,40}))");
    private static final Pattern MENU_WORD = Pattern.compile("(?i)\\b(menu|nav|navigation|link|tab|item|label|button|page)\\b");
    private static final Pattern RENAME_MENU_VAGUE = Pattern.compile(
            "(?i)\\b(rename|relabel|change|edit|update)\\b.*\\b(menu|nav|navigation)\\b");

    private void renameLabel(String m, Work w) throws IOException {
        Matcher r = RENAME.matcher(m);
        while (r.find()) {
            String from = firstOf(r.group(1), r.group(2), r.group(3));
            String to = value(firstOf(r.group(4), r.group(5), r.group(6)));
            boolean quoted = r.group(1) != null || r.group(2) != null;
            boolean known = menuLabels().stream().anyMatch(l -> l.equalsIgnoreCase(from.strip()));
            if (from.isBlank() || to.isBlank() || STYLE_CUE.matcher(from).find() || COLOUR.matcher(to).matches()
                    || !(quoted || known || MENU_WORD.matcher(m).find())) {
                continue;
            }
            int hits = renameText(from.strip(), to, w);
            if (hits > 0) {
                w.consumed = true;
                w.log.add(op("kind", "label", "find", from.strip(), "replace", to));
                w.changes.add("renamed \u201c" + from.strip() + "\u201d to \u201c" + to + "\u201d (" + hits + " places)");
                w.page = "home";
                return;
            }
            if (quoted || known) {
                w.consumed = true;
                w.clarify.add("I couldn't find \u201c" + from.strip() + "\u201d on the site. The menu has: "
                        + String.join(", ", menuLabels()) + ".");
                return;
            }
        }
        if (RENAME_MENU_VAGUE.matcher(m).find() && !STYLE_CUE.matcher(m).find()) {
            w.consumed = true;
            List<String> labels = menuLabels();
            w.clarify.add("Which menu item should I rename, and to what?"
                    + (labels.isEmpty() ? "" : " Your menu is: " + String.join(", ", labels) + ".")
                    + " For example: *rename \"" + (labels.isEmpty() ? "Plans" : labels.get(Math.min(2, labels.size() - 1)))
                    + "\" to \"Pricing\"*.");
        }
    }

    /** Changes every text node that is exactly {@code from} (and the page titles that end with it). */
    private int renameText(String from, String to, Work w) throws IOException {
        Pattern node = Pattern.compile("(?i)(>\\s*)" + Pattern.quote(from) + "(\\s*(?:\u2192|&rarr;)?\\s*<)");
        Pattern title = Pattern.compile("(?i)([\u00b7|\\-\u2014]\\s*)" + Pattern.quote(from) + "(\\s*</title>)");
        String safe = Matcher.quoteReplacement(escape(to));
        int hits = 0;
        for (Path p : pages()) {
            String html = read(p);
            int[] n = {0};
            String updated = node.matcher(html).replaceAll(mr -> {
                n[0]++;
                return Matcher.quoteReplacement(mr.group(1)) + safe + Matcher.quoteReplacement(mr.group(2));
            });
            updated = title.matcher(updated).replaceAll(mr -> {
                n[0]++;
                return Matcher.quoteReplacement(mr.group(1)) + safe + Matcher.quoteReplacement(mr.group(2));
            });
            if (n[0] > 0) {
                write(p, updated);
                hits += n[0];
                w.files.add("src/main/resources/templates/" + p.getFileName());
            }
        }
        Path story = outputDir.resolve("USER_STORY.md");
        if (hits > 0 && Files.exists(story)) {
            String s = read(story);
            String u = Pattern.compile("(?m)^(Menu:.*)$").matcher(s).replaceAll(mr ->
                    Matcher.quoteReplacement(mr.group(1).replaceAll("(?i)(?<=[:,]\\s?)" + Pattern.quote(from) + "(?=\\s*(?:,|$))",
                            Matcher.quoteReplacement(to))));
            if (!u.equals(s)) {
                write(story, u);
            }
        }
        return hits;
    }

    private static final Pattern REMOVE_FROM_MENU = Pattern.compile(
            "(?i)\\b(?:remove|delete|hide|drop|take off|get rid of)\\s+(?:the\\s+)?" + LABEL
                    + "\\s*(?:link|page|tab|item|button)?\\s+(?:from|in|off)\\s+(?:the\\s+)?(?:menu|nav|navigation|navbar|header|top bar)\\b");

    private void removeFromMenu(String m, Work w) throws IOException {
        Matcher r = REMOVE_FROM_MENU.matcher(m);
        if (!r.find()) {
            return;
        }
        w.consumed = true;
        String label = firstOf(r.group(1), r.group(2), r.group(3)).strip();
        int hits = dropMenuLink(label, w);
        if (hits > 0) {
            w.log.add(op("kind", "unmenu", "find", label));
            w.changes.add("removed \u201c" + label + "\u201d from the menu");
            w.page = "home";
        } else {
            w.clarify.add("I couldn't find \u201c" + label + "\u201d in the menu. It has: " + String.join(", ", menuLabels()) + ".");
        }
    }

    private int dropMenuLink(String label, Work w) throws IOException {
        String slug = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        Pattern nav = Pattern.compile("(?is)(<nav class=\"nav\">)(.*?)(</nav>)");
        Pattern link = Pattern.compile("(?is)\\s*<a\\b([^>]*)>(.*?)</a>");
        int hits = 0;
        for (Path p : pages()) {
            String html = read(p);
            Matcher n = nav.matcher(html);
            if (!n.find()) {
                continue;
            }
            int[] removed = {0};
            String inner = link.matcher(n.group(2)).replaceAll(mr -> {
                String text = mr.group(2).replaceAll("<[^>]+>", "").trim();
                boolean hit = text.equalsIgnoreCase(label) || (!slug.isEmpty() && mr.group(1).toLowerCase(Locale.ROOT).contains("/site/" + slug + "\""));
                if (hit) {
                    removed[0]++;
                    return "";
                }
                return Matcher.quoteReplacement(mr.group());
            });
            if (removed[0] > 0) {
                write(p, html.substring(0, n.start(2)) + inner + html.substring(n.end(2)));
                hits += removed[0];
                w.files.add("src/main/resources/templates/" + p.getFileName());
            }
        }
        return hits;
    }

    // ---- contact form section

    private static final Pattern ADD_FORM = Pattern.compile(
            "(?i)\\b(?:add|include|put|insert|show|create|give)\\b.{0,40}\\b(?:contact|enquiry|inquiry|feedback)\\s+(?:form|section)\\b"
                    + "|\\bcontact form\\b.{0,30}\\b(?:add|on|to|in)\\b.{0,20}\\b(?:home|about|pricing|services|blog|careers)\\b");

    private void contactForm(String m, Work w) throws IOException {
        if (!ADD_FORM.matcher(m).find()) {
            return;
        }
        w.consumed = true;
        String page = "home";
        Matcher pw = PAGE_WORD.matcher(m);
        if (pw.find()) {
            page = pw.group(1).toLowerCase(Locale.ROOT);
        } else {
            Matcher bare = Pattern.compile("(?i)\\b(?:on|to|in)\\s+(?:the\\s+)?(home|about|services|pricing|blog|careers|contact)\\b").matcher(m);
            if (bare.find()) {
                page = bare.group(1).toLowerCase(Locale.ROOT);
            }
        }
        Path file = templates().resolve(page + ".html");
        if (!Files.exists(file)) {
            w.clarify.add("There's no \u201c" + page + "\u201d page on this site. Pages: "
                    + String.join(", ", pages().stream().map(SitePatcher::pageName).toList()) + ".");
            return;
        }
        String html = read(file);
        if (html.contains("data-bot-section=\"contact-form\"") || html.contains("action=\"/site/contact\"")) {
            w.clarify.add("The **" + page + "** page already has a contact form \u2014 [take a look](/site/" + page + ").");
            return;
        }
        String brand = brand();
        String section = """
                <section class="section alt" id="contact-form" data-bot-section="contact-form">
                  <h2>Get in touch</h2>
                  <p class="lead">Send us a note%s and we'll get back to you.</p>
                  <form class="card" method="post" action="/site/contact" style="padding:1.25rem;max-width:34rem">
                    <label for="cf-email">Email</label>
                    <input id="cf-email" name="field1" type="email" placeholder="you@email.com"/>
                    <label for="cf-name">Name</label>
                    <input id="cf-name" name="field2" placeholder="Your name"/>
                    <label for="cf-message">Message</label>
                    <textarea id="cf-message" name="field3" rows="4" placeholder="How can we help?"></textarea>
                    <button type="submit">Send message</button>
                  </form>
                </section>
                """.formatted(brand.isEmpty() ? "" : " and the " + escape(brand) + " team");
        if (insertSection(page, section, w)) {
            w.log.add(op("kind", "section", "page", page, "html", section));
            w.changes.add("added a contact form to the " + page + " page");
            w.page = page;
        }
    }

    private boolean insertSection(String page, String section, Work w) throws IOException {
        Path file = templates().resolve(page + ".html");
        if (!Files.exists(file)) {
            return false;
        }
        String html = read(file);
        String key = Pattern.compile("data-bot-section=\"([^\"]+)\"").matcher(section).results()
                .map(r -> r.group(1)).findFirst().orElse("");
        if (!key.isEmpty() && html.contains("data-bot-section=\"" + key + "\"")) {
            return false;
        }
        int at = indexOfIgnoreCase(html, "<footer");
        if (at < 0) {
            at = indexOfIgnoreCase(html, "</body>");
        }
        if (at < 0) {
            return false;
        }
        write(file, html.substring(0, at) + section + "\n" + html.substring(at));
        w.files.add("src/main/resources/templates/" + file.getFileName());
        return true;
    }

    // ---- literal text replacement

    /** Literal replace in one page ("*" = every page); returns how many places changed. */
    private int replaceText(String page, String find, String replace, Work w) throws IOException {
        int hits = 0;
        for (Path p : pages()) {
            if (!"*".equals(page) && !pageName(p).equals(page)) {
                continue;
            }
            String html = read(p);
            int n = count(html, find);
            if (n > 0) {
                write(p, html.replace(find, replace));
                hits += n;
                w.files.add("src/main/resources/templates/" + p.getFileName());
            }
        }
        return hits;
    }

    // ------------------------------------------------------------------ language model fallback

    private static final int MAX_OPS = 12;
    private static final int MAX_PAGE_CHARS = 14000;

    private void modelEdit(String m, Work w) throws IOException {
        String page = "home";
        Matcher pw = PAGE_WORD.matcher(m);
        if (pw.find() && Files.exists(templates().resolve(pw.group(1).toLowerCase(Locale.ROOT) + ".html"))) {
            page = pw.group(1).toLowerCase(Locale.ROOT);
        } else {
            for (Path p : pages()) {
                if (Pattern.compile("(?i)\\b" + Pattern.quote(pageName(p)) + "\\b").matcher(m).find()) {
                    page = pageName(p);
                    break;
                }
            }
        }
        Path file = templates().resolve(page + ".html");
        if (!Files.exists(file)) {
            return;
        }
        String html = read(file);
        String shown = html.replaceAll("(?s)<style id=\"" + CSS_ID + "\">.*?</style>", "");
        shown = shown.length() > MAX_PAGE_CHARS ? shown.substring(0, MAX_PAGE_CHARS) : shown;
        String system = """
                You edit a generated static website for a non-technical owner. Reply with ONLY one JSON object, no prose:
                {"reply":"one friendly sentence in the past tense saying what you changed, starting with a verb",
                 "rebuild":false,
                 "ops":[
                  {"op":"replace","page":"home","find":"text copied EXACTLY from the HTML","replace":"new text"},
                  {"op":"css","css":".selector{property:value !important}"},
                  {"op":"insert_before_footer","page":"home","html":"<section class=\\"section\\" data-bot-section=\\"unique-key\\">...</section>"}
                 ]}
                Rules: make the smallest change that does what was asked; copy "find" strings exactly from the page and keep
                them short but unique; reuse the page's own CSS classes and variables (--accent, --ink, --muted, --soft);
                never add scripts, iframes or event attributes; use "css" for look-and-feel changes. If the request needs a
                new page, a backend feature or a chatbot, answer {"rebuild":true,"ops":[]}.
                """;
        String user = "Page: " + page + "\nPages on the site: "
                + String.join(", ", pages().stream().map(SitePatcher::pageName).toList())
                + "\nRequest: " + m + "\n\nHTML of the page (style block omitted):\n" + shown;
        String answer;
        try {
            answer = llm.complete(system, user, Duration.ofSeconds(60), 1800);
        } catch (RuntimeException e) {
            log.debug("Model edit failed: {}", e.toString());
            return;
        }
        JsonNode plan = parseJson(answer);
        if (plan == null || plan.path("rebuild").asBoolean(false) || !plan.path("ops").isArray()) {
            return;
        }
        int applied = 0;
        for (JsonNode o : plan.path("ops")) {
            if (applied >= MAX_OPS) {
                break;
            }
            String kind = o.path("op").asText("");
            String target = o.path("page").asText(page).toLowerCase(Locale.ROOT);
            if (!Files.exists(templates().resolve(target + ".html"))) {
                target = page;
            }
            switch (kind) {
                case "replace" -> {
                    String find = o.path("find").asText("");
                    String replace = o.path("replace").asText("");
                    if (find.length() >= 2 && !find.equals(replace) && safe(replace) && replaceText(target, find, replace, w) > 0) {
                        w.log.add(op("kind", "text", "page", target, "find", find, "replace", replace));
                        applied++;
                    }
                }
                case "css" -> {
                    String css = o.path("css").asText("");
                    if (css.length() >= 4 && css.length() <= 3000 && safe(css) && !css.contains("</")) {
                        w.log.add(op("kind", "css", "key", "model-" + w.log.size(), "value", "0", "css", css));
                        writeCss(w.log, w);
                        applied++;
                    }
                }
                case "insert_before_footer" -> {
                    String section = o.path("html").asText("");
                    if (section.length() >= 10 && section.length() <= 6000 && safe(section)
                            && insertSection(target, section, w)) {
                        w.log.add(op("kind", "section", "page", target, "html", section));
                        applied++;
                    }
                }
                default -> {
                    // unknown operations are ignored
                }
            }
            if (applied > 0 && w.page == null) {
                w.page = target;
            }
        }
        if (applied > 0) {
            String reply = plan.path("reply").asText("").strip();
            w.changes.add(reply.isEmpty() ? "made that change to the " + page + " page"
                    : Character.toLowerCase(reply.charAt(0)) + reply.substring(1).replaceAll("[.!]+$", ""));
        }
    }

    private static boolean safe(String s) {
        String l = s.toLowerCase(Locale.ROOT);
        return !(l.contains("<script") || l.contains("<iframe") || l.contains("javascript:") || l.contains("<object")
                || Pattern.compile("\\son[a-z]+\\s*=").matcher(l).find() || l.contains("@import") || l.contains("expression("));
    }

    private static JsonNode parseJson(String answer) {
        if (answer == null) {
            return null;
        }
        int a = answer.indexOf('{');
        int b = answer.lastIndexOf('}');
        if (a < 0 || b <= a) {
            return null;
        }
        try {
            return JSON.readTree(answer.substring(a, b + 1));
        } catch (IOException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ keeping edits through a rebuild

    /** Records the look of the site just before a structural rebuild. */
    public synchronized Look snapshotLook() {
        Map<String, String> tokens = new LinkedHashMap<>();
        String fonts = "";
        String styleJson = "";
        Map<String, PageLook> pages = new LinkedHashMap<>();
        try {
            Path home = templates().resolve("home.html");
            if (Files.exists(home)) {
                String html = read(home);
                Matcher root = Pattern.compile(":root\\s*\\{([^}]*)\\}").matcher(html);
                if (root.find()) {
                    Matcher t = Pattern.compile("(--[a-z0-9-]+)\\s*:\\s*([^;]+);").matcher(root.group(1));
                    while (t.find()) {
                        tokens.put(t.group(1), t.group(2).trim());
                    }
                }
                Matcher f = Pattern.compile("<link href=\"(https://fonts\\.googleapis\\.com/[^\"]+)\"").matcher(html);
                if (f.find()) {
                    fonts = f.group(1);
                }
            }
            Path style = outputDir.resolve("SITE_STYLE.json");
            if (Files.exists(style)) {
                styleJson = read(style);
            }
            for (Path p : pages()) {
                String html = read(p);
                pages.put(pageName(p), new PageLook(first(html, HERO_CLASS), first(html, HERO_IMG), all(html, CARD_IMG), first(html, MARK)));
            }
        } catch (IOException e) {
            log.debug("Could not snapshot the look: {}", e.toString());
        }
        return new Look(tokens, fonts, styleJson, pages);
    }

    private static final Pattern HERO_CLASS = Pattern.compile("<section class=\"(hero[^\"]*)\"");
    private static final Pattern HERO_IMG = Pattern.compile("<div class=\"visual\">\\s*<img\\s+src=\"([^\"]+)\"");
    private static final Pattern CARD_IMG = Pattern.compile("<img[^>]*class=\"card-img\"[^>]*?src=\"([^\"]+)\"");
    private static final Pattern MARK = Pattern.compile("class=\"mark\" style=\"background-image:url\\('([^']*)'\\)\"");

    private static String first(String html, Pattern p) {
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : null;
    }

    private static List<String> all(String html, Pattern p) {
        List<String> out = new ArrayList<>();
        Matcher m = p.matcher(html);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    /** Puts the snapshot's theme, fonts, hero layout and pictures back on the rebuilt pages. */
    public synchronized void restoreLook(Look look) {
        if (look == null || look.pages().isEmpty()) {
            return;
        }
        try {
            for (Path p : pages()) {
                String html = read(p);
                String updated = html;
                Matcher root = Pattern.compile(":root\\s*\\{([^}]*)\\}").matcher(updated);
                if (root.find() && !look.tokens().isEmpty()) {
                    String block = root.group(1);
                    for (Map.Entry<String, String> t : look.tokens().entrySet()) {
                        block = Pattern.compile("(" + Pattern.quote(t.getKey()) + "\\s*:\\s*)[^;]+;")
                                .matcher(block).replaceFirst("$1" + Matcher.quoteReplacement(t.getValue()) + ";");
                    }
                    updated = updated.substring(0, root.start(1)) + block + updated.substring(root.end(1));
                }
                if (!look.fontsLink().isEmpty()) {
                    updated = updated.replaceFirst("(<link href=\")https://fonts\\.googleapis\\.com/[^\"]+(\")",
                            "$1" + Matcher.quoteReplacement(look.fontsLink()) + "$2");
                }
                PageLook before = look.pages().get(pageName(p));
                if (before != null) {
                    updated = restorePictures(updated, before);
                }
                if (!updated.equals(html)) {
                    write(p, updated);
                }
            }
            if (!look.styleJson().isBlank()) {
                write(outputDir.resolve("SITE_STYLE.json"), look.styleJson());
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Could not restore the look after the rebuild: {}", e.toString());
        }
    }

    private static String restorePictures(String html, PageLook before) {
        String out = html;
        if (before.heroClass() != null) {
            out = out.replaceFirst("(<section class=\")hero[^\"]*(\")",
                    "$1" + Matcher.quoteReplacement(before.heroClass()) + "$2");
        }
        if (before.heroImage() != null) {
            out = out.replaceFirst("(<div class=\"visual\">\\s*<img\\s+src=\")[^\"]+(\")",
                    "$1" + Matcher.quoteReplacement(before.heroImage()) + "$2");
        }
        if (before.logo() != null) {
            out = out.replaceFirst("(class=\"mark\" style=\"background-image:url\\(')[^']*('\\)\")",
                    "$1" + Matcher.quoteReplacement(before.logo()) + "$2");
        }
        List<String> cards = before.cardImages();
        if (!cards.isEmpty() && all(out, CARD_IMG).size() == cards.size()) {
            Matcher m = Pattern.compile("(<img[^>]*class=\"card-img\"[^>]*?src=\")([^\"]+)(\")").matcher(out);
            StringBuilder sb = new StringBuilder();
            int i = 0;
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + cards.get(i++) + m.group(3)));
            }
            m.appendTail(sb);
            out = sb.toString();
        }
        return out;
    }

    /** Re-applies the recorded edits to a rebuilt site; edits whose text is no longer there are skipped. */
    public synchronized int replay() {
        List<Map<String, String>> edits = loadLog();
        if (edits.isEmpty() || !hasPages()) {
            return 0;
        }
        Work w = new Work(new ArrayList<>());
        int applied = 0;
        try {
            for (Map<String, String> e : edits) {
                switch (e.getOrDefault("kind", "")) {
                    case "text" -> applied += replaceText(e.getOrDefault("page", "*"), e.get("find"), e.get("replace"), w) > 0 ? 1 : 0;
                    case "label" -> applied += renameText(e.get("find"), e.get("replace"), w) > 0 ? 1 : 0;
                    case "brand" -> applied += renameBrand(e.get("find"), e.get("replace"), w) > 0 ? 1 : 0;
                    case "unmenu" -> applied += dropMenuLink(e.get("find"), w) > 0 ? 1 : 0;
                    case "section" -> applied += insertSection(e.get("page"), e.get("html"), w) ? 1 : 0;
                    default -> {
                        // css entries are written together below
                    }
                }
            }
            writeCss(edits, w);
            applied += (int) edits.stream().filter(e -> "css".equals(e.get("kind"))).count();
        } catch (IOException | RuntimeException ex) {
            log.warn("Could not replay the earlier edits: {}", ex.toString());
        }
        return applied;
    }

    /** Notes the request in USER_STORY.md so the file keeps telling how the site got to its present state. */
    private void noteInStory(String message) throws IOException {
        Path story = outputDir.resolve("USER_STORY.md");
        if (!Files.exists(story)) {
            return;
        }
        String s = read(story);
        String line = "- " + message.strip().replaceAll("\\s+", " ") + "\n";
        if (!s.contains("Edits applied from chat:")) {
            s = s.stripTrailing() + "\n\nEdits applied from chat:\n";
        } else if (!s.endsWith("\n")) {
            s += "\n";
        }
        write(story, s + line);
    }

    // ------------------------------------------------------------------ small helpers

    private static String firstOf(String... values) {
        for (String v : values) {
            if (v != null) {
                return v;
            }
        }
        return "";
    }

    /** Cleans a value taken from a sentence: no quotes, no trailing punctuation, single spaces. */
    private static String value(String raw) {
        String v = raw == null ? "" : raw.strip();
        v = v.replaceAll("^[\"'`]+|[\"'`]+$", "").replaceAll("[.!]+$", "").replaceAll("^[\"'`]+|[\"'`]+$", "")
                .replaceAll("\\s+", " ").strip();
        return v.length() > 240 ? v.substring(0, 240) : v;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static int count(String text, String find) {
        if (find == null || find.isEmpty()) {
            return 0;
        }
        int n = 0;
        int i = 0;
        while ((i = text.indexOf(find, i)) >= 0) {
            n++;
            i += find.length();
        }
        return n;
    }

    private static int indexOfIgnoreCase(String text, String find) {
        return text.toLowerCase(Locale.ROOT).indexOf(find.toLowerCase(Locale.ROOT));
    }
}
