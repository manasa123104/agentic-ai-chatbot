package com.agentic.chatbot.generator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a multi-page website with theme images, clickable cards, and story-driven colors/fonts.
 */
final class BigSiteTemplates {

    private BigSiteTemplates() {
    }

    static Map<String, String> generate(String brand, String story) {
        String b = brand == null || brand.isBlank() ? "NovaTech" : brand;
        String s = story == null ? "" : story;
        SiteThemePack.Theme theme = SiteThemePack.fromStory(s);
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("home", page(b, s, theme, "home", "Home", homeBody(b, s, theme)));
        pages.put("about", page(b, s, theme, "about", "About", aboutBody(b, theme)));
        pages.put("services", page(b, s, theme, "services", "Services", servicesBody(b, theme)));
        pages.put("pricing", page(b, s, theme, "pricing", "Pricing", pricingBody(theme)));
        pages.put("blog", page(b, s, theme, "blog", "Blog", blogBody(b, theme)));
        pages.put("careers", page(b, s, theme, "careers", "Careers", careersBody(b, theme)));
        pages.put("contact", page(b, s, theme, "contact", "Contact", contactBody(b, theme)));
        return pages;
    }

    /** Expose theme so generator can write SITE_STYLE.json. */
    static SiteThemePack.Theme themeFor(String story) {
        return SiteThemePack.fromStory(story);
    }

    private static String page(String brand, String story, SiteThemePack.Theme theme, String current, String title, String body) {
        String fontLink = theme.googleFontsHref();
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>%s · %s</title>
                  <link href="%s" rel="stylesheet"/>
                  <style>
                    :root {
                      --bg:%s; --ink:%s; --muted:%s; --line:#e2e8f0;
                      --accent:%s; --soft:%s; --dark:%s;
                    }
                    * { box-sizing:border-box; }
                    body { margin:0; font-family:%s; color:var(--ink); background:var(--bg); }
                    a { color:inherit; text-decoration:none; }
                    .top {
                      position:sticky; top:0; z-index:20; backdrop-filter:blur(10px);
                      background:rgba(255,255,255,.92); border-bottom:1px solid var(--line);
                      display:flex; align-items:center; justify-content:space-between; gap:1rem;
                      padding:.85rem 5vw;
                    }
                    .brand { display:flex; align-items:center; gap:.65rem; font-weight:800; letter-spacing:-.03em; }
                    .mark {
                      width:34px; height:34px; border-radius:10px;
                      background:linear-gradient(135deg,var(--accent),var(--soft));
                      background-size:cover; background-position:center;
                    }
                    .nav { display:flex; flex-wrap:wrap; gap:.2rem 1rem; }
                    .nav a { color:var(--muted); font-size:.92rem; font-weight:500; padding:.35rem 0; }
                    .nav a:hover, .nav a.active { color:var(--accent); }
                    .cta {
                      background:var(--accent); color:#fff !important; padding:.55rem 1rem; border-radius:999px;
                      font-size:.88rem; font-weight:600;
                    }
                    .hero {
                      display:grid; grid-template-columns:1.05fr .95fr; gap:2.5rem; align-items:center;
                      padding:4rem 5vw 3rem; background:linear-gradient(180deg,var(--soft),var(--bg) 75%%);
                    }
                    .hero h1 { margin:0 0 1rem; font-size:clamp(2.1rem,5vw,3.4rem); line-height:1.08; letter-spacing:-.03em; }
                    .hero p { margin:0 0 1.5rem; color:var(--muted); font-size:1.05rem; line-height:1.65; max-width:36rem; }
                    .actions { display:flex; gap:.75rem; flex-wrap:wrap; }
                    .btn {
                      display:inline-flex; align-items:center; justify-content:center; border-radius:999px;
                      padding:.85rem 1.25rem; font-weight:700; font-size:.95rem;
                    }
                    .btn-primary { background:var(--accent); color:#fff; }
                    .btn-ghost { background:#fff; color:var(--accent); border:1px solid color-mix(in srgb, var(--accent) 35%%, #fff); }
                    .visual {
                      min-height:340px; border-radius:28px; overflow:hidden;
                      background:#ddd center/cover no-repeat;
                      box-shadow:0 25px 60px color-mix(in srgb, var(--accent) 25%%, transparent);
                    }
                    .visual img { width:100%%; height:100%%; min-height:340px; object-fit:cover; display:block; }
                    .section { padding:3.5rem 5vw; }
                    .section.alt { background:color-mix(in srgb, var(--soft) 55%%, #fff); }
                    .section h2 { margin:0 0 .5rem; font-size:clamp(1.5rem,3vw,2rem); letter-spacing:-.02em; }
                    .section .lead { color:var(--muted); margin:0 0 1.75rem; max-width:40rem; line-height:1.6; }
                    .grid-3 { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:1rem; }
                    .grid-2 { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:1rem; }
                    a.card, article.card, form.card {
                      background:#fff; border:1px solid var(--line); border-radius:18px; padding:0;
                      box-shadow:0 8px 24px rgba(15,23,42,.04); overflow:hidden;
                      transition:transform .18s ease, box-shadow .18s ease, border-color .18s ease;
                      display:block; color:inherit;
                    }
                    a.card { cursor:pointer; }
                    a.card:hover {
                      transform:translateY(-3px); border-color:var(--accent);
                      box-shadow:0 14px 32px color-mix(in srgb, var(--accent) 18%%, transparent);
                    }
                    .card-media {
                      width:100%%; height:180px; overflow:hidden; background:#e2e8f0; position:relative;
                    }
                    .card-media img.card-img, img.card-img {
                      width:100%%; height:180px; object-fit:cover; object-position:center;
                      display:block; border:0;
                    }
                    .card-body { padding:1.15rem 1.25rem 1.3rem; }
                    form.card { padding:1.25rem; }
                    .card h3 { margin:0 0 .45rem; font-size:1.05rem; }
                    .card p { margin:0; color:var(--muted); line-height:1.55; font-size:.94rem; }
                    .card .go { display:inline-block; margin-top:.75rem; color:var(--accent); font-weight:600; font-size:.88rem; }
                    .price { font-size:2rem; font-weight:800; margin:.5rem 0; }
                    .price span { font-size:.95rem; color:var(--muted); font-weight:500; }
                    .list { margin:.75rem 0 0; padding-left:1.1rem; color:var(--muted); }
                    .list li { margin:.35rem 0; }
                    form label { display:block; margin:.8rem 0 .3rem; font-size:.85rem; color:var(--muted); font-weight:600; }
                    form input, form textarea, form select {
                      width:100%%; padding:.75rem .9rem; border:1px solid var(--line); border-radius:10px; font:inherit;
                    }
                    form button {
                      margin-top:1rem; border:0; background:var(--accent); color:#fff; border-radius:999px;
                      padding:.8rem 1.3rem; font-weight:700; cursor:pointer;
                    }
                    .site-footer {
                      background:var(--dark); color:#94a3b8; padding:3rem 5vw 2rem;
                      display:grid; grid-template-columns:1.4fr 1fr 1fr 1fr; gap:1.5rem;
                    }
                    .site-footer strong { color:#fff; display:block; margin-bottom:.7rem; }
                    .site-footer a { display:block; color:#94a3b8; margin:.35rem 0; font-size:.9rem; }
                    .site-footer a:hover { color:#fff; }
                    .copy { grid-column:1/-1; border-top:1px solid #1e293b; margin-top:1rem; padding-top:1rem; font-size:.85rem; }
                    @media (max-width:900px) {
                      .hero, .grid-3, .grid-2, .site-footer { grid-template-columns:1fr; }
                      .visual, .visual img { min-height:220px; }
                      .nav { display:none; }
                    }
                  </style>
                </head>
                <body>
                <header class="top">
                  <a class="brand" href="/site/home"><span class="mark" style="background-image:url('%s')"></span>%s</a>
                  <nav class="nav">
                    <a href="/site/home" class="%s">Home</a>
                    <a href="/site/about" class="%s">About</a>
                    <a href="/site/services" class="%s">Services</a>
                    <a href="/site/pricing" class="%s">Pricing</a>
                    <a href="/site/blog" class="%s">Blog</a>
                    <a href="/site/careers" class="%s">Careers</a>
                    <a href="/site/contact" class="%s">Contact</a>
                  </nav>
                  <a class="cta" href="/site/contact">Get started</a>
                </header>
                %s
                <footer class="site-footer">
                  <div>
                    <strong>%s</strong>
                    <span>%s — %s</span>
                  </div>
                  <div><strong>Explore</strong><a href="/site/services">Services</a><a href="/site/pricing">Pricing</a><a href="/site/blog">Blog</a></div>
                  <div><strong>Company</strong><a href="/site/about">About</a><a href="/site/careers">Careers</a><a href="/site/contact">Contact</a></div>
                  <div><strong>Tools</strong><a href="/site/home">Home</a><a href="/">New story</a><a href="/edit">Edit site</a></div>
                  <div class="copy">© 2026 %s · Privacy · Terms</div>
                </footer>
                </body>
                </html>
                """.formatted(
                brand, title,
                fontLink,
                theme.bg(), theme.ink(), theme.muted(), theme.accent(), theme.soft(), theme.dark(),
                theme.fontFamily(),
                escAttr(theme.heroImage()), brand,
                on(current, "home"), on(current, "about"), on(current, "services"), on(current, "pricing"),
                on(current, "blog"), on(current, "careers"), on(current, "contact"),
                body, brand, brand, theme.homeLead(), brand
        );
    }

    private static String on(String current, String name) {
        return current.equals(name) ? "active" : "";
    }

    private static String escAttr(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("\"", "&quot;");
    }

    private static String img(String[] imgs, int i) {
        if (imgs == null || imgs.length == 0) {
            return "";
        }
        return imgs[Math.floorMod(i, imgs.length)];
    }

    private static String card(String href, String image, String title, String blurb) {
        String src = (image == null || image.isBlank())
                ? "https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&fit=crop&w=800&q=80"
                : image;
        return """
                <a class="card" href="%s">
                  <div class="card-media">
                    <img class="card-img" src="%s" alt="%s" loading="lazy"
                         onerror="this.onerror=null;this.src='https://images.unsplash.com/photo-1497366216548-37526070297c?auto=format&amp;fit=crop&amp;w=800&amp;q=80'"/>
                  </div>
                  <div class="card-body">
                    <h3>%s</h3>
                    <p>%s</p>
                    <span class="go">Open page →</span>
                  </div>
                </a>
                """.formatted(href, escAttr(src), escAttr(title), title, blurb);
    }

    private static String homeBody(String brand, String story, SiteThemePack.Theme theme) {
        String[] imgs = theme.cardImages();
        return """
                <section class="hero">
                  <div>
                    <p style="margin:0 0 .5rem;color:var(--accent);font-weight:700;letter-spacing:.04em;text-transform:uppercase;font-size:.85rem">Welcome</p>
                    <h1>%s</h1>
                    <p>%s</p>
                    <div class="actions">
                      <a class="btn btn-primary" href="/site/about">About %s</a>
                      <a class="btn btn-ghost" href="/site/contact">Contact us</a>
                    </div>
                  </div>
                  <div class="visual"><img src="%s" alt="%s"/></div>
                </section>
                <section class="section">
                  <h2>About %s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-3">
                    %s
                    %s
                    %s
                  </div>
                </section>
                <section class="section alt">
                  <h2>Explore</h2>
                  <div class="grid-2">
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                brand,
                theme.homeLead(),
                brand,
                escAttr(theme.heroImage()), brand,
                brand,
                theme.homeLead(),
                card("/site/about", img(imgs, 0), "Our story", "Learn who we are and what " + brand + " stands for."),
                card("/site/services", img(imgs, 1), theme.servicesLabel(), theme.serviceBlurbs()[0]),
                card("/site/contact", img(imgs, 2), "Get in touch", "Reach the " + brand + " team anytime."),
                card("/site/about", img(imgs, 3), "About " + brand + " →", "Read more about our mission and values."),
                card("/site/pricing", img(imgs, 4), "See pricing →", "Simple plans from " + brand + ".")
        );
    }

    private static String aboutBody(String brand, SiteThemePack.Theme theme) {
        String[] imgs = theme.cardImages();
        return """
                <section class="section">
                  <h2>About %s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-3">
                    %s
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                brand,
                theme.homeLead(),
                card("/site/services", img(imgs, 0), "What we do", theme.serviceBlurbs()[0]),
                card("/site/careers", img(imgs, 1), "Our people", "The team behind " + brand + "."),
                card("/site/contact", img(imgs, 2), "Talk to us", "Questions about " + brand + "? We’re here to help.")
        );
    }

    private static String servicesBody(String brand, SiteThemePack.Theme theme) {
        String[] imgs = theme.cardImages();
        String[] titles = theme.serviceTitles();
        String[] blurbs = theme.serviceBlurbs();
        String[] links = theme.serviceLinks();
        StringBuilder cards = new StringBuilder();
        for (int i = 0; i < titles.length; i++) {
            cards.append(card(links[i], img(imgs, i), titles[i], blurbs[i]));
        }
        return """
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">Everything %s offers — click any box to open that page.</p>
                  <div class="grid-3">
                    %s
                  </div>
                </section>
                """.formatted(theme.servicesLabel(), brand, cards);
    }

    private static String pricingBody(SiteThemePack.Theme theme) {
        String[] imgs = theme.cardImages();
        return """
                <section class="section">
                  <h2>Pricing</h2>
                  <p class="lead">Simple plans. Click a plan box to continue to contact.</p>
                  <div class="grid-3">
                    <a class="card" href="/site/contact">
                      <div class="card-media"><img class="card-img" src="%s" alt="Starter" loading="lazy"/></div>
                      <div class="card-body">
                        <h3>Starter</h3>
                        <div class="price">$0 <span>/mo</span></div>
                        <ul class="list"><li>Core pages</li><li>Theme images</li><li>Contact form</li></ul>
                        <span class="go">Choose Starter →</span>
                      </div>
                    </a>
                    <a class="card" href="/site/contact" style="border-color:var(--accent)">
                      <div class="card-media"><img class="card-img" src="%s" alt="Growth" loading="lazy"/></div>
                      <div class="card-body">
                        <h3>Growth</h3>
                        <div class="price">$49 <span>/mo</span></div>
                        <ul class="list"><li>Unlimited stories</li><li>Custom colors & fonts</li><li>Priority support</li></ul>
                        <span class="go">Choose Growth →</span>
                      </div>
                    </a>
                    <a class="card" href="/site/contact">
                      <div class="card-media"><img class="card-img" src="%s" alt="Enterprise" loading="lazy"/></div>
                      <div class="card-body">
                        <h3>Enterprise</h3>
                        <div class="price">Custom</div>
                        <ul class="list"><li>Dedicated workspace</li><li>SLA + onboarding</li><li>Security review</li></ul>
                        <span class="go">Talk to sales →</span>
                      </div>
                    </a>
                  </div>
                </section>
                """.formatted(escAttr(img(imgs, 0)), escAttr(img(imgs, 1)), escAttr(img(imgs, 2)));
    }

    private static String blogBody(String brand, SiteThemePack.Theme theme) {
        String[] imgs = theme.cardImages();
        return """
                <section class="section">
                  <h2>Blog</h2>
                  <p class="lead">Stories from the %s team. Each card opens a related page.</p>
                  <div class="grid-3">
                    %s
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                brand,
                card("/site/services", img(imgs, 3), "Behind the experience", "How we craft moments guests remember."),
                card("/site/about", img(imgs, 4), "Meet the makers", "People, places, and the craft behind " + brand + "."),
                card("/site/contact", img(imgs, 5), "Ask us anything", "Planning a visit or trip? Start a conversation.")
        );
    }

    private static String careersBody(String brand, SiteThemePack.Theme theme) {
        String[] imgs = theme.cardImages();
        return """
                <section class="section">
                  <h2>Careers at %s</h2>
                  <p class="lead">Join a team that cares about craft. Click a role to apply via Contact.</p>
                  <div class="grid-2">
                    %s
                    %s
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                brand,
                card("/site/contact", img(imgs, 0), "Front of house lead", "Full-time · On-site"),
                card("/site/contact", img(imgs, 1), "Experience designer", "Hybrid · Full-time"),
                card("/site/contact", img(imgs, 2), "Operations manager", "Remote · Full-time"),
                card("/site/contact", img(imgs, 3), "Guest success specialist", "Full-time · Flexible")
        );
    }

    private static String contactBody(String brand, SiteThemePack.Theme theme) {
        return """
                <section class="section">
                  <h2>Contact %s</h2>
                  <p class="lead">Tell us what you want to build or book. We’ll respond within one business day.</p>
                  <div class="grid-2">
                    <form class="card" method="post" action="/site/contact" style="padding:1.25rem">
                      <label for="field1">Email</label>
                      <input id="field1" name="field1" placeholder="you@email.com"/>
                      <label for="field2">Name / company</label>
                      <input id="field2" name="field2" placeholder="Your name"/>
                      <label for="field3">Message</label>
                      <input id="field3" name="field3" placeholder="I’d like to book / plan / learn more…"/>
                      <button type="submit">Send message</button>
                    </form>
                    <a class="card" href="/site/home">
                      <img class="card-img" src="%s" alt="Back home" loading="lazy"/>
                      <div class="card-body">
                        <h3>Back to home</h3>
                        <p>Return to the main page and keep exploring.</p>
                        <span class="go">Go home →</span>
                      </div>
                    </a>
                  </div>
                </section>
                """.formatted(brand, escAttr(theme.heroImage()));
    }
}
