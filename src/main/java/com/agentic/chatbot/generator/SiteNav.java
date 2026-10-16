package com.agentic.chatbot.generator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The menu bar of one generated website: which pages it shows and what each is called.
 *
 * <p>The old generator printed the same seven corporate labels on every site (Home, About, Services, Pricing, Blog,
 * Careers, Contact). Now the page addresses stay stable (so routes and exports keep working) but every label comes
 * from the business: its topic, the words in the user stories ("wine" gives a Wine list, "reservations" gives
 * Reservations, "private dining" gives Private dining) and a seed made of the business name plus the story. Each site
 * shows 5 or 6 of its pages in the menu, so two sites never get the same menu bar and no site gets the fixed
 * seven-item corporate nav. A restaurant reads Menu / Private dining / Wine list / Reservations; a music school reads
 * Lessons / Class packs / Recitals / Book a lesson. An edit such as "make the nav about wine list" renames pages.
 */
final class SiteNav {

    /** What a page is for. The name shown to visitors is chosen separately. */
    enum Role { HOME, ABOUT, OFFER, TIERS, JOURNAL, TEAM, CONTACT }

    /**
     * One page of the site. The slug is the address ({@code /site/<slug>}) and stays one of the seven stock slugs so
     * routes, exports and the generated Spring controller keep working; the label is what the menu shows.
     * Pages that are not in the menu are still generated and reachable from cards and the footer.
     */
    record Item(String slug, String label, boolean inMenu) {
        String href() {
            return "/site/" + slug;
        }
    }

    /** The seven labels the generator used to print on every site. */
    static final List<String> GENERIC_LABELS = List.of("Home", "About", "Services", "Pricing", "Blog", "Careers", "Contact");

    /** Same list under the name the tests use. */
    static final List<String> STOCK_LABELS = GENERIC_LABELS;

    /** True when a menu is (mostly) the stock corporate one: five or more of the seven stock words. */
    static boolean isStockMenu(List<String> labels) {
        int hits = 0;
        for (String stock : STOCK_LABELS) {
            if (labels.stream().anyMatch(l -> l.equalsIgnoreCase(stock))) {
                hits++;
            }
        }
        return hits >= 5;
    }

    private static final List<String> SLUGS = List.of("home", "about", "services", "pricing", "blog", "careers", "contact");

    private SiteNav() {
    }

    static Role roleOf(String slug) {
        return switch (slug) {
            case "about" -> Role.ABOUT;
            case "services" -> Role.OFFER;
            case "pricing" -> Role.TIERS;
            case "blog" -> Role.JOURNAL;
            case "careers" -> Role.TEAM;
            case "contact" -> Role.CONTACT;
            default -> Role.HOME;
        };
    }

    static String slugOf(Role role) {
        return switch (role) {
            case ABOUT -> "about";
            case OFFER -> "services";
            case TIERS -> "pricing";
            case JOURNAL -> "blog";
            case TEAM -> "careers";
            case CONTACT -> "contact";
            case HOME -> "home";
        };
    }

    /** Menu label of a page; never the stock word unless the user asked for it. */
    static String label(List<Item> nav, String slug) {
        for (Item i : nav) {
            if (i.slug().equals(slug)) {
                return i.label();
            }
        }
        return slug.isEmpty() ? slug : Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }

    /** Only the pages shown in the menu bar, in order. */
    static List<Item> menu(List<Item> nav) {
        return nav.stream().filter(Item::inMenu).toList();
    }

    /** Labels of the pages shown in the menu bar, in order. */
    static List<String> menuLabels(List<Item> nav) {
        return menu(nav).stream().map(Item::label).toList();
    }

    /** Names of the three options on the page that used to be "Pricing" (Starter / Growth / Enterprise). */
    static String[] planNames(SiteThemePack.Kind kind, String[] fallback) {
        Vocab v = VOCAB.get(kind);
        return v == null ? fallback : v.tierNames().clone();
    }

    /** Three short feature lines per option, matching {@link #planNames}. */
    static String[] planBlurbs(SiteThemePack.Kind kind) {
        return VOCAB.get(kind).tierBlurbs().clone();
    }

    static String[] journalTitles(SiteThemePack.Kind kind) {
        return VOCAB.get(kind).journalTitles().clone();
    }

    static String[] teamTitles(SiteThemePack.Kind kind) {
        return VOCAB.get(kind).teamTitles().clone();
    }

    // ------------------------------------------------------------------ vocabulary

    /** Label choices and copy of one topic. Groups are separated by {@code |}. */
    private record Vocab(String[] home, String[] about, String[] offer, String[] tiers, String[] journal, String[] team,
                         String[] contact, String[] tierNames, String[] tierBlurbs, String[] journalTitles,
                         String[] teamTitles) {

        String[] labels(Role role) {
            return switch (role) {
                case HOME -> home;
                case ABOUT -> about;
                case OFFER -> offer;
                case TIERS -> tiers;
                case JOURNAL -> journal;
                case TEAM -> team;
                case CONTACT -> contact;
            };
        }
    }

    private static final Map<SiteThemePack.Kind, Vocab> VOCAB = new EnumMap<>(SiteThemePack.Kind.class);

    private static void put(SiteThemePack.Kind kind, String home, String about, String offer, String tiers, String journal,
                            String team, String contact, String tierNames, String tierBlurbs, String journalTitles,
                            String teamTitles) {
        VOCAB.put(kind, new Vocab(split(home), split(about), split(offer), split(tiers), split(journal), split(team),
                split(contact), split(tierNames), split(tierBlurbs), split(journalTitles), split(teamTitles)));
    }

    private static String[] split(String s) {
        return s.split("\\|");
    }

    static {
        put(SiteThemePack.Kind.RESTAURANT,
                "Welcome|Tonight|Our table|Come hungry",
                "Our kitchen|The chef's story|Fire and farm|Who we feed",
                "Menu|Seasonal plates|What's cooking|Dishes",
                "Tasting menus|Set dinners|Chef's table|Private dining",
                "Wine list|Notes from the pass|Pantry stories|Seasonal journal",
                "The brigade|Meet the kitchen|Front of house|Our crew",
                "Reservations|Book a table|Find us|Visit us",
                "Tasting menu|Chef's table|Private dining",
                "Five courses built around the week's market; Paired pours on request; Seats in the main dining room"
                        + "|Counter seats facing the pass; A menu that changes nightly; Ask the chef anything"
                        + "|Rooms for birthdays and team dinners; Custom menus; A dedicated host",
                "What is on the pass this week|Meet the producers|Questions about your first visit",
                "Head chef|Sous chef|Host and reservations|Sommelier");
        put(SiteThemePack.Kind.TRAVEL,
                "Start here|Where to?|Departures|Wander",
                "Our travel story|Why we roam|Meet the planners|How we travel",
                "Trips|Destinations|Itineraries|Journeys",
                "Trip styles|Packages|Group trips|Custom journeys",
                "Travel notes|Field guide|Road stories|Dispatches",
                "Trip planners|Local guides|Meet the guides|Our explorers",
                "Plan a trip|Start planning|Talk to a planner|Book a call",
                "Weekend escape|Signature journey|Fully custom",
                "Two to three days with flights and stays sorted; A local guide for one afternoon; Easy cancellation"
                        + "|Seven to ten days across several stops; Handpicked stays; A planner on call"
                        + "|Built from scratch around you; Private guides; Door-to-door transfers",
                "Where to go this season|A day with a local guide|Packing, visas and other questions",
                "Lead trip planner|Local guide|Travel concierge|Destination specialist");
        put(SiteThemePack.Kind.MUSIC,
                "Welcome|Warm up|Backstage|Tune in",
                "Our sound|Why we teach|Studio story|Who we are",
                "Lessons|Instruments|Ensembles|Studios",
                "Class packs|Lesson levels|Term options|Studio time",
                "Recitals|Practice notes|Concert diary|Sheet music",
                "Our teachers|Meet the faculty|The ensemble|Coaches",
                "Book a lesson|Trial class|Say hello|Join in",
                "First notes|Regular lessons|Performance track",
                "A trial lesson to find your instrument; A loaner instrument for a month; No commitment"
                        + "|Weekly one-to-one sessions; A practice plan with feedback; Flexible rescheduling"
                        + "|Ensemble rehearsals and recitals; Stage coaching; A recording session",
                "Spring recital line-up|How to practise when you are short on time|Choosing your first instrument",
                "Piano teacher|Vocal coach|Ensemble director|Front desk and bookings");
        put(SiteThemePack.Kind.WEDDING,
                "Begin here|Our promise|Save the date|Hello, lovebirds",
                "Our story|Why we plan|The planner's note|Behind the bouquet",
                "Planning|Celebrations|Your day|Wedding days",
                "Collections|Day-of coordination|Full planning|Elopements",
                "Real weddings|Inspiration|Mood boards|Love notes",
                "Planners|Meet the team|Our florists|The crew",
                "Book a consult|Check your date|Say hello|Start planning",
                "Day-of coordination|Full planning|Elopement",
                "Timeline, vendors and rehearsal handled; One planner on the day; Calm for you and your families"
                        + "|From first idea to last dance; Vendor shortlists; Design and styling"
                        + "|A small ceremony anywhere; Photographer and florals; Legal paperwork guidance",
                "A garden wedding in full bloom|Mood boards for a winter day|Questions couples ask first",
                "Lead planner|Floral designer|Day-of coordinator|Client concierge");
        put(SiteThemePack.Kind.PETS,
                "Welcome|Paws up|Wag in|Hello friend",
                "Our promise|Why we care|The pack|Our story",
                "Care|Stays and play|Daycare|Pet care",
                "Stay options|Day passes|Care plans|Boarding",
                "Pet tips|Tail tales|Happy stories|Care guide",
                "The pack|Pet carers|Meet the carers|Our vets",
                "Book a stay|Drop in|Say hello|Visit",
                "Day play|Overnight stay|Extended boarding",
                "Supervised play and a nap; Daily photo updates; Pick-up before dinner"
                        + "|A cosy private suite; An evening walk; Medication handled"
                        + "|A week or longer; Routine kept the same as home; A vet on call",
                "Settling a nervous pup|Meet this month's regulars|What to pack for a stay",
                "Lead carer|Dog walker|Groomer|Front desk host");
        put(SiteThemePack.Kind.BOOKS,
                "Welcome|Open here|Chapter one|Pull up a chair",
                "Our shelves|The bookseller's note|Why we read|Our story",
                "Books|Shelves|Browse|Recommended reads",
                "Book clubs|Subscriptions|Gift options|Reading plans",
                "Staff picks|Reading notes|Author events|New arrivals",
                "The booksellers|Meet the readers|Staff|Our readers",
                "Find us|Order a book|Say hello|Visit",
                "Monthly book box|Book club seat|Gift card",
                "One surprise book chosen by a bookseller; A handwritten note; Free local delivery"
                        + "|Meet monthly to talk it over; A discount on the pick; Tea provided"
                        + "|Any amount; Works in store and online; Never expires",
                "Staff picks this month|An evening with a local author|Books to read next",
                "Head bookseller|Children's books lead|Events host|Online orders");
        put(SiteThemePack.Kind.GARDEN,
                "Welcome|Grow here|Sprout|Step outside",
                "Our roots|Why we grow|The gardeners' note|Our story",
                "Plants|Our nursery|Garden range|Grow",
                "Garden plans|Planting packages|Seasonal care|Workshops",
                "Seasonal tips|Garden diary|Plant guide|What is blooming",
                "The gardeners|Meet the growers|Our crew|Horticulturists",
                "Visit the nursery|Ask a gardener|Say hello|Book a visit",
                "Starter bed|Seasonal care|Full garden design",
                "Soil, plants and a planting plan; Water-wise choices; A photo guide for the first month"
                        + "|Visits each season; Pruning and feeding; A pest check"
                        + "|Design drawings; Installation by our crew; Aftercare calls",
                "What to plant this month|How to save a thirsty fern|Compost without the smell",
                "Head gardener|Nursery grower|Landscape designer|Customer helper");
        put(SiteThemePack.Kind.GAMING,
                "Lobby|Press start|Spawn|Join the squad",
                "Our squad|Lore|Why we play|Origin story",
                "Games|Arenas|Tournaments|Play",
                "Memberships|Battle passes|Squad tiers|Pro entry",
                "Match reports|Patch notes|Highlights|Dev diary",
                "The roster|Meet the squad|Coaches|Players",
                "Join up|Queue up|Sign up|Say hello",
                "Casual|Competitive|Pro squad",
                "Open play nights; Practice rooms; Friendly lobbies"
                        + "|A weekly ladder; Coach reviews; Team scrims"
                        + "|Sponsored events; Travel support; Streaming kit",
                "Last night's finals recap|New season patch notes|Scouting new talent",
                "Head coach|Team captain|Shoutcaster|Community manager");
        put(SiteThemePack.Kind.SPACE,
                "Look up|Welcome|Liftoff|Gaze",
                "Our mission|Why we look up|Meet the stargazers|Our story",
                "Shows|Exhibits|Night sky|Domes",
                "Tickets|Visit passes|School visits|Membership",
                "Sky tonight|Star notes|Mission log|Astronomy news",
                "The astronomers|Meet the guides|Our crew|Educators",
                "Plan a visit|Book a show|Say hello|Find us",
                "Single show|Dome and exhibits|Night sky session",
                "Forty minutes under the dome; Narrated by an astronomer; Good for ages seven and up"
                        + "|A show plus exhibit hall access; A hands-on gallery; A cafe stop"
                        + "|A telescope evening with guides; Warm drinks; Free rebooking if it is cloudy",
                "What is in the sky this month|Meet our resident astronomer|Your first visit to the dome",
                "Lead astronomer|Dome presenter|Education officer|Visitor host");
        put(SiteThemePack.Kind.CLEANING,
                "Fresh start|Welcome|Sparkle|Hello",
                "Our promise|Why we clean|Our standards|Our story",
                "Cleans|What we clean|Our rounds|Spaces",
                "Plans|Visit options|Rates and rounds|Deep cleans",
                "Cleaning tips|Before and after|Home care notes|Stain guide",
                "The crew|Meet the cleaners|Our team|Trusted hands",
                "Book a clean|Get a quote|Say hello|Schedule",
                "Standard clean|Deep clean|Move-in clean",
                "Dusting, floors, bathrooms and kitchen; Eco-friendly products; The same crew each visit"
                        + "|Inside appliances and baseboards; Window tracks; A detailed checklist"
                        + "|An empty-home reset; Cupboards and drawers; Photo proof when done",
                "How to keep a kitchen fresh|Before and after: a deep clean|A stain guide for busy homes",
                "Crew lead|Deep-clean specialist|Scheduler|Quality checker");
        put(SiteThemePack.Kind.HOSPITAL,
                "Welcome|Care starts here|Check in|Hello",
                "Our approach|Why we care|Our clinic|Our story",
                "Care|Treatments|Specialties|Clinics",
                "Check-ups|Care packages|Visit types|Health plans",
                "Health notes|Wellness guide|Ask the doctor|Patient stories",
                "Our doctors|Meet the care team|Clinicians|Our nurses",
                "Book an appointment|Appointments|Find a clinic|Reach us",
                "General check-up|Specialist visit|Health screening",
                "A full review with your doctor; Same-week slots; Clear next steps"
                        + "|Referral or direct booking; Notes shared with you; A follow-up call"
                        + "|Blood work and vitals; Results explained; Annual reminders",
                "Preparing for your first appointment|Seasonal health reminders|Ask the doctor",
                "Family doctor|Nurse practitioner|Care coordinator|Front desk");
        put(SiteThemePack.Kind.FITNESS,
                "Warm up|Start|Welcome|Let's go",
                "Our why|The coaching way|Our gym|Our story",
                "Classes|Training|Workouts|Studios",
                "Memberships|Class packs|Trial week|Coaching plans",
                "Training log|Form checks|Member wins|Recipes",
                "Coaches|Meet the trainers|The crew|Instructors",
                "Join now|Try a class|Book a session|Say hello",
                "Drop-in|Monthly membership|One-to-one coaching",
                "Any class, pay as you go; Towels included; Bring a friend"
                        + "|Unlimited classes; A free assessment; Freeze any month"
                        + "|A personal plan; Weekly check-ins; Form feedback by video",
                "A four-week strength plan|Member of the month|Recovery tips that work",
                "Head coach|Yoga teacher|Nutrition coach|Front desk");
        put(SiteThemePack.Kind.BANK,
                "Welcome|Overview|Your money|Start",
                "Our principles|Why us|How we work|Our story",
                "Accounts|Savings and loans|Money tools|Ways to bank",
                "Rates|Account tiers|Fees explained|Compare accounts",
                "Money notes|Guides|Market notes|Plain-English finance",
                "Advisors|Meet your advisors|Our people|Specialists",
                "Open an account|Talk to an advisor|Reach us|Branches",
                "Everyday|Saver|Premier",
                "No monthly fee; A debit card and mobile app; Round-up savings"
                        + "|Higher interest on balances; Goals and pots; Withdraw any time"
                        + "|A dedicated advisor; Priority support; Travel cover",
                "How to build a three-month buffer|Understanding your interest|Scam safety checklist",
                "Relationship advisor|Mortgage specialist|Fraud analyst|Branch manager");
        put(SiteThemePack.Kind.TECH,
                "Overview|Start|Welcome|Launchpad",
                "Our approach|Why we build|The team and mission|Our story",
                "Products|Features|Solutions|What we build",
                "Plans|Editions|Packages|Compare options",
                "Changelog|Release notes|Engineering notes|Field notes",
                "The builders|Meet the team|People|Engineers",
                "Talk to us|Request a demo|Get in touch|Say hello",
                "Starter|Team|Scale",
                "Core features for one workspace; Email support; A free trial"
                        + "|Shared projects; Roles and permissions; Priority support"
                        + "|Single sign-on; Audit logs; Onboarding help",
                "What shipped this quarter|How we test every release|Roadmap questions answered",
                "Staff engineer|Product designer|Customer success|Support engineer");
    }

    // ------------------------------------------------------------------ story keywords

    /** A word in the story that names a page ("wine" gives a Wine list page). */
    private record Rule(Pattern when, Role role, String label) {
    }

    private static Rule rule(String regex, Role role, String label) {
        return new Rule(Pattern.compile("(?i)" + regex), role, label);
    }

    private static final List<Rule> RULES = List.of(
            rule("\\bwine|sommelier|cocktail", Role.JOURNAL, "Wine list"),
            rule("\\breserv|book(?:ing)? a table|table booking", Role.CONTACT, "Reservations"),
            rule("private (?:dining|events?|parties)|banquet|event space", Role.TIERS, "Private dining"),
            rule("\\bcatering", Role.TIERS, "Catering"),
            rule("\\bmember(?:ship)?s?\\b", Role.TIERS, "Memberships"),
            rule("\\bsubscription", Role.TIERS, "Subscriptions"),
            rule("\\bpricing|\\bpackages?\\b|\\bplans\\b|\\brates\\b", Role.TIERS, "Packages"),
            rule("\\bclass(?:es)?\\b|\\blessons?\\b|\\bcourses?\\b|\\bworkshops?\\b", Role.OFFER, "Classes"),
            rule("\\brooms?\\b|\\bsuites?\\b|\\bstays?\\b", Role.OFFER, "Rooms"),
            rule("\\btours?\\b|\\bitinerar", Role.OFFER, "Tours"),
            rule("\\badopt", Role.OFFER, "Adoption"),
            rule("\\bshop\\b|\\bstore\\b|order online|\\bbuy\\b", Role.OFFER, "Shop"),
            rule("\\bappointments?\\b", Role.CONTACT, "Appointments"),
            rule("\\bgallery\\b|\\bportfolio\\b|\\bphotos?\\b", Role.JOURNAL, "Gallery"),
            rule("\\brecitals?\\b|\\bconcerts?\\b|\\bevents?\\b", Role.JOURNAL, "Events"),
            rule("\\bcareers?\\b|\\bhiring\\b|\\bjobs?\\b|\\bvacanc", Role.TEAM, "Join the team"),
            rule("\\bblog\\b|\\bnews\\b|\\bjournal\\b|\\barticles?\\b", Role.JOURNAL, "Journal"),
            rule("\\bour story\\b|\\bmission\\b|\\bhistory\\b", Role.ABOUT, "Our story"));

    private static final Pattern NAV_EDIT = Pattern.compile(
            "(?i)\\b(?:nav(?:igation)?(?:\\s+(?:bar|menu|labels?|items?|links?))?|menu\\s+bar|top\\s+menu|page\\s+names?|headings?)\\b"
                    + "[^.\\n]{0,30}?\\b(?:about|around|for|on|with|to|called|named|as|=)\\s*:?\\s+([^.\\n]{2,120})");
    private static final Set<String> NAV_FILLER = Set.of("the", "a", "an", "my", "our", "site", "website", "page", "pages",
            "nav", "navigation", "menu", "labels", "items", "links");

    // ------------------------------------------------------------------ plan

    /**
     * The menu of the site for this business: stable for a brand and story, different for different ones.
     * {@code story} may end with an "Edit request:" tail ("make the nav about wine list"), which names pages directly.
     */
    static List<Item> build(String story, SiteThemePack.Kind kind, int brandSeed) {
        String base = StoryDetails.baseStory(story);
        int seed = SiteVariant.mix(brandSeed, SiteVariant.seedFor(base.toLowerCase(Locale.ROOT)));
        Vocab v = VOCAB.get(kind);

        // labels the story asks for, by role
        Map<Role, String> asked = new LinkedHashMap<>();
        for (Rule r : RULES) {
            if (r.when().matcher(base).find()) {
                asked.putIfAbsent(r.role(), r.label());
            }
        }
        // an edit like "make the nav about wine list" names pages directly
        Map<Role, String> custom = customLabels(story);

        // optional pages: the ones the story or an edit names come first, the seed fills up to three
        List<Role> optional = new ArrayList<>(List.of(Role.ABOUT, Role.TIERS, Role.JOURNAL, Role.TEAM));
        optional.sort(Comparator
                .comparing((Role r) -> !(asked.containsKey(r) || custom.containsKey(r)))
                .thenComparingInt(r -> SiteVariant.mix(seed, 100 + r.ordinal())));
        int named = (int) optional.stream().filter(r -> asked.containsKey(r) || custom.containsKey(r)).count();
        int optionalCount = Math.max(2 + Math.floorMod(SiteVariant.mix(seed, 99), 2), Math.min(4, named));
        List<Role> shown = new ArrayList<>(optional.subList(0, optionalCount));

        List<Role> order = new ArrayList<>();
        order.add(Role.HOME);
        order.add(Role.OFFER);
        for (Role r : List.of(Role.ABOUT, Role.TIERS, Role.JOURNAL, Role.TEAM)) {
            if (shown.contains(r)) {
                order.add(r);
            }
        }
        order.add(Role.CONTACT);
        // the seed decides whether the about-style page comes right after Home or later
        if (order.contains(Role.ABOUT) && Math.floorMod(SiteVariant.mix(seed, 98), 2) == 0) {
            order.remove(Role.ABOUT);
            order.add(1, Role.ABOUT);
        }
        // pages left out of the menu follow, so every slug still has a label
        for (Role r : List.of(Role.ABOUT, Role.TIERS, Role.JOURNAL, Role.TEAM)) {
            if (!order.contains(r)) {
                order.add(r);
            }
        }

        List<String> usedLabels = new ArrayList<>();
        Map<Role, Item> bySlug = new EnumMap<>(Role.class);
        int salt = 0;
        for (Role role : order) {
            String[] options = v.labels(role);
            String label = custom.get(role);
            if (label == null) {
                label = asked.get(role);
            }
            if (label == null) {
                int pick = Math.floorMod(SiteVariant.mix(seed, 60 + salt), options.length);
                label = options[pick];
                // never the same word twice in one menu bar
                for (int i = 1; i < options.length && containsIgnoreCase(usedLabels, label); i++) {
                    label = options[(pick + i) % options.length];
                }
            }
            salt++;
            if (containsIgnoreCase(usedLabels, label)) {
                label = label + " " + (role == Role.OFFER ? "range" : "page");
            }
            usedLabels.add(label);
            bySlug.put(role, new Item(slugOf(role), label, shown.contains(role) || role == Role.HOME
                    || role == Role.OFFER || role == Role.CONTACT));
        }

        // addresses stay in the stock order; the menu order is `order` minus hidden pages
        List<Item> out = new ArrayList<>();
        for (Role role : order) {
            if (bySlug.get(role).inMenu()) {
                out.add(bySlug.get(role));
            }
        }
        for (String slug : SLUGS) {
            Item item = bySlug.get(roleOf(slug));
            if (!item.inMenu()) {
                out.add(item);
            }
        }
        return List.copyOf(out);
    }

    /** Pages named by an "Edit request:" tail such as "make the nav about wine list, private dining". */
    private static Map<Role, String> customLabels(String story) {
        Map<Role, String> out = new EnumMap<>(Role.class);
        if (story == null) {
            return out;
        }
        int at = story.lastIndexOf("Edit request:");
        if (at < 0) {
            return out;
        }
        Matcher m = NAV_EDIT.matcher(story.substring(at + "Edit request:".length()));
        if (!m.find()) {
            return out;
        }
        List<String> labels = new ArrayList<>();
        for (String part : m.group(1).split(",|;|\\band\\b|&|/")) {
            String label = tidyLabel(part);
            if (!label.isEmpty() && !containsIgnoreCase(labels, label)) {
                labels.add(label);
            }
            if (labels.size() == 5) {
                break;
            }
        }
        Role[] roles = {Role.OFFER, Role.TIERS, Role.JOURNAL, Role.ABOUT, Role.TEAM};
        for (int i = 0; i < labels.size() && i < roles.length; i++) {
            out.put(roles[i], labels.get(i));
        }
        return out;
    }

    private static String tidyLabel(String raw) {
        String cleaned = StoryDetails.clean(raw).replaceAll("(?i)\\b(?:please|instead|too|only)\\b", " ").trim();
        List<String> words = new ArrayList<>();
        for (String w : cleaned.split("\\s+")) {
            if (words.isEmpty() && NAV_FILLER.contains(w.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (!w.isEmpty()) {
                words.add(w);
            }
        }
        while (!words.isEmpty() && NAV_FILLER.contains(words.get(words.size() - 1).toLowerCase(Locale.ROOT))) {
            words.remove(words.size() - 1);
        }
        if (words.isEmpty() || words.size() > 3) {
            return "";
        }
        String joined = String.join(" ", words);
        return Character.toUpperCase(joined.charAt(0)) + joined.substring(1);
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list) {
            if (s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
