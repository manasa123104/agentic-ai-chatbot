package com.agentic.chatbot.generator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks images, colors, and fonts from the user story (restaurant, travel, etc.).
 */
public final class SiteThemePack {

    public enum Kind {
        RESTAURANT, TRAVEL, HOSPITAL, FITNESS, BANK, TECH
    }

    public record Theme(
            Kind kind,
            String accent,
            String soft,
            String dark,
            String bg,
            String ink,
            String muted,
            String fontFamily,
            String googleFontsHref,
            String heroImage,
            String[] cardImages,
            String homeHeadline,
            String homeLead,
            String servicesLabel,
            String[] serviceTitles,
            String[] serviceBlurbs,
            String[] serviceLinks
    ) {
    }

    private SiteThemePack() {
    }

    public static Theme fromStory(String userStory) {
        String s = userStory == null ? "" : userStory.toLowerCase(Locale.ROOT);
        Kind kind = detectKind(s);
        Theme base = switch (kind) {
            case RESTAURANT -> restaurant();
            case TRAVEL -> travel();
            case HOSPITAL -> hospital();
            case FITNESS -> fitness();
            case BANK -> bank();
            case TECH -> tech();
        };
        return applyRequestedStyle(base, s, userStory);
    }

    /**
     * Apply color/font/dark-mode from an edit instruction on top of an existing theme.
     * Instruction always wins over colors buried in the previous story.
     */
    public static Theme applyStyleOverrides(Theme base, String instruction) {
        if (instruction == null || instruction.isBlank()) {
            return base;
        }
        return applyRequestedStyle(base, instruction.toLowerCase(Locale.ROOT), instruction);
    }

    /**
     * Keep colors/fonts from {@code styled}, take hero/card images (and copy) from {@code images}.
     */
    public static Theme withImagesFrom(Theme styled, Theme images) {
        if (images == null) {
            return styled;
        }
        return new Theme(
                images.kind(),
                styled.accent(), styled.soft(), styled.dark(), styled.bg(), styled.ink(), styled.muted(),
                styled.fontFamily(), styled.googleFontsHref(),
                images.heroImage(), images.cardImages(),
                images.homeHeadline(), images.homeLead(), images.servicesLabel(),
                images.serviceTitles(), images.serviceBlurbs(), images.serviceLinks()
        );
    }

    /** True when the text asks for a theme/image change. */
    public static boolean requestsImageTheme(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String s = text.toLowerCase(Locale.ROOT);
        return containsAny(s,
                "image", "images", "photo", "picture", "pictures", "photos",
                "restaurant", "travel", "traveler", "hotel", "cafe", "café",
                "gym", "fitness", "hospital", "clinic", "bank", "food", "beach",
                "theme", "look like", "show me");
    }

    private static Kind detectKind(String s) {
        if (containsAny(s, "restaurant", "cafe", "café", "dining", "food", "menu", "bistro", "kitchen", "chef")) {
            return Kind.RESTAURANT;
        }
        if (containsAny(s, "travel", "traveler", "traveller", "tourism", "tour", "hotel", "vacation",
                "holiday", "flight", "destination", "trip", "resort")) {
            return Kind.TRAVEL;
        }
        if (containsAny(s, "hospital", "clinic", "doctor", "patient", "health", "medical", "care")) {
            return Kind.HOSPITAL;
        }
        if (containsAny(s, "gym", "fitness", "workout", "yoga", "sport")) {
            return Kind.FITNESS;
        }
        if (containsAny(s, "bank", "finance", "fintech", "loan", "insurance")) {
            return Kind.BANK;
        }
        return Kind.TECH;
    }

    private static boolean containsAny(String s, String... words) {
        for (String w : words) {
            if (s.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static Theme applyRequestedStyle(Theme base, String lower, String original) {
        String accent = base.accent;
        String soft = base.soft;
        String bg = base.bg;
        String ink = base.ink;
        String dark = base.dark;
        String font = base.fontFamily;
        String fontsHref = base.googleFontsHref;

        String src = original == null ? "" : original;

        // Hex color — last match wins
        Matcher hex = Pattern.compile("(?i)(?:color|colour|accent|theme)?\\s*(?:is|=|:)?\\s*(#[0-9a-f]{6})").matcher(src);
        String lastHex = null;
        while (hex.find()) {
            lastHex = hex.group(1);
        }
        if (lastHex != null) {
            accent = lastHex;
            soft = "#f8fafc";
        } else {
            // Last named color in the text wins (so "orange ... pink" → pink)
            String named = lastColorName(lower);
            if (named != null) {
                switch (named) {
                    case "red" -> { accent = "#c62828"; soft = "#ffebee"; }
                    case "blue" -> { accent = "#1565c0"; soft = "#e3f2fd"; }
                    case "green" -> { accent = "#2e7d32"; soft = "#e8f5e9"; }
                    case "orange", "warm" -> { accent = "#ef6c00"; soft = "#fff3e0"; }
                    case "purple", "violet" -> { accent = "#6a1b9a"; soft = "#f3e5f5"; }
                    case "teal", "turquoise" -> { accent = "#00897b"; soft = "#e0f2f1"; }
                    case "pink" -> { accent = "#d81b60"; soft = "#fce4ec"; bg = "#fff5f8"; }
                    case "gold", "yellow" -> { accent = "#f9a825"; soft = "#fffde7"; ink = "#3e2723"; }
                    case "black" -> { accent = "#111827"; soft = "#f3f4f6"; }
                    case "white" -> { accent = "#9ca3af"; soft = "#ffffff"; bg = "#ffffff"; }
                    default -> { }
                }
            }
        }

        if (lower.contains("dark mode") || lower.contains("dark theme") || lower.contains("black background")) {
            bg = "#0f172a";
            ink = "#e2e8f0";
            dark = "#020617";
            soft = "#1e293b";
        } else if (lower.contains("white background") || lower.contains("light theme") || lower.contains("pink")) {
            if (lower.contains("pink") && !lower.contains("dark")) {
                bg = "#fff5f8";
                ink = "#4a1528";
            } else if (lower.contains("white background") || lower.contains("light theme")) {
                bg = "#ffffff";
                ink = "#0f172a";
            }
        }

        if (lower.contains("poppins")) {
            font = "Poppins, Arial, sans-serif";
            fontsHref = "https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700;800&display=swap";
        } else if (lower.contains("playfair") || (lower.contains("serif") && !lower.contains("sans"))) {
            font = "\"Playfair Display\", Georgia, serif";
            fontsHref = "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;600;700&family=Inter:wght@400;500;600;700&display=swap";
        } else if (lower.contains("montserrat")) {
            font = "Montserrat, Arial, sans-serif";
            fontsHref = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap";
        } else if (lower.contains("roboto")) {
            font = "Roboto, Arial, sans-serif";
            fontsHref = "https://fonts.googleapis.com/css2?family=Roboto:wght@400;500;700&display=swap";
        } else if (lower.contains("lato")) {
            font = "Lato, Arial, sans-serif";
            fontsHref = "https://fonts.googleapis.com/css2?family=Lato:wght@400;700&display=swap";
        } else if (lower.contains("open sans")) {
            font = "\"Open Sans\", Arial, sans-serif";
            fontsHref = "https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600;700&display=swap";
        }

        return new Theme(
                base.kind, accent, soft, dark, bg, ink, base.muted, font, fontsHref,
                base.heroImage, base.cardImages, base.homeHeadline, base.homeLead,
                base.servicesLabel, base.serviceTitles, base.serviceBlurbs, base.serviceLinks
        );
    }

    /** Returns the last color keyword found in text (pink after orange → pink). */
    private static String lastColorName(String lower) {
        if (lower == null || lower.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile(
                "\\b(red|blue|green|orange|warm|purple|violet|teal|turquoise|pink|gold|yellow|black|white)\\b",
                Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(lower);
        String last = null;
        while (m.find()) {
            last = m.group(1).toLowerCase(Locale.ROOT);
        }
        return last;
    }

    private static Theme restaurant() {
        return new Theme(
                Kind.RESTAURANT,
                "#c45c26", "#fff4ec", "#1c1917", "#fffaf7", "#1c1917", "#78716c",
                "\"Playfair Display\", Georgia, serif",
                "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;600;700&family=Inter:wght@400;500;600;700&display=swap",
                "https://images.unsplash.com/photo-1517248135467-4c7edcad34c4?auto=format&fit=crop&w=1400&q=80",
                new String[]{
                        "https://images.unsplash.com/photo-1414235077428-338989a2e8c0?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1559339352-11d035aa65de?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1555396273-367ea4eb4db5?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1544025162-d76694265947?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1466978913421-dad2ebd01d17?auto=format&fit=crop&w=800&q=80"
                },
                "Reserve a table. Taste the season.",
                "A warm dining experience with chef-led menus, private events, and evenings worth remembering.",
                "Dining experiences",
                new String[]{"Seasonal menu", "Private dining", "Weekend brunch", "Wine list", "Catering", "Chef’s table"},
                new String[]{
                        "Farm-to-table plates that change with the harvest.",
                        "Intimate rooms for celebrations and corporate dinners.",
                        "Slow mornings with pastries, eggs, and coffee.",
                        "Curated pours paired with each course.",
                        "Bring our kitchen to your next gathering.",
                        "A multi-course evening at the pass."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme travel() {
        return new Theme(
                Kind.TRAVEL,
                "#0e7490", "#ecfeff", "#0f172a", "#ffffff", "#0f172a", "#64748b",
                "Poppins, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1488646953014-85cb44e25828?auto=format&fit=crop&w=1400&q=80",
                new String[]{
                        "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1469474968028-56623f02e42e?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1476514525535-07fb3b4ae5f1?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1523906834658-6e24ef2386f9?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1530521954074-e64f6810b32d?auto=format&fit=crop&w=800&q=80"
                },
                "Go farther. Travel lighter. Return inspired.",
                "Handcrafted itineraries, stay packages, and local guides for travelers who want more than a checklist.",
                "Trip experiences",
                new String[]{"Beach escapes", "City breaks", "Adventure tours", "Family packages", "Honeymoon plans", "Group travel"},
                new String[]{
                        "Sun, sand, and carefully paced resort days.",
                        "Culture-packed weekends in iconic cities.",
                        "Hiking, wildlife, and off-map discoveries.",
                        "Kid-friendly routes with flexible pacing.",
                        "Romantic stays and private transfers.",
                        "Shared journeys for friends and teams."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme hospital() {
        return new Theme(
                Kind.HOSPITAL,
                "#0284c7", "#e0f2fe", "#0c4a6e", "#ffffff", "#0f172a", "#64748b",
                "Inter, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1519494026892-80bbd2d6fd0d?auto=format&fit=crop&w=1400&q=80",
                new String[]{
                        "https://images.unsplash.com/photo-1576091160399-112ba8d25d1d?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1581594693702-fbdc51b2763b?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1551076805-e1869033fa41?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1631217868264-e5b90bb7e133?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1666214280557-f1b5022eb634?auto=format&fit=crop&w=800&q=80"
                },
                "Care that feels personal. Support that feels close.",
                "Appointments, specialists, and patient resources designed for clarity and calm.",
                "Care services",
                new String[]{"Primary care", "Specialists", "Diagnostics", "Emergency", "Wellness plans", "Patient portal"},
                new String[]{
                        "Same-week visits with trusted clinicians.",
                        "Cardiology, ortho, and more under one roof.",
                        "Labs and imaging with fast results.",
                        "24/7 triage when minutes matter.",
                        "Prevention programs for whole families.",
                        "Records, messages, and refills online."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme fitness() {
        return new Theme(
                Kind.FITNESS,
                "#ea580c", "#fff7ed", "#1c1917", "#ffffff", "#1c1917", "#78716c",
                "Montserrat, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1534438327276-14e5300c3a48?auto=format&fit=crop&w=1400&q=80",
                new String[]{
                        "https://images.unsplash.com/photo-1517836357463-d25dfeac3438?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1571019614242-c5c5dee9f50b?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1540496905036-5937c10647cc?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1574680096145-d05b474e2155?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1599058945522-28d584b6f14f?auto=format&fit=crop&w=800&q=80"
                },
                "Train hard. Recover smarter. Feel unstoppable.",
                "Classes, coaching, and memberships built for every fitness level.",
                "Training programs",
                new String[]{"Strength", "Cardio", "Yoga", "Personal training", "Group classes", "Nutrition"},
                new String[]{
                        "Progressive lifting for real results.",
                        "Intervals that burn and build stamina.",
                        "Mobility and calm in every session.",
                        "1:1 coaching tailored to your goals.",
                        "Energy-packed classes with community.",
                        "Simple plans that fuel performance."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme bank() {
        return new Theme(
                Kind.BANK,
                "#1d4ed8", "#eff6ff", "#0f172a", "#ffffff", "#0f172a", "#64748b",
                "Inter, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1565514020176-ebe93d0f6f1b?auto=format&fit=crop&w=1400&q=80",
                new String[]{
                        "https://images.unsplash.com/photo-1554224155-6726b3ff858f?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1579621970563-ebec7560ff3e?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1454165804606-c3d57bc86b40?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1563986768609-322da13575f3?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1601597111158-2fceff292cdc?auto=format&fit=crop&w=800&q=80"
                },
                "Banking that feels clear, fast, and secure.",
                "Accounts, transfers, and guidance designed for modern money moments.",
                "Financial services",
                new String[]{"Checking", "Savings", "Cards", "Loans", "Investing", "Business"},
                new String[]{
                        "Everyday spending with smart alerts.",
                        "Goals that grow with automatic saves.",
                        "Rewards without the fine print fog.",
                        "Transparent rates for life milestones.",
                        "Simple portfolios for long-term growth.",
                        "Tools built for growing companies."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme tech() {
        return new Theme(
                Kind.TECH,
                "#1a73e8", "#eef4ff", "#0b1220", "#ffffff", "#0f172a", "#64748b",
                "Inter, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=1400&q=80",
                new String[]{
                        "https://images.unsplash.com/photo-1460925895917-afdab827c52f?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1553877522-43269d4ea984?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1522071820081-009f0129c71c?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1519389950473-47ba0277781c?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1559136555-9303baea8ebd?auto=format&fit=crop&w=800&q=80",
                        "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?auto=format&fit=crop&w=800&q=80"
                },
                "Build faster. Ship smarter. Grow together.",
                "A complete company website experience generated from your user story.",
                "What we offer",
                new String[]{"Product websites", "Auth experiences", "Booking & commerce", "Support portals", "HR tools", "Automation"},
                new String[]{
                        "Multi-page marketing sites with pricing and lead capture.",
                        "Familiar sign-in and onboarding flows.",
                        "Appointments, catalogs, and checkout journeys.",
                        "Contact and ticket forms that convert.",
                        "Leave requests and internal workflows.",
                        "Tests generated alongside every screen."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }
}
