package com.agentic.chatbot.generator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes every generated website look and read differently.
 *
 * <p>A stable seed is derived from the business name (so the same name always gives the same site, and
 * editing a site does not reshuffle it). The seed then picks a palette shift, a heading/body font pair, a hero
 * layout, corner style and the wording of every H1/H2, kicker and button, so two sites of the same kind
 * (two restaurants, two travel companies) are not the same page with the name swapped. Anything the user
 * asked for explicitly (a colour, a hex code, a font) is kept as asked.
 */
final class SiteVariant {

    /** Everything one site needs on top of its {@link SiteThemePack.Theme}. */
    record Design(
            int seed,
            SiteThemePack.Theme theme,
            String headFont,
            String fontsHref,
            String heroLayout,
            int radius,
            int headWeight,
            String kicker,
            String heroHeadline,
            String heroLead,
            String aboutHeading,
            String exploreHeading,
            String servicesHeading,
            String servicesLead,
            String pricingHeading,
            String pricingLead,
            String blogHeading,
            String blogLead,
            String careersHeading,
            String careersLead,
            String contactHeading,
            String contactLead,
            String ctaLabel,
            String[] planNames,
            String[] blogTitles,
            String[] roleTitles,
            String signature
    ) {
    }

    private record FontPair(String head, String body, String href) {
    }

    private static final FontPair[] FONT_PAIRS = {
            new FontPair("\"Playfair Display\", Georgia, serif", "Inter, Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@500;700;800&family=Inter:wght@400;500;600;700&display=swap"),
            new FontPair("Poppins, Arial, sans-serif", "Poppins, Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700;800&display=swap"),
            new FontPair("Montserrat, Arial, sans-serif", "\"Open Sans\", Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Montserrat:wght@600;700;800&family=Open+Sans:wght@400;600;700&display=swap"),
            new FontPair("\"DM Serif Display\", Georgia, serif", "\"DM Sans\", Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=DM+Serif+Display&family=DM+Sans:wght@400;500;700&display=swap"),
            new FontPair("\"Space Grotesk\", Arial, sans-serif", "Inter, Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Space+Grotesk:wght@500;700&family=Inter:wght@400;500;600;700&display=swap"),
            new FontPair("Lora, Georgia, serif", "\"Source Sans 3\", Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Lora:wght@500;700&family=Source+Sans+3:wght@400;600;700&display=swap"),
            new FontPair("Raleway, Arial, sans-serif", "Lato, Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Raleway:wght@600;700;800&family=Lato:wght@400;700&display=swap"),
            new FontPair("Manrope, Arial, sans-serif", "Manrope, Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Manrope:wght@400;500;600;700;800&display=swap"),
            new FontPair("Outfit, Arial, sans-serif", "Inter, Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Outfit:wght@500;700;800&family=Inter:wght@400;500;600;700&display=swap"),
            new FontPair("Merriweather, Georgia, serif", "\"Open Sans\", Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Merriweather:wght@400;700;900&family=Open+Sans:wght@400;600;700&display=swap"),
            new FontPair("Sora, Arial, sans-serif", "Inter, Arial, sans-serif",
                    "https://fonts.googleapis.com/css2?family=Sora:wght@500;700;800&family=Inter:wght@400;500;600;700&display=swap"),
    };

    private static final String[] LAYOUTS = {"split", "reverse", "center"};
    private static final int[] RADII = {6, 14, 22, 30};
    private static final int[] WEIGHTS = {600, 700, 800};

    private static final String[] KICKERS = {
            "Welcome", "Now open", "Hello there", "Meet us", "Est. 2026", "Your next favourite", "Come on in", "New here?"
    };
    private static final String[] HERO_TEMPLATES = {
            "{brand}: {tagline}",
            "Welcome to {brand}",
            "{brand} \u2014 {promise}",
            "This is {brand}. {promise}.",
            "Meet {brand}, your {place}",
            "{promise}, with {brand}",
            "{tagline}",
            "{brand}. {promise}.",
    };
    private static final String[] LEAD_TEMPLATES = {
            "{lead}",
            "{brand} in a sentence? {lead}",
            "Step inside {brand}. {lead}",
            "Why people choose {brand}: {leadLower}",
            "Here\u2019s what {brand} is about. {lead}",
    };
    private static final String[] ABOUT = {
            "About {brand}", "The story behind {brand}", "Why {brand}", "Meet {brand}", "Inside {brand}"
    };
    private static final String[] EXPLORE = {
            "Explore", "Start here", "Pick your path", "Where to next?", "Take a look around"
    };
    private static final String[] SERVICES = {
            "{label}", "{label} at {brand}", "What {brand} offers: {label}", "{brand} {label}"
    };
    private static final String[] SERVICES_LEAD = {
            "Everything {brand} offers \u2014 click any box to open that page.",
            "Browse what {brand} does best. Every box opens its own page.",
            "Pick a card to see how {brand} can help.",
            "A closer look at {brand}, one card at a time.",
    };
    private static final String[] PRICING = {
            "Pricing", "Simple pricing", "Choose your {brand} plan", "What it costs", "Plans & pricing"
    };
    private static final String[] PRICING_LEAD = {
            "Simple plans. Click a plan box to continue to contact.",
            "No surprises \u2014 pick the {brand} plan that fits and say hello.",
            "Transparent prices from {brand}. Tap a plan to get in touch.",
    };
    private static final String[] BLOG = {
            "Blog", "The {brand} journal", "Field notes from {brand}", "Stories & updates", "Latest from {brand}"
    };
    private static final String[] BLOG_LEAD = {
            "Stories from the {brand} team. Each card opens a related page.",
            "News, ideas and behind-the-scenes from {brand}.",
            "What we are thinking about at {brand} this season.",
    };
    private static final String[] CAREERS = {
            "Careers at {brand}", "Join {brand}", "Work with {brand}", "Build the next chapter at {brand}"
    };
    private static final String[] CAREERS_LEAD = {
            "Join a team that cares about craft. Click a role to apply via Contact.",
            "{brand} is growing. Find a role and tell us why you\u2019re the one.",
            "Good people, good work. See where you could fit at {brand}.",
    };
    private static final String[] CONTACT = {
            "Contact {brand}", "Talk to {brand}", "Say hello to {brand}", "Visit or reach {brand}"
    };
    private static final String[] CONTACT_LEAD = {
            "Tell us what you want to build or book. We\u2019ll respond within one business day.",
            "Questions, bookings, ideas \u2014 {brand} reads every message.",
            "Drop {brand} a line and we\u2019ll get back to you within a day.",
    };
    private static final String[] CTA = {
            "Get started", "Say hello", "Let\u2019s talk", "Join us", "Plan a visit", "Book now"
    };
    private static final String[][] PLANS = {
            {"Starter", "Growth", "Enterprise"},
            {"Basic", "Plus", "Premier"},
            {"Explorer", "Regular", "Insider"},
            {"Solo", "Team", "Studio"},
    };
    private static final String[][] BLOG_TITLES = {
            {"Behind the experience", "Meet the makers", "Ask us anything"},
            {"What\u2019s new this season", "A day in the life", "Questions we hear most"},
            {"Our craft, up close", "People behind the name", "Your first visit, explained"},
    };
    private static final String[][] ROLES = {
            {"Front of house lead", "Experience designer", "Operations manager", "Guest success specialist"},
            {"Community host", "Creative producer", "Programme coordinator", "Customer care partner"},
            {"Team lead", "Brand storyteller", "Logistics planner", "Support specialist"},
    };

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern COLOR_WORD = Pattern.compile(
            "\\b(red|blue|green|orange|purple|violet|teal|turquoise|pink|gold|yellow|black|white|crimson|navy|maroon)\\b");
    private static final Pattern FONT_WORD = Pattern.compile(
            "(poppins|playfair|montserrat|roboto|lato|open sans|\\bserif\\b|sans-serif|\\bfont\\b)");
    private static final Pattern BRAND_CALLED = Pattern.compile(
            "(?i:\\b(?:called|named))\\s+((?:[A-Z][A-Za-z0-9]+)(?:\\s+[A-Z][A-Za-z0-9]+){0,2})");
    private static final Pattern BRAND_FOR = Pattern.compile("\\b(?:for|For)\\s+([A-Z][A-Za-z0-9]{2,})\\b");

    private SiteVariant() {
    }

    /** The business name in the prompt ("called Harmony Notes", "for OliveGrove"), or {@code fallback}. */
    static String brandFrom(String story, String fallback) {
        if (story != null) {
            Matcher m = BRAND_CALLED.matcher(story);
            if (m.find()) {
                return m.group(1).trim();
            }
            m = BRAND_FOR.matcher(story);
            if (m.find()) {
                return m.group(1);
            }
        }
        return fallback;
    }

    /** Stable per-brand seed. */
    static int seedFor(String brand) {
        String key = brand == null ? "" : brand.trim().toLowerCase(Locale.ROOT);
        int h = 0x811C9DC5;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x01000193;
        }
        return h & 0x7fffffff;
    }

    static Design design(String brand, String story) {
        String b = brand == null || brand.isBlank() ? "NovaTech" : brand.trim();
        String s = story == null ? "" : story;
        String lower = s.toLowerCase(Locale.ROOT);
        SiteThemePack.Theme base = SiteThemePack.fromStory(s);
        int seed = seedFor(b);

        boolean darkBase = luminance(base.bg()) < 0.4;
        boolean colorAsked = HEX.matcher(s).find() || COLOR_WORD.matcher(lower).find()
                || lower.contains("dark mode") || lower.contains("dark theme");
        boolean fontAsked = FONT_WORD.matcher(lower).find();

        // palette: rotate the accent hue, rebuild the tints from it
        String accent = base.accent();
        String soft = base.soft();
        String dark = base.dark();
        String bg = base.bg();
        if (!colorAsked) {
            int[] shifts = {-39, -26, -13, 17, 13, 26, 39};
            int shift = shifts[Math.floorMod(mix(seed, 1), shifts.length)];
            float[] hsl = toHsl(accent);
            float hue = (hsl[0] + shift + 360f) % 360f;
            float sat = Math.max(0.45f, Math.min(0.85f, hsl[1]));
            float light = Math.max(0.30f, Math.min(0.46f, hsl[2]));
            accent = fromHsl(hue, sat, light);
            if (!darkBase) {
                soft = fromHsl(hue, 0.70f, 0.95f);
                dark = fromHsl(hue, 0.45f, 0.13f);
                if (luminance(bg) > 0.9) {
                    bg = fromHsl(hue, 0.60f, 0.985f);
                }
            }
        }

        // typography
        FontPair pair = FONT_PAIRS[Math.floorMod(mix(seed, 2), FONT_PAIRS.length)];
        String bodyFont = base.fontFamily();
        String fontsHref = base.googleFontsHref();
        String headFont = base.fontFamily();
        if (!fontAsked) {
            bodyFont = pair.body();
            headFont = pair.head();
            fontsHref = pair.href();
        }

        String layout = LAYOUTS[Math.floorMod(mix(seed, 3), LAYOUTS.length)];
        int radius = RADII[Math.floorMod(mix(seed, 4), RADII.length)];
        int weight = WEIGHTS[Math.floorMod(mix(seed, 5), WEIGHTS.length)];

        String[] promises = promisesFor(base.kind());
        String place = placeFor(base.kind());
        String promise = promises[Math.floorMod(mix(seed, 6), promises.length)];
        String tagline = base.homeHeadline();
        String hero = fill(pick(HERO_TEMPLATES, seed, 7), b, tagline, promise, place, base.homeLead(), base.servicesLabel());
        String lead = fill(pick(LEAD_TEMPLATES, seed, 8), b, tagline, promise, place, base.homeLead(), base.servicesLabel());
        String servicesHeading = fill(pick(SERVICES, seed, 9), b, tagline, promise, place, base.homeLead(), base.servicesLabel());

        SiteThemePack.Theme theme = new SiteThemePack.Theme(
                base.kind(), accent, soft, dark, bg, base.ink(), base.muted(), bodyFont, fontsHref,
                base.heroImage(), base.cardImages(), hero, lead, servicesHeading,
                base.serviceTitles(), base.serviceBlurbs(), base.serviceLinks());

        return new Design(
                seed, theme, headFont, fontsHref, layout, radius, weight,
                pick(KICKERS, seed, 10),
                hero,
                lead,
                fill(pick(ABOUT, seed, 11), b),
                pick(EXPLORE, seed, 12),
                servicesHeading,
                fill(pick(SERVICES_LEAD, seed, 13), b),
                fill(pick(PRICING, seed, 14), b),
                fill(pick(PRICING_LEAD, seed, 15), b),
                fill(pick(BLOG, seed, 16), b),
                fill(pick(BLOG_LEAD, seed, 17), b),
                fill(pick(CAREERS, seed, 18), b),
                fill(pick(CAREERS_LEAD, seed, 19), b),
                fill(pick(CONTACT, seed, 20), b),
                fill(pick(CONTACT_LEAD, seed, 21), b),
                pick(CTA, seed, 22),
                PLANS[Math.floorMod(mix(seed, 23), PLANS.length)],
                BLOG_TITLES[Math.floorMod(mix(seed, 24), BLOG_TITLES.length)],
                ROLES[Math.floorMod(mix(seed, 25), ROLES.length)],
                accent.toLowerCase(Locale.ROOT) + "|" + headFont + "|" + layout + "|" + radius
        );
    }

    // ------------------------------------------------------------------ copy

    private static String[] promisesFor(SiteThemePack.Kind kind) {
        return switch (kind) {
            case RESTAURANT -> new String[]{"Seasonal plates, slow evenings", "Fire, farm and flavour",
                    "Where every table tells a story", "Dinner you will want to replay"};
            case TRAVEL -> new String[]{"Trips planned around you", "Leave the checklist at home",
                    "Roads, routes and real discoveries", "Wander further, worry less"};
            case MUSIC -> new String[]{"Find your sound", "From first scales to centre stage",
                    "Every lesson has a rhythm", "Make music that moves people"};
            case WEDDING -> new String[]{"Your story, beautifully told", "Vows, flowers and no stress",
                    "A day planned down to the petal", "Forever starts here"};
            case PETS -> new String[]{"Happy tails, calm owners", "Care that speaks fluent pet",
                    "A second home for your best friend", "Wag-worthy care every day"};
            case BOOKS -> new String[]{"Pages worth getting lost in", "Coffee, covers and quiet corners",
                    "Read more, wander slowly", "Stories picked by real readers"};
            case GARDEN -> new String[]{"Grow something wonderful", "Roots, shoots and good advice",
                    "Green spaces start small", "Bring the outdoors home"};
            case GAMING -> new String[]{"Play loud, win together", "Built by gamers, for gamers",
                    "Every match is a story", "Level up with the squad"};
            case SPACE -> new String[]{"The universe, up close", "Look up and keep looking",
                    "Stars, shows and big questions", "Wonder on a cosmic scale"};
            case CLEANING -> new String[]{"Fresh starts, every visit", "Spotless without the effort",
                    "Clean spaces, clear heads", "Trusted hands, gleaming results"};
            case HOSPITAL -> new String[]{"Care that listens first", "Health made simpler",
                    "Your wellbeing, our priority", "Clear answers, kind people"};
            case FITNESS -> new String[]{"Stronger every single week", "Move more, feel unstoppable",
                    "Coaching that meets you where you are", "Sweat, smile, repeat"};
            case BANK -> new String[]{"Money, made clear", "Banking without the fine print",
                    "Save, spend and grow with confidence", "Finance that keeps up with you"};
            case TECH -> new String[]{"Software people actually enjoy", "Build it once, ship it everywhere",
                    "Tools that keep teams moving", "Ideas turned into products"};
        };
    }

    private static String placeFor(SiteThemePack.Kind kind) {
        return switch (kind) {
            case RESTAURANT -> "neighbourhood table";
            case TRAVEL -> "travel studio";
            case MUSIC -> "music academy";
            case WEDDING -> "wedding team";
            case PETS -> "pet-care crew";
            case BOOKS -> "local bookshop";
            case GARDEN -> "garden centre";
            case GAMING -> "esports club";
            case SPACE -> "window to the stars";
            case CLEANING -> "cleaning crew";
            case HOSPITAL -> "care team";
            case FITNESS -> "training studio";
            case BANK -> "money partner";
            case TECH -> "product team";
        };
    }

    private static String pick(String[] options, int seed, int salt) {
        return options[Math.floorMod(mix(seed, salt), options.length)];
    }

    private static String fill(String template, String brand) {
        return template.replace("{brand}", brand);
    }

    private static String fill(String template, String brand, String tagline, String promise, String place,
                               String lead, String label) {
        String leadLower = lead == null || lead.isEmpty() ? "" : Character.toLowerCase(lead.charAt(0)) + lead.substring(1);
        return template
                .replace("{brand}", brand)
                .replace("{tagline}", tagline == null ? "" : tagline)
                .replace("{promise}", promise)
                .replace("{place}", place)
                .replace("{leadLower}", leadLower)
                .replace("{lead}", lead == null ? "" : lead)
                .replace("{label}", label == null ? "" : label);
    }

    /** Cheap integer hash so each design choice uses a different slice of the seed. */
    static int mix(int seed, int salt) {
        int h = seed * 0x9E3779B1 + salt * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 13;
        h *= 0x297A2D39;
        h ^= h >>> 16;
        return h & 0x7fffffff;
    }

    // ----------------------------------------------------------------- color

    private static double luminance(String hex) {
        int[] rgb = rgb(hex);
        return (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255.0;
    }

    private static int[] rgb(String hex) {
        String h = hex == null ? "" : hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.length() == 3) {
            h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
        }
        if (h.length() != 6) {
            return new int[]{255, 255, 255};
        }
        try {
            return new int[]{
                    Integer.parseInt(h.substring(0, 2), 16),
                    Integer.parseInt(h.substring(2, 4), 16),
                    Integer.parseInt(h.substring(4, 6), 16)};
        } catch (NumberFormatException e) {
            return new int[]{255, 255, 255};
        }
    }

    private static float[] toHsl(String hex) {
        int[] c = rgb(hex);
        float r = c[0] / 255f;
        float g = c[1] / 255f;
        float b = c[2] / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float h;
        float s;
        if (max == min) {
            h = 0;
            s = 0;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) {
                h = (g - b) / d + (g < b ? 6 : 0);
            } else if (max == g) {
                h = (b - r) / d + 2;
            } else {
                h = (r - g) / d + 4;
            }
            h *= 60f;
        }
        return new float[]{h, s, l};
    }

    private static String fromHsl(float h, float s, float l) {
        float c = (1f - Math.abs(2f * l - 1f)) * s;
        float hp = (h % 360f) / 60f;
        float x = c * (1f - Math.abs(hp % 2f - 1f));
        float r1 = 0;
        float g1 = 0;
        float b1 = 0;
        if (hp < 1) {
            r1 = c;
            g1 = x;
        } else if (hp < 2) {
            r1 = x;
            g1 = c;
        } else if (hp < 3) {
            g1 = c;
            b1 = x;
        } else if (hp < 4) {
            g1 = x;
            b1 = c;
        } else if (hp < 5) {
            r1 = x;
            b1 = c;
        } else {
            r1 = c;
            b1 = x;
        }
        float m = l - c / 2f;
        return String.format(Locale.ROOT, "#%02x%02x%02x",
                Math.round((r1 + m) * 255), Math.round((g1 + m) * 255), Math.round((b1 + m) * 255));
    }
}
