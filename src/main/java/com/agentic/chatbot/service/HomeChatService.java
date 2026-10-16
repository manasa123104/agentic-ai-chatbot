package com.agentic.chatbot.service;

import com.agentic.chatbot.generator.SiteAgent;
import com.agentic.chatbot.generator.UploadedStory;
import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.ConversionResponse;
import com.agentic.chatbot.model.PipelineResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The conversation brain of the home chat ("/"): works out what a message means in the context of the conversation
 * (new build, follow-up change, style edit, plain question) and words BOT's answers the way a helpful assistant
 * would, instead of dumping the internal pipeline log.
 */
@Service
public class HomeChatService {

    /** What a chat message asks for. */
    public enum Intent {
        /** A new website / app: run the whole pipeline on it. */
        BUILD,
        /** A follow-up change to the current site ("add a chatbot agent"): rebuild from the earlier brief plus the change. */
        REFINE,
        /** A colour / font / image change: patch the current site. */
        EDIT,
        /** Just talking: a question, a greeting, thanks. */
        CHAT
    }

    private static final Pattern QUESTION_START = Pattern.compile(
            "(?i)^\\s*(?:(?:so|ok|okay|and|also|hey bot,?|bot,?)\\s+)*"
                    + "(what|why|how|who|when|where|which|is|are|was|were|does|did|do|tell me|explain)\\b");
    private static final Pattern GREETING = Pattern.compile(
            "(?i)^\\s*(hi|hello|hey|yo|thanks|thank you|thx|ok|okay|cool|great|nice|awesome|perfect|"
                    + "good (?:morning|afternoon|evening)|help)\\b[\\s\\S]{0,40}$");
    private static final Pattern SITE_WORDS = Pattern.compile(
            "(?i)\\b(web\\s?site|web\\s?page|landing page|site|portal|app|application|dashboard|login|chat\\s?bot|agent)\\b");
    private static final Pattern STORY_FORM = Pattern.compile("(?i)\\bas an? [^.\\n]{2,60},? i (?:want|need)\\b");
    private static final Pattern NEW_BUILD = Pattern.compile(
            "(?i)\\b(?:build|create|generate|develop|design|make)\\s+(?:me\\s+)?(?:a|an|my|our|another|new)\\b[^.\\n]{0,80}?"
                    + "\\b(?:web\\s?site|web\\s?page|landing page|site|portal)\\b");
    private static final Pattern EXPLICIT_EDIT = Pattern.compile(
            "(?i)\\b(?:edit|change|update|modify|restyle|recolor|redesign)\\s+(?:the\\s+|this\\s+|my\\s+)?"
                    + "(?:web\\s?site|site|page|colou?rs?|fonts?|images?|theme)\\b");
    private static final Pattern STYLE_WORDS = Pattern.compile(
            "(?i)\\b(colou?rs?|fonts?|typeface|background|accent|theme|palette|dark mode|light mode|images?|photos?|pictures?|"
                    + "pink|blue|red|green|orange|purple|violet|teal|turquoise|yellow|gold|black|white|dark|darker|light|lighter)\\b");
    private static final Pattern REFINE_START = Pattern.compile(
            "(?i)^\\s*(?:(?:ok|okay|now|then|and|also|please|can you|could you|would you|can we|let's|lets|i want you to|i'd like you to)\\s+)*"
                    + "(add|include|insert|put|remove|delete|drop|replace|shorten|lengthen|rename|move|hide|enable|give|"
                    + "with an?|with the|make|show|use)\\b");

    private static final String FOLLOW_UP_HEADER = "Follow-up changes requested in the conversation (apply all of them):";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path outputDir;

    public HomeChatService(@Value("${agentic.output-dir:generated-app}") String outputDir) {
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
    }

    // ------------------------------------------------------------------ what does the message mean?

    /**
     * Works out what to do with a message. Conversion requests are recognised earlier (they have their own service),
     * and uploaded stories are always a new build.
     *
     * @param hasSite  a website has been generated already
     * @param hasBrief the brief of that website is known, so a follow-up can be added to it
     */
    public static Intent classify(String message, boolean hasSite, boolean hasBrief) {
        String m = message == null ? "" : message.trim();
        if (m.isEmpty() || UploadedStory.hasUploads(m)) {
            return Intent.BUILD;
        }
        boolean storyForm = STORY_FORM.matcher(m).find();
        if (!storyForm && m.length() < 400) {
            boolean question = QUESTION_START.matcher(m).find()
                    && (m.endsWith("?") || !SITE_WORDS.matcher(m).find());
            boolean greeting = GREETING.matcher(m).find() && !SITE_WORDS.matcher(m).find();
            if (question || greeting) {
                return Intent.CHAT;
            }
        }
        if (!hasSite || storyForm) {
            return Intent.BUILD;
        }
        boolean brandNew = NEW_BUILD.matcher(m).find() && !EXPLICIT_EDIT.matcher(m).find();
        if (brandNew) {
            return Intent.BUILD;
        }
        boolean edit = SiteEditService.isEditMessage(m);
        if (edit && STYLE_WORDS.matcher(m).find()) {
            return Intent.EDIT;
        }
        boolean refinement = edit
                || (m.length() < 300 && REFINE_START.matcher(m).find())
                || (m.length() < 200 && SiteAgent.wantsAgent(m));
        if (refinement) {
            return hasBrief ? Intent.REFINE : (edit ? Intent.EDIT : Intent.BUILD);
        }
        return Intent.BUILD;
    }

    /** The earlier brief plus every follow-up change asked for since, so each rebuild keeps all of them. */
    public static String refinedBrief(String brief, List<String> followUps) {
        StringBuilder sb = new StringBuilder(brief == null ? "" : brief.trim());
        if (followUps != null && !followUps.isEmpty()) {
            if (!sb.toString().contains(FOLLOW_UP_HEADER)) {
                sb.append("\n\n").append(FOLLOW_UP_HEADER);
            }
            for (String f : followUps) {
                sb.append("\n- ").append(f.strip().replaceAll("\\s+", " "));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ facts about the current site

    /** Brand of the site on disk: from the chatbot agent file, else the page title; blank when unknown. */
    public String siteBrand() {
        try {
            Path agent = outputDir.resolve("SITE_AGENT.json");
            if (Files.exists(agent)) {
                JsonNode n = JSON.readTree(Files.readString(agent, StandardCharsets.UTF_8));
                String brand = n.path("brand").asText("").trim();
                if (!brand.isEmpty()) {
                    return brand;
                }
            }
            Path home = outputDir.resolve("src/main/resources/templates/home.html");
            if (Files.exists(home)) {
                Matcher t = Pattern.compile("(?is)<title>(.*?)</title>").matcher(Files.readString(home, StandardCharsets.UTF_8));
                if (t.find()) {
                    String title = t.group(1).replaceAll("<[^>]+>", "").trim();
                    int cut = title.indexOf(" - ");
                    if (cut < 0) {
                        cut = title.indexOf(" | ");
                    }
                    if (cut < 0) {
                        cut = title.indexOf(" \u2014 ");
                    }
                    title = cut > 0 ? title.substring(0, cut).trim() : title;
                    return title.length() > 60 ? "" : title;
                }
            }
        } catch (Exception ignored) {
            // the brand is only a nicety in the answer
        }
        return "";
    }

    /** True when the generated site has its own on-page chatbot agent. */
    public boolean siteHasAgent() {
        return Files.exists(outputDir.resolve("SITE_AGENT.json"));
    }

    // ------------------------------------------------------------------ how BOT words things

    /** Friendly present-tense label of a bot while it works ("Writing the user stories"). */
    public static String activeLabel(String botName) {
        return switch (kind(botName)) {
            case "analyst" -> "Writing the user stories";
            case "developer" -> "Building the website and its code";
            case "unit" -> "Writing the unit tests";
            case "functional" -> "Writing the functional tests";
            case "trainer" -> "Saving what I learned";
            case "editor" -> "Applying your change";
            default -> cleanName(botName);
        };
    }

    /** Friendly past-tense label of a finished bot ("Wrote the user stories"). */
    public static String doneLabel(String botName) {
        return switch (kind(botName)) {
            case "analyst" -> "Wrote the user stories";
            case "developer" -> "Built the website and its code";
            case "unit" -> "Wrote the unit tests";
            case "functional" -> "Wrote the functional tests";
            case "trainer" -> "Saved what I learned";
            case "editor" -> "Applied your change";
            default -> cleanName(botName);
        };
    }

    private static String kind(String botName) {
        String n = botName == null ? "" : botName.toLowerCase(Locale.ROOT);
        if (n.contains("business analyst")) {
            return "analyst";
        }
        if (n.contains("java developer")) {
            return "developer";
        }
        if (n.contains("unit")) {
            return "unit";
        }
        if (n.contains("functional")) {
            return "functional";
        }
        if (n.contains("developer tester") || n.contains("trainer")) {
            return "trainer";
        }
        if (n.contains("site editor")) {
            return "editor";
        }
        return "other";
    }

    private static String cleanName(String botName) {
        return botName == null ? "Working" : botName.replaceFirst("^Bot \\d+\\s*-\\s*", "");
    }

    /** What the user reads after a build: a short, friendly summary with links, not the pipeline's own log. */
    public static String narrateBuild(PipelineResponse r, String brand, boolean refined, boolean withAgent) {
        String site = brand == null || brand.isBlank() ? "website" : "**" + brand.trim() + "** website";
        StringBuilder sb = new StringBuilder();
        sb.append(refined
                ? "Done \u2014 I've rebuilt your " + site + " with your changes.\n\n"
                : "All done \u2014 your " + site + " is ready.\n\n");
        sb.append("Here's what I did:\n");
        for (BotStepLog step : r.getSteps()) {
            if (!step.isSuccess()) {
                continue;
            }
            sb.append("- ").append(doneLabel(step.getBotName()));
            if ("developer".equals(kind(step.getBotName())) && step.getGeneratedFiles() != null
                    && step.getGeneratedFiles().size() > 1) {
                sb.append(" (").append(step.getGeneratedFiles().size()).append(" files)");
            }
            sb.append('\n');
        }
        if (withAgent) {
            sb.append("- Added an on-page chatbot agent that answers your visitors' questions\n");
        }
        String preview = r.getPreviewUrl() == null ? "/site/home" : r.getPreviewUrl();
        sb.append("\n**Take a look**\n")
                .append("- [Open the website](").append(preview).append(")\n")
                .append("- [User stories + code](").append(r.getCodeUrl() == null ? "/code" : r.getCodeUrl()).append(")\n")
                .append("- [Edit the site](/edit)\n\n");
        String examples = withAgent
                ? "*\"make the hero shorter\"* or *\"change the colours to blue\"*"
                : "*\"make the hero shorter\"*, *\"add a chatbot agent\"* or *\"change the colours to blue\"*";
        sb.append("Want something different? Just tell me \u2014 for example ").append(examples).append('.');
        return sb.toString();
    }

    /** After a quick style edit of the existing site. */
    public static String narrateEdit(PipelineResponse r) {
        String preview = r.getPreviewUrl() == null ? "/site/home" : r.getPreviewUrl();
        return "Done \u2014 I've applied that to your website.\n\n"
                + "- [Open the website](" + preview + ")\n"
                + "- [Edit the site](/edit)\n\n"
                + "Anything else? You can keep refining it by chatting with me.";
    }

    /** A conversion request: the code itself is shown as a card below this text. */
    public static String narrateConversion(ConversionResponse c) {
        if (!c.success()) {
            return "I couldn't convert that: " + (c.message() == null ? "something went wrong" : c.message())
                    + "\n\nYou can try again with the code pasted right after the request, or use the [code converter](/convert).";
        }
        String route = (c.sourceLanguage() == null ? "Code" : c.sourceLanguage())
                + " \u2192 " + (c.targetLanguage() == null ? "the new language" : c.targetLanguage());
        return "Here's your **" + route + "** conversion"
                + (c.message() == null || c.message().isBlank() ? "." : " \u2014 " + c.message().strip())
                + "\n\nCopy or download it below. For a whole project, the [code converter](/convert) keeps every folder together.";
    }

    /** Instruction to the vision model: describe the look so the site builder can match it. */
    public static final String VISION_SYSTEM = "You are a senior web designer reading a reference picture for a website "
            + "builder. Reply in plain English only.";

    public static String visionPrompt(String userMessage) {
        return "The user asked: \"" + (userMessage == null ? "" : userMessage.strip())
                + "\"\nDescribe the attached reference image(s) as a design brief for the website: overall layout and "
                + "sections, colour palette (name the main colours and give approximate hex codes), typography style, "
                + "imagery and mood. Be concrete and stay under 180 words. Do not suggest chatbots, assistants or agents.";
    }

    /** Text added to the brief when the vision model has described the pictures. */
    public static String visualReference(String description) {
        return "\n\nVisual reference (from the image(s) the user attached; match this look):\n" + description.strip();
    }

    /** Text added to the brief when the pictures cannot be looked at: they become site assets instead. */
    public static String attachedAssets(List<String> urls) {
        return "\n\nThe user attached image(s), saved as site assets: " + String.join(", ", urls);
    }

    /** Tells the user what happened to their pictures, in plain words. */
    public static String imageNote(boolean looked, boolean placed, List<String> urls, List<String> problems) {
        StringBuilder sb = new StringBuilder();
        if (urls.isEmpty()) {
            sb.append("\n\nI couldn't use your image:");
            for (String p : problems) {
                sb.append("\n- ").append(p);
            }
            return sb.toString();
        }
        if (looked) {
            sb.append("\n\n**Your image").append(urls.size() == 1 ? "" : "s")
                    .append(":** I looked at ").append(urls.size() == 1 ? "it" : "them")
                    .append(" and described the look (layout, colours, mood) in the brief, so the site follows it.");
        } else if (placed) {
            sb.append("\n\n**Your image").append(urls.size() == 1 ? "" : "s")
                    .append(":** the connected model can't look at pictures, so I saved ")
                    .append(urls.size() == 1 ? "it" : "them")
                    .append(" as site assets and used ").append(urls.size() == 1 ? "it" : "them")
                    .append(" as the hero and card images.");
        } else {
            sb.append("\n\n**Your image").append(urls.size() == 1 ? "" : "s")
                    .append(":** I saved ").append(urls.size() == 1 ? "it" : "them").append(" as site assets.");
        }
        for (String u : urls) {
            sb.append("\n- [").append(u).append("](").append(u).append(")");
        }
        for (String p : problems) {
            sb.append("\n- Skipped ").append(p);
        }
        return sb.toString();
    }

    /** A run that failed part-way: say what happened in plain words and how to continue. */
    public static String narrateFailure(PipelineResponse r) {
        String failed = null;
        for (BotStepLog step : r.getSteps()) {
            if (!step.isSuccess()) {
                failed = step.getBotName();
                break;
            }
        }
        StringBuilder sb = new StringBuilder("I ran into a problem");
        if (failed != null) {
            sb.append(" while ").append(activeLabel(failed).toLowerCase(Locale.ROOT));
        }
        sb.append('.');
        String reason = r.getMessage();
        if (reason != null && !reason.isBlank()) {
            sb.append(" ").append(reason.strip());
        }
        sb.append(r.isResumable()
                ? "\n\nNothing is lost \u2014 press **Resume** and I'll carry on from the last step that finished."
                : "\n\nTry rewording your request, and I'll have another go.");
        return sb.toString();
    }

    /** The user pressed Stop. */
    public static String narrateStopped(PipelineResponse r) {
        int done = r.getCompletedSteps();
        return "Okay, I've stopped" + (done > 0 ? " after finishing " + done + (done == 1 ? " step" : " steps") : "")
                + ". Press **Resume** whenever you want me to continue where I left off.";
    }

    /** System prompt of a plain conversation turn: ChatGPT-like, grounded in what BOT can really do. */
    public String chatSystemPrompt(String userSystemPrompt) {
        String brand = siteBrand();
        StringBuilder sb = new StringBuilder("""
                You are BOT, a friendly assistant that works like ChatGPT inside a tool that turns a plain-English \
                description into a working website. Behind the scenes a Business Analyst writes user stories, a Developer \
                writes the site and its code, Testers write unit and functional tests, and a Trainer saves what was learned. \
                You can also convert code between programming languages (/convert) and run each stage on its own (/stages).
                Rules: answer in a few short, plain sentences, in the user's language; use light markdown (bold, short \
                lists, `code`) when it helps; never mention internal bot names or pipeline jargon unless asked; \
                never invent features or links you were not told about. If the user wants something built or changed, \
                tell them they can simply describe it (for example "build a bakery website called Crumb Hearth with a \
                chatbot agent, backend language Python") and you will build it.
                Useful links: [User stories + code](/code), [Edit the site](/edit), [Code converter](/convert), \
                [5-stage pipeline](/stages).
                """);
        if (Files.exists(outputDir.resolve("SITE_META.json"))) {
            sb.append("A website has already been built in this conversation")
                    .append(brand.isBlank() ? "" : " for \"" + brand + "\"")
                    .append(": it is at [/site/home](/site/home).\n");
        } else {
            sb.append("No website has been built yet in this conversation.\n");
        }
        if (userSystemPrompt != null && !userSystemPrompt.isBlank()) {
            sb.append("The user's own standing instructions: ").append(userSystemPrompt.strip(), 0,
                    Math.min(userSystemPrompt.strip().length(), 800)).append('\n');
        }
        return sb.toString();
    }

    /** What BOT says to a plain message when no language model is connected. */
    public String offlineChatReply(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean site = Files.exists(outputDir.resolve("SITE_META.json"));
        if (m.matches("(?s).*\\b(thanks|thank you|thx)\\b.*")) {
            return "You're welcome! Tell me if you'd like anything else changed" + (site ? " on the site." : ".");
        }
        if (site && m.matches("(?s).*\\b(where|link|open|see|view|show)\\b.*")) {
            return "Your website is at [/site/home](/site/home), and the user stories and code are in "
                    + "[User stories + code](/code). You can also [edit the site](/edit).";
        }
        return "Hi! I'm **BOT**. Describe a website, app or code conversion and I'll build it \u2014 for example "
                + "*\"build a bakery website called Crumb Hearth with a chatbot agent, backend language Python\"*.\n\n"
                + (site ? "Your current site is at [/site/home](/site/home); tell me what to change and I'll update it.\n\n" : "")
                + "I'm running without a connected language model right now, so I can build and convert, "
                + "but open-ended chat is limited.";
    }

    /** Splits an answer into small pieces so it can be streamed word by word. */
    public static List<String> pieces(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\\S+\\s*").matcher(text == null ? "" : text);
        StringBuilder cur = new StringBuilder();
        int words = 0;
        while (m.find()) {
            cur.append(m.group());
            words++;
            if (words >= 3 || m.group().contains("\n")) {
                out.add(cur.toString());
                cur.setLength(0);
                words = 0;
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
