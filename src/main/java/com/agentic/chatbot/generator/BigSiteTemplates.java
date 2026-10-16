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
        // every brand gets its own palette, fonts, layout and wording (see SiteVariant)
        SiteVariant.Design d = SiteVariant.design(b, s);
        SiteThemePack.Theme theme = d.theme();
        String agent = SiteAgent.wantsAgent(s)
                ? SiteAgent.widget(SiteAgent.knowledge(b, theme, null, d.nav()), d.radius())
                : "";
        Map<String, String> pages = new LinkedHashMap<>();
        // slugs stay stable for routing; the visible titles/labels come from the business (SiteNav)
        pages.put("home", page(b, d, "home", d.label("home"), homeBody(b, d) + agent));
        pages.put("about", page(b, d, "about", d.label("about"), aboutBody(b, d) + agent));
        pages.put("services", page(b, d, "services", d.label("services"), servicesBody(b, d) + agent));
        pages.put("pricing", page(b, d, "pricing", d.label("pricing"), pricingBody(b, d) + agent));
        pages.put("blog", page(b, d, "blog", d.label("blog"), blogBody(b, d) + agent));
        pages.put("careers", page(b, d, "careers", d.label("careers"), careersBody(b, d) + agent));
        pages.put("contact", page(b, d, "contact", d.label("contact"), contactBody(b, d) + agent));
        return pages;
    }

    /** Expose the personalised theme so the generator can write SITE_STYLE.json. */
    static SiteThemePack.Theme themeFor(String brand, String story) {
        return SiteVariant.design(brand, story).theme();
    }

    static SiteVariant.Design designFor(String brand, String story) {
        return SiteVariant.design(brand, story);
    }

    private static String page(String brand, SiteVariant.Design d, String current, String title, String body) {
        SiteThemePack.Theme theme = d.theme();
        String fontLink = d.fontsHref();
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
                      --head-font:%s; --hw:%d; --r:%dpx; --rb:%dpx;
                    }
                    * { box-sizing:border-box; }
                    body { margin:0; font-family:%s; color:var(--ink); background:var(--bg); }
                    h1, h2, h3, .brand, .price { font-family:var(--head-font); }
                    .hero h1, .section h2 { font-weight:var(--hw); }
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
                      background:var(--accent); color:#fff !important; padding:.55rem 1rem; border-radius:var(--rb);
                      font-size:.88rem; font-weight:600;
                    }
                    .hero {
                      display:grid; grid-template-columns:1.05fr .95fr; gap:2.5rem; align-items:center;
                      padding:4rem 5vw 3rem; background:linear-gradient(180deg,var(--soft),var(--bg) 75%%);
                    }
                    .hero h1 { margin:0 0 1rem; font-size:clamp(2.1rem,5vw,3.4rem); line-height:1.08; letter-spacing:-.03em; }
                    .hero p { margin:0 0 1.5rem; color:var(--muted); font-size:1.05rem; line-height:1.65; max-width:36rem; }
                    .hero.reverse .visual { order:-1; }
                    .hero.center { grid-template-columns:1fr; text-align:center; }
                    .hero.center p { margin-left:auto; margin-right:auto; }
                    .hero.center .actions { justify-content:center; }
                    .hero.center .visual { min-height:300px; }
                    .actions { display:flex; gap:.75rem; flex-wrap:wrap; }
                    .btn {
                      display:inline-flex; align-items:center; justify-content:center; border-radius:var(--rb);
                      padding:.85rem 1.25rem; font-weight:700; font-size:.95rem;
                    }
                    .btn-primary { background:var(--accent); color:#fff; }
                    .btn-ghost { background:#fff; color:var(--accent); border:1px solid color-mix(in srgb, var(--accent) 35%%, #fff); }
                    .visual {
                      min-height:340px; border-radius:calc(var(--r) + 8px); overflow:hidden;
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
                      background:#fff; border:1px solid var(--line); border-radius:var(--r); padding:0;
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
                      margin-top:1rem; border:0; background:var(--accent); color:#fff; border-radius:var(--rb);
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
                    %s
                  </nav>
                  <a class="cta" href="/site/contact">%s</a>
                </header>
                %s
                <footer class="site-footer">
                  <div>
                    <strong>%s</strong>
                    <span>%s — %s</span>
                  </div>
                  <div><strong>Explore</strong>%s</div>
                  <div><strong>%s</strong>%s</div>
                  <div><strong>Tools</strong><a href="/site/home">%s</a><a href="/">New story</a><a href="/edit">Edit site</a></div>
                  <div class="copy">© 2026 %s · Privacy · Terms</div>
                </footer>
                </body>
                </html>
                """.formatted(
                brand, esc(title),
                fontLink,
                theme.bg(), theme.ink(), theme.muted(), theme.accent(), theme.soft(), theme.dark(),
                d.headFont(), d.headWeight(), d.radius(), d.radius() < 14 ? 8 : 999,
                theme.fontFamily(),
                escAttr(theme.heroImage()), brand,
                navHtml(d, current),
                d.ctaLabel(),
                body, brand, brand, theme.homeLead(),
                linksHtml(d, "services", "pricing", "blog"),
                esc(d.label("about")), linksHtml(d, "about", "careers", "contact"),
                esc(d.label("home")),
                brand
        );
    }

    /** The top menu: only the pages this site's nav keeps, each under the label that fits the business. */
    private static String navHtml(SiteVariant.Design d, String current) {
        StringBuilder sb = new StringBuilder();
        for (SiteNav.Item item : d.nav()) {
            if (!item.inMenu()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n                    ");
            }
            sb.append("<a href=\"").append(item.href()).append("\" class=\"")
                    .append(item.slug().equals(current) ? "active" : "").append("\">")
                    .append(esc(item.label())).append("</a>");
        }
        return sb.toString();
    }

    private static String linksHtml(SiteVariant.Design d, String... slugs) {
        StringBuilder sb = new StringBuilder();
        for (String slug : slugs) {
            sb.append("<a href=\"/site/").append(slug).append("\">").append(esc(d.label(slug))).append("</a>");
        }
        return sb.toString();
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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

    private static String card(String href, String image, String title, String blurb, String fallback) {
        String fb = (fallback == null || fallback.isBlank())
                ? "https://images.unsplash.com/photo-1511379938547-c1f69419868d?auto=format&fit=crop&w=800&q=80"
                : fallback;
        String src = (image == null || image.isBlank()) ? fb : image;
        return """
                <a class="card" href="%s">
                  <div class="card-media">
                    <img class="card-img" src="%s" alt="%s" loading="lazy"
                         onerror="this.onerror=null;this.src='%s'"/>
                  </div>
                  <div class="card-body">
                    <h3>%s</h3>
                    <p>%s</p>
                    <span class="go">Open page →</span>
                  </div>
                </a>
                """.formatted(href, escAttr(src), escAttr(title), escAttr(fb), title, blurb);
    }

    private static String card(String href, String image, String title, String blurb, SiteThemePack.Theme theme) {
        String fb = theme.heroImage();
        if (fb == null || fb.isBlank()) {
            fb = img(theme.cardImages(), 0);
        }
        return card(href, image, title, blurb, fb);
    }

    private static String homeBody(String brand, SiteVariant.Design d) {
        SiteThemePack.Theme theme = d.theme();
        String[] imgs = theme.cardImages();
        String layoutClass = switch (d.heroLayout()) {
            case "reverse" -> " reverse";
            case "center" -> " center";
            default -> "";
        };
        return """
                <section class="hero%s">
                  <div>
                    <p style="margin:0 0 .5rem;color:var(--accent);font-weight:700;letter-spacing:.04em;text-transform:uppercase;font-size:.85rem">%s</p>
                    <h1>%s</h1>
                    <p>%s</p>
                    <div class="actions">
                      <a class="btn btn-primary" href="/site/about">%s</a>
                      <a class="btn btn-ghost" href="/site/contact">%s</a>
                    </div>
                  </div>
                  <div class="visual"><img src="%s" alt="%s"/></div>
                </section>
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-3">
                    %s
                    %s
                    %s
                  </div>
                </section>
                <section class="section alt">
                  <h2>%s</h2>
                  <div class="grid-2">
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                layoutClass,
                d.kicker(),
                d.heroHeadline(),
                d.heroLead(),
                esc(d.label("about")), esc(d.label("contact")),
                escAttr(theme.heroImage()), brand,
                d.aboutHeading(),
                d.heroLead(),
                card("/site/about", img(imgs, 0), "Our story", "Learn who we are and what " + brand + " stands for.", theme),                card("/site/services", img(imgs, 1), theme.servicesLabel(), theme.serviceBlurbs()[0], theme),
                card("/site/contact", img(imgs, 2), d.label("contact"), "Reach the " + brand + " team anytime.", theme),
                d.exploreHeading(),
                card("/site/about", img(imgs, 3), d.label("about") + " →", "Read more about our mission and values.", theme),
                card("/site/pricing", img(imgs, 4), d.label("pricing") + " →", "Simple plans from " + brand + ".", theme)
        );
    }

    private static String aboutBody(String brand, SiteVariant.Design d) {
        SiteThemePack.Theme theme = d.theme();
        String[] imgs = theme.cardImages();
        return """
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-3">
                    %s
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                d.aboutHeading(),
                d.heroLead(),
                card("/site/services", img(imgs, 0), "What we do", theme.serviceBlurbs()[0], theme),
                card("/site/careers", img(imgs, 1), "Our people", "The team behind " + brand + ".", theme),
                card("/site/contact", img(imgs, 2), "Talk to us", "Questions about " + brand + "? We’re here to help.", theme)
        );
    }

    private static String servicesBody(String brand, SiteVariant.Design d) {
        SiteThemePack.Theme theme = d.theme();
        String[] imgs = theme.cardImages();
        String[] titles = theme.serviceTitles();
        String[] blurbs = theme.serviceBlurbs();
        String[] links = theme.serviceLinks();
        StringBuilder cards = new StringBuilder();
        for (int i = 0; i < titles.length; i++) {
            cards.append(card(links[i], img(imgs, i), titles[i], blurbs[i], theme));
        }
        return """
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-3">
                    %s
                  </div>
                </section>
                """.formatted(d.servicesHeading(), d.servicesLead(), cards);
    }

    private static String pricingBody(String brand, SiteVariant.Design d) {
        SiteThemePack.Theme theme = d.theme();
        String[] imgs = theme.cardImages();
        String[] plans = d.planNames();
        return """
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-3">
                    <a class="card" href="/site/contact">
                      <div class="card-media"><img class="card-img" src="%s" alt="%s" loading="lazy"/></div>
                      <div class="card-body">
                        <h3>%s</h3>
                        <div class="price">$0 <span>/mo</span></div>
                        <ul class="list"><li>Core pages</li><li>Theme images</li><li>Contact form</li></ul>
                        <span class="go">Choose %s →</span>
                      </div>
                    </a>
                    <a class="card" href="/site/contact" style="border-color:var(--accent)">
                      <div class="card-media"><img class="card-img" src="%s" alt="%s" loading="lazy"/></div>
                      <div class="card-body">
                        <h3>%s</h3>
                        <div class="price">$49 <span>/mo</span></div>
                        <ul class="list"><li>Unlimited stories</li><li>Custom colors & fonts</li><li>Priority support</li></ul>
                        <span class="go">Choose %s →</span>
                      </div>
                    </a>
                    <a class="card" href="/site/contact">
                      <div class="card-media"><img class="card-img" src="%s" alt="%s" loading="lazy"/></div>
                      <div class="card-body">
                        <h3>%s</h3>
                        <div class="price">Custom</div>
                        <ul class="list"><li>Dedicated workspace</li><li>SLA + onboarding</li><li>Security review</li></ul>
                        <span class="go">Talk to sales →</span>
                      </div>
                    </a>
                  </div>
                </section>
                """.formatted(d.pricingHeading(), d.pricingLead(),
                escAttr(img(imgs, 0)), plans[0], plans[0], plans[0],
                escAttr(img(imgs, 1)), plans[1], plans[1], plans[1],
                escAttr(img(imgs, 2)), plans[2], plans[2]);
    }

    private static String blogBody(String brand, SiteVariant.Design d) {
        SiteThemePack.Theme theme = d.theme();
        String[] imgs = theme.cardImages();
        String[] t = d.blogTitles();
        return """
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-3">
                    %s
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                d.blogHeading(), d.blogLead(),
                card("/site/services", img(imgs, 3), t[0], "How we craft moments people remember.", theme),
                card("/site/about", img(imgs, 4), t[1], "People, places, and the craft behind " + brand + ".", theme),
                card("/site/contact", img(imgs, 5), t[2], "Planning a visit or a project? Start a conversation.", theme)
        );
    }

    private static String careersBody(String brand, SiteVariant.Design d) {
        SiteThemePack.Theme theme = d.theme();
        String[] imgs = theme.cardImages();
        String[] r = d.roleTitles();
        return """
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">%s</p>
                  <div class="grid-2">
                    %s
                    %s
                    %s
                    %s
                  </div>
                </section>
                """.formatted(
                d.careersHeading(), d.careersLead(),
                card("/site/contact", img(imgs, 0), r[0], "Full-time · On-site", theme),
                card("/site/contact", img(imgs, 1), r[1], "Hybrid · Full-time", theme),
                card("/site/contact", img(imgs, 2), r[2], "Remote · Full-time", theme),
                card("/site/contact", img(imgs, 3), r[3], "Full-time · Flexible", theme)
        );
    }

    private static String contactBody(String brand, SiteVariant.Design d) {
        SiteThemePack.Theme theme = d.theme();
        return """
                <section class="section">
                  <h2>%s</h2>
                  <p class="lead">%s</p>
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
                """.formatted(d.contactHeading(), d.contactLead(), escAttr(theme.heroImage()));
    }
}
