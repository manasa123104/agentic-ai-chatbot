package com.agentic.chatbot.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a user story says about THIS business, so the generated website is not the same template for every story:
 * the business name (written or invented from the story), the things it offers, the place it is in and, when several
 * user stories are entered, one site section per story.
 *
 * <p>Everything returned is plain text with markup characters removed, so it can go straight into HTML.
 */
public final class StoryDetails {

    /** One user story turned into a card: a short title and a one-line blurb. */
    public record StoryCard(String title, String blurb) {
    }

    private final List<StoryCard> storyCards;
    private final List<String> features;
    private final String place;

    private StoryDetails(List<StoryCard> storyCards, List<String> features, String place) {
        this.storyCards = storyCards;
        this.features = features;
        this.place = place;
    }

    /** One card per user story when the prompt holds two or more stories, otherwise empty. */
    public List<StoryCard> storyCards() {
        return storyCards;
    }

    /** Offers / products / services named in the prompt ("with brunch, catering and wine"), at most six. */
    public List<String> features() {
        return features;
    }

    /** City or area named in the prompt ("in Lisbon"), or an empty string. */
    public String place() {
        return place;
    }

    public boolean isEmpty() {
        return storyCards.isEmpty() && features.isEmpty() && place.isEmpty();
    }

    /** The headings the site should show as its service cards: story titles first, then named features. */
    public List<String> cardTitles() {
        if (!storyCards.isEmpty()) {
            return storyCards.stream().map(StoryCard::title).toList();
        }
        return features;
    }

    // ------------------------------------------------------------------ parsing

    private static final Pattern EDIT_TAIL = Pattern.compile("(?s)\\n\\s*\\n?\\s*Edit request:.*$");
    private static final Pattern STORY_START = Pattern.compile(
            "(?im)^\\s*(?:(?:[-*\u2022]|\\d+[.)]|(?:user\\s+)?story\\s*#?\\d*\\s*[:.\\-])\\s*)?as\\s+an?\\s+");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*(?:[-*\u2022]|\\d+[.)])\\s+(.{12,})$");
    private static final Pattern WANT = Pattern.compile(
            "(?is)\\bI\\s+(?:want|need|would like|can|should be able)(?:\\s+to)?\\s+(.+?)(?:,?\\s+so\\s+(?:that\\s+)?(.+?))?(?:[.\\n]|$)");
    private static final Pattern ROLE = Pattern.compile("(?is)^\\s*(?:[-*\u2022]|\\d+[.)])?\\s*as\\s+an?\\s+(.+?),");
    private static final Pattern LIST_LEAD = Pattern.compile(
            "(?i)\\b(?:with|featuring|offering|offers|including|includes|serving|serves|selling|sells|specializ(?:es|ing) in|"
                    + "known for|menu of|classes in|courses in|focused on)\\s*:?\\s+([^.\\n;()]{3,200})");
    private static final Pattern PLACE = Pattern.compile(
            "\\b(?:[Ii]n|[Nn]ear|[Bb]ased in|[Ll]ocated in|[Dd]owntown)\\s+([A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,})?)\\b");

    private static final Pattern NOT_A_FEATURE = Pattern.compile(
            "(?i).*\\b(?:chat\\s?bot|agent|assistant|font|colou?rs?|colou?red|images?|photos?|spring|thymeleaf|java|python|"
                    + "javascript|typescript|backend|language|unit tests?|responsive|navigation|footer|header|pages?|"
                    + "websites?|sites?|home|about|services|pricing|blog|careers|contact|theme|dark mode|login|sign ?in|"
                    + "form|button|layout|design|modern|clean|simple|beautiful|nice)\\b.*");

    private static final Set<String> NOT_A_PLACE = Set.of("Spring", "Boot", "Java", "Python", "Thymeleaf", "Go", "Rust",
            "Kotlin", "Node", "React", "Docker", "The", "This", "That", "Html", "Css", "Mind", "Order", "Place");

    /** "# Harbor Bakery" at the top of an uploaded requirements file names the business. */
    private static final Pattern TITLE_HEADING = Pattern.compile("(?m)^#\\s+([A-Z][A-Za-z0-9]+(?:\\s+[A-Z][A-Za-z0-9]+){0,2})\\s*$");
    private static final Pattern GENERIC_TITLE = Pattern.compile(
            "(?i)\\b(user stor|requirement|readme|notes?|spec|specification|overview|backlog|epic|features?|todo|draft|document|project)");

    private static final String[] NAME_FIRST = {"Juniper", "Saffron", "Ember", "Willow", "Copper", "Maple", "Indigo",
            "Harbor", "Lumen", "Cedar", "Atlas", "Meadow", "Coral", "Sable", "Aurora", "Fable", "Quill", "Zephyr",
            "Marigold", "Slate", "Olive", "Sorrel", "Nimbus", "Briar"};

    private static String[] nameSecond(SiteThemePack.Kind kind) {
        return switch (kind) {
            case RESTAURANT -> new String[]{"Table", "Kitchen", "Bistro", "House"};
            case TRAVEL -> new String[]{"Trails", "Voyages", "Journeys", "Getaways"};
            case MUSIC -> new String[]{"Notes", "Academy", "Studio", "Sound"};
            case WEDDING -> new String[]{"Vows", "Weddings", "Events", "Bloom"};
            case PETS -> new String[]{"Paws", "Pet Lodge", "Tails", "Pet Care"};
            case BOOKS -> new String[]{"Books", "Reads", "Bookshop", "Pages"};
            case GARDEN -> new String[]{"Garden", "Greens", "Nursery", "Gardens"};
            case GAMING -> new String[]{"Esports", "Gaming", "Arena", "Squad"};
            case SPACE -> new String[]{"Planetarium", "Observatory", "Skies", "Cosmos"};
            case CLEANING -> new String[]{"Cleaning", "Shine", "Clean Co", "Maids"};
            case HOSPITAL -> new String[]{"Clinic", "Care", "Health", "Medical"};
            case FITNESS -> new String[]{"Fitness", "Gym", "Strength", "Studio"};
            case BANK -> new String[]{"Bank", "Finance", "Trust", "Credit"};
            case TECH -> new String[]{"Labs", "Works", "Systems", "Digital"};
        };
    }

    /** What a prompt says about the business. A follow-up "Edit request:" tail is ignored so edits keep the site stable. */
    public static StoryDetails parse(String story) {
        String text = baseStory(story);
        if (text.isBlank()) {
            return new StoryDetails(List.of(), List.of(), "");
        }
        List<StoryCard> cards = storyCards(text);
        List<String> features = cards.isEmpty() ? features(text) : List.of();
        return new StoryDetails(cards, features, place(text));
    }

    /** The story without any appended edit request. */
    static String baseStory(String story) {
        if (story == null) {
            return "";
        }
        return EDIT_TAIL.matcher(story).replaceAll("").strip();
    }

    /**
     * The business name for a prompt: the one written in it ("called Harmony Notes") or, when there is none, a name
     * invented from the story itself - the same story always gives the same name and different stories give different
     * names, so two unnamed restaurants are not both "Restaurant".
     */
    public static String brandOf(String story) {
        String named = SiteVariant.brandFrom(baseStory(story), null);
        if (named != null && !named.isBlank()) {
            return named;
        }
        String base = baseStory(story);
        Matcher heading = TITLE_HEADING.matcher(base);
        if (heading.find() && !GENERIC_TITLE.matcher(heading.group(1)).find()) {
            return heading.group(1).trim();
        }
        SiteThemePack.Kind kind = SiteThemePack.fromStory(base).kind();
        String key = base.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        int seed = SiteVariant.seedFor(key.isEmpty() ? "bot" : key);
        String first = NAME_FIRST[Math.floorMod(SiteVariant.mix(seed, 31), NAME_FIRST.length)];
        String[] second = nameSecond(kind);
        return first + " " + second[Math.floorMod(SiteVariant.mix(seed, 32), second.length)];
    }

    private static List<StoryCard> storyCards(String text) {
        List<String> stories = splitStories(text);
        if (stories.size() < 2) {
            return List.of();
        }
        List<StoryCard> cards = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String s : stories) {
            StoryCard card = cardFor(s);
            if (seen.add(card.title().toLowerCase(Locale.ROOT))) {
                cards.add(card);
            }
            if (cards.size() == 6) {
                break;
            }
        }
        return cards.size() < 2 ? List.of() : List.copyOf(cards);
    }

    /** Splits "As a ... I want ..." stories, or numbered / bulleted lists, into separate stories. */
    static List<String> splitStories(String text) {
        // stories written one after another on a single line
        text = text.replaceAll("(?<=[.!?])\\s+(?=[Aa]s an? )", "\n");
        List<Integer> starts = new ArrayList<>();
        Matcher m = STORY_START.matcher(text);
        while (m.find()) {
            starts.add(m.start());
        }
        List<String> out = new ArrayList<>();
        if (starts.size() >= 2) {
            for (int i = 0; i < starts.size(); i++) {
                int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
                String part = text.substring(starts.get(i), end).strip();
                if (!part.isEmpty()) {
                    out.add(part);
                }
            }
            return out;
        }
        Matcher b = BULLET.matcher(text);
        while (b.find()) {
            out.add(b.group(1).strip());
        }
        return out.size() >= 2 ? out : List.of();
    }

    private static StoryCard cardFor(String story) {
        String flat = story.replaceAll("\\s+", " ").trim();
        String title;
        String blurb;
        Matcher w = WANT.matcher(flat);
        if (w.find()) {
            title = shorten(capitalize(clean(w.group(1))), 42);
            String so = w.group(2);
            if (so != null && !so.isBlank()) {
                blurb = capitalize(clean(so)) + ".";
            } else {
                Matcher r = ROLE.matcher(flat);
                blurb = r.find() ? "Made for " + clean(r.group(1)) + "." : capitalize(shorten(clean(flat), 110)) + ".";
            }
        } else {
            title = shorten(capitalize(clean(flat.replaceFirst("(?i)^(?:[-*\u2022]|\\d+[.)])\\s*", ""))), 42);
            blurb = capitalize(shorten(clean(flat), 110)) + ".";
        }
        if (title.isBlank()) {
            title = "Story";
        }
        return new StoryCard(title, blurb.replaceAll("\\.+$", "."));
    }

    private static List<String> features(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher m = LIST_LEAD.matcher(text);
        while (m.find() && found.size() < 6) {
            for (String part : m.group(1).split(",|\\band\\b|&|/|\\bplus\\b|\\bas well as\\b")) {
                String item = clean(part).replaceFirst("(?i)^(?:a|an|the|some|our|great|fresh|local)\\s+", "");
                if (item.length() < 3 || item.length() > 40 || item.split("\\s+").length > 4) {
                    continue;
                }
                if (NOT_A_FEATURE.matcher(item).matches() || item.matches("(?i).*\\b(called|named)\\b.*")) {
                    continue;
                }
                found.add(titleCase(item));
                if (found.size() == 6) {
                    break;
                }
            }
        }
        return List.copyOf(found);
    }

    private static String place(String text) {
        Matcher m = PLACE.matcher(text);
        while (m.find()) {
            String p = m.group(1).trim();
            String firstWord = p.split("\\s+")[0];
            if (!NOT_A_PLACE.contains(firstWord)) {
                return p;
            }
        }
        return "";
    }

    // ------------------------------------------------------------------ text helpers

    /** Removes markup characters and trailing punctuation so the text is safe to place in HTML. */
    static String clean(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", " and ").replaceAll("[<>\"`{}\\\\]", "").replaceAll("\\s+", " ")
                .replaceAll("^[\\s,;:.\\-]+|[\\s,;:.\\-]+$", "");
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split("\\s+")) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
        }
        return sb.toString();
    }

    private static String shorten(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        int cut = s.lastIndexOf(' ', max);
        return s.substring(0, cut > max / 2 ? cut : max).replaceAll("[\\s,;:.\\-]+$", "");
    }
}
