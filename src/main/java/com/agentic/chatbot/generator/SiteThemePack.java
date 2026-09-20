package com.agentic.chatbot.generator;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Picks images, colors, and fonts from the user story (restaurant, travel, etc.).
 */
public final class SiteThemePack {

    public enum Kind {
        MUSIC, WEDDING, PETS, BOOKS, GARDEN, GAMING, SPACE, CLEANING,
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

    private static final String Q800 = "?auto=format&fit=crop&w=800&q=80";
    private static final String Q1400 = "?auto=format&fit=crop&w=1400&q=80";

    private SiteThemePack() {
    }

    public static Theme fromStory(String userStory) {
        String s = userStory == null ? "" : userStory.toLowerCase(Locale.ROOT);
        Kind kind = detectKind(s);
        Theme base = switch (kind) {
            case MUSIC -> music();
            case WEDDING -> wedding();
            case PETS -> pets();
            case BOOKS -> books();
            case GARDEN -> garden();
            case GAMING -> gaming();
            case SPACE -> space();
            case CLEANING -> cleaning();
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
                "music", "piano", "guitar", "concert", "wedding", "bridal",
                "pet", "dog", "cat", "book", "library", "garden", "plant", "nursery",
                "game", "gaming", "esports", "space", "planet", "museum", "cleaning",
                "theme", "look like", "show me");
    }

    private static Kind detectKind(String s) {
        // Most specific topics first
        if (containsAny(s, "music", "piano", "guitar", "violin", "orchestra", "concert",
                "choir", "song", "musician", "instrument", "harmonia", "notes academy", "music school", "music academy")) {
            return Kind.MUSIC;
        }
        if (containsAny(s, "wedding", "bridal", "bride", "groom", "marriage", "vow")) {
            return Kind.WEDDING;
        }
        if (containsAny(s, "pet", "pets", "dog", "cat", "puppy", "kitten", "boarding", "veterinary", "vet clinic", "paw")) {
            return Kind.PETS;
        }
        if (containsAny(s, "bookstore", "book shop", "library", "reading", "bookstore", "chapter", "books")) {
            return Kind.BOOKS;
        }
        if (containsAny(s, "garden", "nursery", "plant", "florist", "flower", "fern", "landscap")) {
            return Kind.GARDEN;
        }
        if (containsAny(s, "esport", "gaming", "gamer", "video game", "neonrift", "console")) {
            return Kind.GAMING;
        }
        if (containsAny(s, "planetarium", "astronomy", "space", "galaxy", "cosmos", "orion dome", "observatory")) {
            return Kind.SPACE;
        }
        if (containsAny(s, "cleaning", "cleaner", "maid", "janitor", "leafshine", "housekeep")) {
            return Kind.CLEANING;
        }
        if (containsAny(s, "restaurant", "cafe", "café", "dining", "food", "menu", "bistro", "kitchen", "chef")) {
            return Kind.RESTAURANT;
        }
        if (containsAny(s, "travel", "traveler", "traveller", "tourism", "tour", "hotel", "vacation",
                "holiday", "flight", "destination", "trip", "resort", "surf", "beach")) {
            return Kind.TRAVEL;
        }
        if (containsAny(s, "hospital", "clinic", "doctor", "patient", "medical", "healthcare", "health care", "dentist")) {
            return Kind.HOSPITAL;
        }
        if (containsAny(s, "gym", "fitness", "workout", "yoga", "sport", "athletic")) {
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

    private static Theme music() {
        return new Theme(
                Kind.MUSIC,
                "#7c3aed", "#f5f3ff", "#1e1b4b", "#faf9ff", "#1e1b4b", "#6b7280",
                "Poppins, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700;800&display=swap",
                // Mixing board / studio
                "https://images.unsplash.com/photo-1511379938547-c1f69419868d" + Q1400,
                new String[]{
                        // Headphones / listening
                        "https://images.unsplash.com/photo-1514320291840-2d0d18b0f0c0" + Q800,
                        // Live singer / concert
                        "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f" + Q800,
                        // Piano keys
                        "https://images.unsplash.com/photo-1507838153414-b4b713384bfd" + Q800,
                        // Vocalist with mic
                        "https://images.unsplash.com/photo-1516280440612-48037c9f3c5f" + Q800,
                        // DJ / stage lights
                        "https://images.unsplash.com/photo-1470225620780-dba8ba36b745" + Q800,
                        // Electric guitar
                        "https://images.unsplash.com/photo-1510915361894-db8b50106b35" + Q800,
                        // Drum kit
                        "https://images.unsplash.com/photo-1519892300165-cb5542fb47e1" + Q800,
                        // Vinyl / records
                        "https://images.unsplash.com/photo-1487180144351-b8472daed4fc" + Q800
                },
                "Learn music. Love every note.",
                "Lessons, ensembles, and stages where students grow from first scales to full performances.",
                "Programs",
                new String[]{"Piano", "Guitar", "Vocals", "Drums", "Kids classes", "Recitals"},
                new String[]{
                        "Private piano lessons for every level.",
                        "Acoustic and electric guitar pathways.",
                        "Voice coaching for solo and choir.",
                        "Drum kits, rhythm, and groove labs.",
                        "Fun starter classes for young musicians.",
                        "End-of-term concerts on a real stage."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme wedding() {
        return new Theme(
                Kind.WEDDING,
                "#db2777", "#fdf2f8", "#831843", "#fff7fb", "#831843", "#9f1239",
                "\"Playfair Display\", Georgia, serif",
                "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;600;700&family=Inter:wght@400;500;600;700&display=swap",
                "https://images.unsplash.com/photo-1519741497674-611481863552" + Q1400,
                new String[]{
                        "https://images.unsplash.com/photo-1465495976277-4387d4b0b4c6" + Q800,
                        "https://images.unsplash.com/photo-1520854221256-17451cc331bf" + Q800,
                        "https://images.unsplash.com/photo-1511285560929-80b4565780ab" + Q800,
                        "https://images.unsplash.com/photo-1606800052052-a08af7148866" + Q800,
                        "https://images.unsplash.com/photo-1522673607200-164d1b6ce486" + Q800,
                        "https://images.unsplash.com/photo-1460978812857-470ed1c77af0" + Q800
                },
                "Your day, beautifully planned.",
                "Full wedding planning, styling, and coordination so every moment feels intentional.",
                "Wedding services",
                new String[]{"Full planning", "Day-of coord", "Florals", "Venues", "Photography", "Styling"},
                new String[]{
                        "End-to-end planning from vision to vows.",
                        "Calm coordination when the day arrives.",
                        "Bouquets and installs that photograph beautifully.",
                        "Venue shortlists matched to your guest count.",
                        "Trusted photographer partners.",
                        "Tablescapes, lighting, and mood boards."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme pets() {
        return new Theme(
                Kind.PETS,
                "#0d9488", "#f0fdfa", "#134e4a", "#ffffff", "#134e4a", "#5eead4",
                "Montserrat, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1450778869180-41d0601e046e" + Q1400,
                new String[]{
                        "https://images.unsplash.com/photo-1587300003388-59208cc962f0" + Q800,
                        "https://images.unsplash.com/photo-1548199973-03cce0bbc87b" + Q800,
                        "https://images.unsplash.com/photo-1514888286974-6c03e2ca1dba" + Q800,
                        "https://images.unsplash.com/photo-1530281700549-e82e7bf110d6" + Q800,
                        "https://images.unsplash.com/photo-1601758228041-f3b2795255f1" + Q800,
                        "https://images.unsplash.com/photo-1583511655857-d19b40a7a54e" + Q800
                },
                "Happy pets. Peaceful owners.",
                "Boarding, daycare, and care plans designed around how animals actually feel safe.",
                "Pet care",
                new String[]{"Boarding", "Daycare", "Grooming", "Training", "Vet visits", "Pickup"},
                new String[]{
                        "Overnight stays with webcam check-ins.",
                        "Playgroups matched by energy level.",
                        "Baths, nails, and gentle coat care.",
                        "Basic manners and confidence building.",
                        "Partner clinics for routine checkups.",
                        "Door-to-door transport for busy weeks."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme books() {
        return new Theme(
                Kind.BOOKS,
                "#7c2d12", "#fff7ed", "#1c1917", "#fffaf5", "#1c1917", "#a8a29e",
                "\"Playfair Display\", Georgia, serif",
                "https://fonts.googleapis.com/css2?family=Playfair+Display:wght@400;600;700&family=Inter:wght@400;500;600;700&display=swap",
                "https://images.unsplash.com/photo-1507842217343-583bb7270b66" + Q1400,
                new String[]{
                        "https://images.unsplash.com/photo-1481627834876-b7833e8f5570" + Q800,
                        "https://images.unsplash.com/photo-1524995997943-a5c4d61d9a1c" + Q800,
                        "https://images.unsplash.com/photo-1512820790803-83ca734da794" + Q800,
                        "https://images.unsplash.com/photo-1495446815901-a7297e633e8d" + Q800,
                        "https://images.unsplash.com/photo-1526243741027-444d633d7365" + Q800,
                        "https://images.unsplash.com/photo-1521587760476-6c12a4b040da" + Q800
                },
                "Books, coffee, and quiet corners.",
                "A neighborhood bookstore-café for readers who linger, browse, and discover.",
                "For readers",
                new String[]{"New releases", "Events", "Membership", "Café", "Kids corner", "Orders"},
                new String[]{
                        "Weekly shelves of fiction and nonfiction.",
                        "Author nights and book clubs.",
                        "Member perks and early holds.",
                        "Espresso and pastry while you browse.",
                        "Story time and junior shelves.",
                        "Special orders arrive fast."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme garden() {
        return new Theme(
                Kind.GARDEN,
                "#15803d", "#f0fdf4", "#14532d", "#ffffff", "#14532d", "#86efac",
                "\"Open Sans\", Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;600;700&display=swap",
                "https://images.unsplash.com/photo-1416879595882-3373a0480b5b" + Q1400,
                new String[]{
                        "https://images.unsplash.com/photo-1466692476866-aef1dfb1e735" + Q800,
                        "https://images.unsplash.com/photo-1416879595882-3373a0480b5b" + Q800,
                        "https://images.unsplash.com/photo-1485955900006-10f4d324a462" + Q800,
                        "https://images.unsplash.com/photo-1459156212016-c812103e8a98" + Q800,
                        "https://images.unsplash.com/photo-1463936577151-ac2dd4aa5c1e" + Q800,
                        "https://images.unsplash.com/photo-1501004318641-b39e6451bec6" + Q800
                },
                "Grow greener, one plant at a time.",
                "Nursery plants, soil advice, and garden design for balconies and backyards.",
                "Garden services",
                new String[]{"Indoor plants", "Outdoor beds", "Soil & pots", "Design", "Delivery", "Workshops"},
                new String[]{
                        "Low-light and bright-room favorites.",
                        "Seasonal flowers and edibles.",
                        "Mixes and containers that drain right.",
                        "Layout plans for small spaces.",
                        "Doorstep plant delivery.",
                        "Weekend workshops for beginners."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme gaming() {
        return new Theme(
                Kind.GAMING,
                "#2563eb", "#0f172a", "#020617", "#0b1220", "#e2e8f0", "#94a3b8",
                "Montserrat, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1542751371-adc38448a05e" + Q1400,
                new String[]{
                        "https://images.unsplash.com/photo-1538481199705-c710c4ea71b0" + Q800,
                        "https://images.unsplash.com/photo-1493711662062-fa541f7f3d94" + Q800,
                        "https://images.unsplash.com/photo-1511512578047-dfb367046420" + Q800,
                        "https://images.unsplash.com/photo-1552820728-8b83bb6b773f" + Q800,
                        "https://images.unsplash.com/photo-1550745165-9bc0b252726f" + Q800,
                        "https://images.unsplash.com/photo-1593305841991-05c297ba4575" + Q800
                },
                "Compete louder. Fan harder.",
                "Roster news, merch drops, and match nights for fans who live the game.",
                "Fan hub",
                new String[]{"Roster", "Matches", "Merch", "Streams", "Academy", "Sponsors"},
                new String[]{
                        "Meet the players behind the tags.",
                        "Schedules and ticketed watch parties.",
                        "Jerseys and limited drops.",
                        "Live streams and VODs.",
                        "Youth tryouts and coaching.",
                        "Partner brands and community events."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme space() {
        return new Theme(
                Kind.SPACE,
                "#f59e0b", "#0f172a", "#020617", "#020617", "#e2e8f0", "#94a3b8",
                "Inter, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap",
                "https://images.unsplash.com/photo-1446776877081-d282a0f896e2" + Q1400,
                new String[]{
                        "https://images.unsplash.com/photo-1462331940025-496dfbfc7564" + Q800,
                        "https://images.unsplash.com/photo-1451187580459-43490279c0fa" + Q800,
                        "https://images.unsplash.com/photo-1444703686981-aaf876c3ff9a" + Q800,
                        "https://images.unsplash.com/photo-1464802686167-b939a6910659" + Q800,
                        "https://images.unsplash.com/photo-1419242902214-272b3f66ee7a" + Q800,
                        "https://images.unsplash.com/photo-1502134249126-9f3755a50d30" + Q800
                },
                "Look up. Wonder more.",
                "Exhibits, shows, and education programs that bring the night sky close.",
                "Visit",
                new String[]{"Dome shows", "Exhibits", "Tickets", "School trips", "Night sky", "Shop"},
                new String[]{
                        "Immersive planetarium presentations.",
                        "Hands-on astronomy galleries.",
                        "Timed entry and memberships.",
                        "Curriculum-aligned field trips.",
                        "Seasonal stargazing evenings.",
                        "Souvenirs and star charts."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
    }

    private static Theme cleaning() {
        return new Theme(
                Kind.CLEANING,
                "#059669", "#ecfdf5", "#064e3b", "#ffffff", "#064e3b", "#6b7280",
                "Lato, Arial, sans-serif",
                "https://fonts.googleapis.com/css2?family=Lato:wght@400;700&display=swap",
                "https://images.unsplash.com/photo-1581578731548-c64695cc6952" + Q1400,
                new String[]{
                        "https://images.unsplash.com/photo-1563453392212-326f5e854473" + Q800,
                        "https://images.unsplash.com/photo-1527515637462-dff97aa6706f" + Q800,
                        "https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf" + Q800,
                        "https://images.unsplash.com/photo-1556912173-46c336c7fd55" + Q800,
                        "https://images.unsplash.com/photo-1585421514284-efb74c2b69ba" + Q800,
                        "https://images.unsplash.com/photo-1600585154340-be6161a56a0c" + Q800
                },
                "Homes that feel freshly reset.",
                "Eco-minded cleaning for apartments, offices, and move-outs.",
                "Cleaning plans",
                new String[]{"Standard clean", "Deep clean", "Move-out", "Offices", "Eco products", "Recurring"},
                new String[]{
                        "Weekly freshen-ups that stick to a checklist.",
                        "Detail work for kitchens and bathrooms.",
                        "Empty-home cleans before keys exchange.",
                        "Desk areas and shared spaces.",
                        "Plant-based supplies on request.",
                        "Same team, same day each week."
                },
                new String[]{"/site/services", "/site/pricing", "/site/blog", "/site/about", "/site/contact", "/site/careers"}
        );
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
