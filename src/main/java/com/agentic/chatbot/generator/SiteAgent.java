package com.agentic.chatbot.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The on-page chatbot agent for generated websites.
 *
 * <p>Only added when the prompt asks for a chatbot / agent / assistant. The widget talks to BOT's
 * {@code /api/site-agent} endpoint (LLM-backed when a model is connected, rule-based otherwise) and falls back to
 * the same rules in the browser, so the exported site still answers when it runs on its own.
 */
public final class SiteAgent {

    /** What the agent knows about the business. Written to {@code SITE_AGENT.json} next to the site. */
    public record Knowledge(
            String brand,
            String agentName,
            String kind,
            String lead,
            List<String> titles,
            List<String> blurbs,
            List<String> links
    ) {
    }

    public record Action(String label, String href) {
    }

    public record Reply(String text, List<Action> actions, List<String> suggestions) {
    }

    private static final Pattern NEGATED = Pattern.compile(
            "(?i)\\b(?:no|without|skip|don'?t (?:add|need|want)|do not (?:add|need|want))\\s+(?:a\\s+|an\\s+|the\\s+)?"
                    + "(?:chat\\s?bot|agent|assistant|chat widget)");
    private static final Pattern NOT_AN_AGENT = Pattern.compile(
            "(?i)\\b(?:travel|real[- ]estate|estate|insurance|booking|sales|talent|ticket|shipping|user|support|customer service|"
                    + "teaching|executive|personal|administrative|admin)\\s+(?:agent|assistant)s?\\b");
    private static final Pattern WANTS = Pattern.compile(
            "(?i)\\b(?:chat\\s?bot|chat\\s?bots|chat widget|live chat|chat assistant|agentic|agent|ai assistant|"
                    + "virtual assistant|virtual guide|concierge|assistant)\\b");

    private SiteAgent() {
    }

    /** True when the prompt asks for a chatbot / agent on the site. */
    public static boolean wantsAgent(String story) {
        if (story == null || story.isBlank()) {
            return false;
        }
        if (NEGATED.matcher(story).find()) {
            return false;
        }
        String cleaned = NOT_AN_AGENT.matcher(story).replaceAll(" ");
        return WANTS.matcher(cleaned).find();
    }

    static String roleFor(SiteThemePack.Kind kind) {
        return switch (kind) {
            case RESTAURANT -> "Host";
            case TRAVEL -> "Trip Planner";
            case MUSIC -> "Practice Buddy";
            case WEDDING -> "Planner";
            case PETS -> "Pet Concierge";
            case BOOKS -> "Bookseller";
            case GARDEN -> "Garden Guide";
            case GAMING -> "Squad Bot";
            case SPACE -> "Star Guide";
            case CLEANING -> "Booking Helper";
            case HOSPITAL -> "Care Navigator";
            case FITNESS -> "Coach";
            case BANK -> "Money Guide";
            case TECH -> "Assistant";
        };
    }

    static Knowledge knowledge(String brand, SiteThemePack.Theme theme, String lead) {
        return new Knowledge(
                brand,
                brand + " " + roleFor(theme.kind()),
                theme.kind().name().toLowerCase(Locale.ROOT),
                lead == null || lead.isBlank() ? theme.homeLead() : lead,
                List.of(theme.serviceTitles()),
                List.of(theme.serviceBlurbs()),
                List.of(theme.serviceLinks()));
    }

    // ------------------------------------------------------------------ rules

    /** Rule-based answer: always available, no model needed. */
    public static Reply reply(Knowledge k, String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT).trim();
        List<String> suggestions = suggestions(k);
        if (m.isEmpty()) {
            return new Reply("Ask me anything about " + k.brand() + ".", List.of(), suggestions);
        }
        if (m.matches("(?s).*\\b(hi|hello|hey|good (morning|evening|afternoon))\\b.*") && m.length() < 40) {
            return new Reply("Hi! I'm the " + k.agentName() + ". I can walk you through " + k.brand()
                    + ", find the right page, or point you to the team. What would you like to know?",
                    List.of(), suggestions);
        }
        if (m.matches("(?s).*\\b(thanks|thank you|cheers)\\b.*")) {
            return new Reply("You're welcome! Anything else about " + k.brand() + "?", List.of(), suggestions);
        }
        // a service/card the visitor named
        for (int i = 0; i < k.titles().size(); i++) {
            if (mentions(m, k.titles().get(i))) {
                String blurb = i < k.blurbs().size() ? k.blurbs().get(i) : k.lead();
                String link = i < k.links().size() ? k.links().get(i) : "/site/services";
                return new Reply(k.titles().get(i) + " at " + k.brand() + ": " + blurb,
                        List.of(new Action("Open " + k.titles().get(i), link), new Action("Contact us", "/site/contact")),
                        suggestions);
            }
        }
        if (has(m, "price", "cost", "plan", "pricing", "how much", "fee", "rate")) {
            return new Reply("Plans at " + k.brand() + " are simple and transparent. The pricing page lists every tier, "
                    + "and the team can tailor one to you.",
                    List.of(new Action("See pricing", "/site/pricing"), new Action("Talk to the team", "/site/contact")),
                    suggestions);
        }
        if (has(m, "contact", "reach", "email", "phone", "talk", "human", "book", "reserve", "reservation",
                "appointment", "visit", "hours", "open", "enquir", "inquir", "quote")) {
            return new Reply("The quickest way to reach " + k.brand()
                    + " is the contact form \u2014 leave your details and someone replies within one business day.",
                    List.of(new Action("Open contact form", "/site/contact")), suggestions);
        }
        if (has(m, "career", "job", "hiring", "work with", "vacanc", "apply")) {
            return new Reply(k.brand() + " is growing. Browse the open roles and apply through the contact form.",
                    List.of(new Action("See careers", "/site/careers")), suggestions);
        }
        if (has(m, "blog", "news", "article", "update")) {
            return new Reply("The " + k.brand() + " blog has the latest stories and behind-the-scenes updates.",
                    List.of(new Action("Read the blog", "/site/blog")), suggestions);
        }
        if (has(m, "about", "who are", "who is", "story", "mission", "what is", "what do you")) {
            return new Reply(k.lead(), List.of(new Action("About " + k.brand(), "/site/about")), suggestions);
        }
        if (has(m, "service", "offer", "help", "what can", "menu", "program", "package", "class")) {
            return new Reply(k.brand() + " offers: " + String.join(", ", k.titles()) + ". Pick one and I'll tell you more.",
                    List.of(new Action("Browse everything", "/site/services")), suggestions);
        }
        return new Reply("I'm the " + k.agentName() + ". I can tell you about " + String.join(", ", k.titles())
                + ", pricing, careers or how to get in touch. What are you curious about?",
                List.of(new Action("Contact " + k.brand(), "/site/contact")), suggestions);
    }

    static List<String> suggestions(Knowledge k) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(3, k.titles().size()); i++) {
            out.add(k.titles().get(i));
        }
        out.add("Pricing");
        out.add("Contact");
        return out;
    }

    private static boolean has(String m, String... words) {
        for (String w : words) {
            if (m.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentions(String message, String title) {
        String t = title.toLowerCase(Locale.ROOT).replace("&", " ");
        if (message.contains(t)) {
            return true;
        }
        for (String word : t.split("[^a-z0-9]+")) {
            if (word.length() >= 4 && message.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** System prompt for an LLM-backed answer, grounded in the site's own content. */
    public static String systemPrompt(Knowledge k) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the ").append(k.agentName()).append(", the on-page chat agent for the website of ")
                .append(k.brand()).append(" (a ").append(k.kind()).append(" business).\n");
        sb.append("Business summary: ").append(k.lead()).append('\n');
        sb.append("Offerings:\n");
        for (int i = 0; i < k.titles().size(); i++) {
            sb.append("- ").append(k.titles().get(i));
            if (i < k.blurbs().size()) {
                sb.append(": ").append(k.blurbs().get(i));
            }
            sb.append('\n');
        }
        sb.append("Pages: /site/home, /site/about, /site/services, /site/pricing, /site/blog, /site/careers, /site/contact.\n");
        sb.append("Answer in at most three short sentences, in a friendly voice, only about this business. ")
                .append("If you do not know, say so and point to /site/contact. Never invent prices or opening hours.");
        return sb.toString();
    }

    // ------------------------------------------------------------------ json

    public static String toJson(Knowledge k) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"brand\":").append(q(k.brand()));
        sb.append(",\"agentName\":").append(q(k.agentName()));
        sb.append(",\"kind\":").append(q(k.kind()));
        sb.append(",\"lead\":").append(q(k.lead()));
        sb.append(",\"services\":[");
        for (int i = 0; i < k.titles().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"title\":").append(q(k.titles().get(i)))
                    .append(",\"blurb\":").append(q(i < k.blurbs().size() ? k.blurbs().get(i) : ""))
                    .append(",\"link\":").append(q(i < k.links().size() ? k.links().get(i) : "/site/services"))
                    .append('}');
        }
        return sb.append("]}").toString();
    }

    private static String q(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<' -> sb.append("\\u003c");
                case '>' -> sb.append("\\u003e");
                case '&' -> sb.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ----------------------------------------------------------------- widget

    /** The floating chat widget (HTML + CSS + JS) appended to every page of the site. */
    static String widget(Knowledge k, int radius) {
        return WIDGET
                .replace("@@KNOWLEDGE@@", toJson(k))
                .replace("@@RADIUS@@", String.valueOf(radius))
                .replace("@@AGENT@@", esc(k.agentName()))
                .replace("@@BRAND@@", esc(k.brand()));
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static final String WIDGET = """
            <!-- BOT site agent: on-page chatbot -->
            <style>
              #sa-fab { position:fixed; right:20px; bottom:20px; z-index:9998; width:58px; height:58px; border:0; cursor:pointer;
                border-radius:50%; background:var(--accent,#2563eb); color:#fff; box-shadow:0 10px 30px rgba(15,23,42,.3); display:grid; place-items:center; }
              #sa-fab:hover { transform:translateY(-2px); }
              #sa-panel { position:fixed; right:20px; bottom:90px; z-index:9999; width:min(360px, calc(100vw - 32px)); height:min(520px, 70vh);
                display:none; flex-direction:column; background:#fff; color:#0f172a; border:1px solid rgba(15,23,42,.12);
                border-radius:@@RADIUS@@px; box-shadow:0 24px 60px rgba(15,23,42,.28); overflow:hidden; font:15px/1.45 system-ui, Arial, sans-serif; }
              #sa-panel.open { display:flex; }
              #sa-head { background:var(--accent,#2563eb); color:#fff; padding:.8rem 1rem; display:flex; align-items:center; justify-content:space-between; gap:.5rem; }
              #sa-head strong { display:block; font-size:1rem; }
              #sa-head small { opacity:.85; font-size:.78rem; }
              #sa-close { background:transparent; border:0; color:#fff; font-size:1.3rem; cursor:pointer; line-height:1; }
              #sa-log { flex:1; overflow:auto; padding:.9rem; display:flex; flex-direction:column; gap:.55rem; background:#f8fafc; }
              .sa-msg { max-width:86%; padding:.55rem .8rem; border-radius:14px; white-space:pre-wrap; word-wrap:break-word; }
              .sa-bot { background:#fff; border:1px solid #e2e8f0; align-self:flex-start; }
              .sa-me { background:var(--accent,#2563eb); color:#fff; align-self:flex-end; }
              .sa-actions, #sa-chips { display:flex; flex-wrap:wrap; gap:.35rem; margin-top:.4rem; }
              .sa-actions a, #sa-chips button { font:inherit; font-size:.82rem; padding:.3rem .65rem; border-radius:999px; cursor:pointer;
                border:1px solid var(--accent,#2563eb); color:var(--accent,#2563eb); background:#fff; text-decoration:none; }
              #sa-chips { padding:.5rem .9rem 0; margin:0; background:#f8fafc; }
              #sa-form { display:flex; gap:.4rem; padding:.7rem; border-top:1px solid #e2e8f0; background:#fff; }
              #sa-input { flex:1; padding:.6rem .8rem; border:1px solid #cbd5e1; border-radius:999px; font:inherit; }
              #sa-send { border:0; border-radius:999px; padding:0 1rem; background:var(--accent,#2563eb); color:#fff; font-weight:600; cursor:pointer; }
              #sa-note { font-size:.7rem; color:#64748b; text-align:center; padding:0 .5rem .45rem; background:#fff; }
            </style>
            <button id="sa-fab" type="button" aria-label="Chat with @@AGENT@@" aria-expanded="false">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M4 3h16a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H9l-5 4v-4H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/></svg>
            </button>
            <section id="sa-panel" role="dialog" aria-label="@@AGENT@@">
              <div id="sa-head">
                <div><strong>@@AGENT@@</strong><small>AI agent for @@BRAND@@ &middot; online</small></div>
                <button id="sa-close" type="button" aria-label="Close chat">&times;</button>
              </div>
              <div id="sa-log" aria-live="polite"></div>
              <div id="sa-chips"></div>
              <form id="sa-form" autocomplete="off">
                <input id="sa-input" type="text" maxlength="400" placeholder="Ask about @@BRAND@@..." aria-label="Your message"/>
                <button id="sa-send" type="submit">Send</button>
              </form>
              <div id="sa-note">Demo agent generated by BOT</div>
            </section>
            <script>
            (function () {
              var K = @@KNOWLEDGE@@;
              var fab = document.getElementById('sa-fab');
              var panel = document.getElementById('sa-panel');
              var log = document.getElementById('sa-log');
              var chips = document.getElementById('sa-chips');
              var form = document.getElementById('sa-form');
              var input = document.getElementById('sa-input');
              var greeted = false;

              function has(m, words) { for (var i = 0; i < words.length; i++) { if (m.indexOf(words[i]) >= 0) return true; } return false; }
              function suggestions() {
                var s = K.services.slice(0, 3).map(function (x) { return x.title; });
                s.push('Pricing'); s.push('Contact');
                return s;
              }
              function mentions(m, title) {
                var t = title.toLowerCase().replace('&', ' ');
                if (m.indexOf(t) >= 0) return true;
                var parts = t.split(/[^a-z0-9]+/);
                for (var i = 0; i < parts.length; i++) { if (parts[i].length >= 4 && m.indexOf(parts[i]) >= 0) return true; }
                return false;
              }
              // same rules as the server, so the site still answers when it runs on its own
              function localReply(text) {
                var m = (text || '').toLowerCase();
                var sug = suggestions();
                if (/\\b(hi|hello|hey)\\b/.test(m) && m.length < 40) {
                  return { reply: "Hi! I'm the " + K.agentName + ". I can walk you through " + K.brand + ", find the right page, or point you to the team.", actions: [], suggestions: sug };
                }
                for (var i = 0; i < K.services.length; i++) {
                  if (mentions(m, K.services[i].title)) {
                    return { reply: K.services[i].title + " at " + K.brand + ": " + K.services[i].blurb,
                      actions: [{ label: 'Open ' + K.services[i].title, href: K.services[i].link }, { label: 'Contact us', href: '/site/contact' }], suggestions: sug };
                  }
                }
                if (has(m, ['price', 'cost', 'plan', 'how much', 'fee'])) return { reply: 'Plans at ' + K.brand + ' are simple and transparent - see every tier on the pricing page.', actions: [{ label: 'See pricing', href: '/site/pricing' }], suggestions: sug };
                if (has(m, ['contact', 'reach', 'email', 'phone', 'talk', 'book', 'reserve', 'appointment', 'visit', 'hours', 'quote'])) return { reply: 'The quickest way to reach ' + K.brand + ' is the contact form - someone replies within a business day.', actions: [{ label: 'Open contact form', href: '/site/contact' }], suggestions: sug };
                if (has(m, ['career', 'job', 'hiring', 'apply'])) return { reply: K.brand + ' is growing - browse the open roles.', actions: [{ label: 'See careers', href: '/site/careers' }], suggestions: sug };
                if (has(m, ['about', 'who are', 'story', 'mission'])) return { reply: K.lead, actions: [{ label: 'About ' + K.brand, href: '/site/about' }], suggestions: sug };
                var names = K.services.map(function (x) { return x.title; }).join(', ');
                return { reply: "I'm the " + K.agentName + '. I can tell you about ' + names + ', pricing, careers or how to get in touch.', actions: [{ label: 'Contact ' + K.brand, href: '/site/contact' }], suggestions: sug };
              }

              function addMsg(kind, text, actions) {
                var d = document.createElement('div');
                d.className = 'sa-msg ' + (kind === 'me' ? 'sa-me' : 'sa-bot');
                d.textContent = text;
                if (actions && actions.length) {
                  var box = document.createElement('div');
                  box.className = 'sa-actions';
                  actions.forEach(function (a) {
                    var l = document.createElement('a');
                    l.href = a.href; l.textContent = a.label;
                    box.appendChild(l);
                  });
                  d.appendChild(box);
                }
                log.appendChild(d);
                log.scrollTop = log.scrollHeight;
              }
              function setChips(list) {
                chips.innerHTML = '';
                (list || []).forEach(function (s) {
                  var b = document.createElement('button');
                  b.type = 'button'; b.textContent = s;
                  b.addEventListener('click', function () { send(s); });
                  chips.appendChild(b);
                });
              }
              function show(d) {
                addMsg('bot', d.reply, d.actions);
                setChips(d.suggestions);
              }
              function send(text) {
                text = (text || '').trim();
                if (!text) return;
                addMsg('me', text);
                input.value = '';
                var done = false;
                var timer = setTimeout(function () { if (!done) { done = true; show(localReply(text)); } }, 12000);
                fetch('/api/site-agent', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ message: text, page: location.pathname })
                }).then(function (r) { if (!r.ok) throw new Error('agent ' + r.status); return r.json(); })
                  .then(function (d) { if (!done) { done = true; clearTimeout(timer); show(d); } })
                  .catch(function () { if (!done) { done = true; clearTimeout(timer); show(localReply(text)); } });
              }
              function open(v) {
                panel.classList.toggle('open', v);
                fab.setAttribute('aria-expanded', v ? 'true' : 'false');
                if (v && !greeted) {
                  greeted = true;
                  show({ reply: "Hi! I'm the " + K.agentName + ". Ask me about " + K.brand + " - services, pricing, or how to get in touch.", actions: [], suggestions: suggestions() });
                }
                if (v) input.focus();
              }
              fab.addEventListener('click', function () { open(!panel.classList.contains('open')); });
              document.getElementById('sa-close').addEventListener('click', function () { open(false); });
              form.addEventListener('submit', function (e) { e.preventDefault(); send(input.value); });
            })();
            </script>
            """;
}
