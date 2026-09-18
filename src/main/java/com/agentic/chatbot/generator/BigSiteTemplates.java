package com.agentic.chatbot.generator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds a multi-page company website (home, about, services, pricing, blog, careers, contact).
 */
final class BigSiteTemplates {

    private BigSiteTemplates() {
    }

    static Map<String, String> generate(String brand, String story) {
        String b = brand == null || brand.isBlank() ? "NovaTech" : brand;
        String s = story == null ? "" : story;
        Map<String, String> pages = new LinkedHashMap<>();
        pages.put("home", page(b, s, "home", "Home", homeBody(b, s)));
        pages.put("about", page(b, s, "about", "About", aboutBody(b)));
        pages.put("services", page(b, s, "services", "Services", servicesBody(b)));
        pages.put("pricing", page(b, s, "pricing", "Pricing", pricingBody(b)));
        pages.put("blog", page(b, s, "blog", "Blog", blogBody(b)));
        pages.put("careers", page(b, s, "careers", "Careers", careersBody(b)));
        pages.put("contact", page(b, s, "contact", "Contact", contactBody(b)));
        return pages;
    }

    private static String page(String brand, String story, String current, String title, String body) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>%s · %s</title>
                  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap" rel="stylesheet"/>
                  <style>
                    :root {
                      --bg:#ffffff; --ink:#0f172a; --muted:#64748b; --line:#e2e8f0;
                      --accent:#1a73e8; --accent-2:#0f9d58; --soft:#eef4ff; --dark:#0b1220;
                    }
                    * { box-sizing:border-box; }
                    body { margin:0; font-family:Inter,Arial,sans-serif; color:var(--ink); background:var(--bg); }
                    a { color:inherit; text-decoration:none; }
                    .top {
                      position:sticky; top:0; z-index:20; backdrop-filter:blur(10px);
                      background:rgba(255,255,255,.9); border-bottom:1px solid var(--line);
                      display:flex; align-items:center; justify-content:space-between; gap:1rem;
                      padding:.85rem 5vw;
                    }
                    .brand { display:flex; align-items:center; gap:.65rem; font-weight:800; letter-spacing:-.03em; }
                    .mark {
                      width:34px; height:34px; border-radius:10px;
                      background:conic-gradient(from 180deg,#4285F4,#34A853,#FBBC05,#EA4335,#4285F4);
                    }
                    .nav { display:flex; flex-wrap:wrap; gap:.2rem 1rem; }
                    .nav a { color:var(--muted); font-size:.92rem; font-weight:500; padding:.35rem 0; }
                    .nav a:hover, .nav a.active { color:var(--accent); }
                    .cta {
                      background:var(--accent); color:#fff !important; padding:.55rem 1rem; border-radius:999px;
                      font-size:.88rem; font-weight:600;
                    }
                    .hero {
                      display:grid; grid-template-columns:1.15fr .85fr; gap:2.5rem; align-items:center;
                      padding:4.5rem 5vw 3rem; background:linear-gradient(180deg,var(--soft),#fff 70%%);
                    }
                    .hero h1 { margin:0 0 1rem; font-size:clamp(2.2rem,5vw,3.6rem); line-height:1.08; letter-spacing:-.04em; }
                    .hero p { margin:0 0 1.5rem; color:var(--muted); font-size:1.08rem; line-height:1.65; max-width:36rem; }
                    .actions { display:flex; gap:.75rem; flex-wrap:wrap; }
                    .btn {
                      display:inline-flex; align-items:center; justify-content:center; border-radius:999px;
                      padding:.85rem 1.25rem; font-weight:700; font-size:.95rem;
                    }
                    .btn-primary { background:var(--accent); color:#fff; }
                    .btn-ghost { background:#fff; color:var(--accent); border:1px solid #c2d7ff; }
                    .visual {
                      min-height:320px; border-radius:28px;
                      background:
                        radial-gradient(circle at 25%% 30%%,#4285F4,transparent 42%%),
                        radial-gradient(circle at 75%% 35%%,#34A853,transparent 40%%),
                        radial-gradient(circle at 50%% 80%%,#FBBC05,transparent 45%%),
                        #e8f0fe;
                      box-shadow:0 25px 60px rgba(26,115,232,.2);
                    }
                    .section { padding:3.5rem 5vw; }
                    .section.alt { background:#f8fafc; }
                    .section h2 { margin:0 0 .5rem; font-size:clamp(1.5rem,3vw,2rem); letter-spacing:-.02em; }
                    .section .lead { color:var(--muted); margin:0 0 1.75rem; max-width:40rem; line-height:1.6; }
                    .grid-3 { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:1rem; }
                    .grid-2 { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:1rem; }
                    .card {
                      background:#fff; border:1px solid var(--line); border-radius:18px; padding:1.25rem;
                      box-shadow:0 8px 24px rgba(15,23,42,.04);
                    }
                    .card h3 { margin:0 0 .45rem; font-size:1.05rem; }
                    .card p { margin:0; color:var(--muted); line-height:1.55; font-size:.94rem; }
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
                      .visual { min-height:220px; }
                      .nav { display:none; }
                    }
                  </style>
                </head>
                <body>
                <header class="top">
                  <a class="brand" href="/site/home"><span class="mark"></span>%s</a>
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
                    <span>A full multi-page website generated from your user story.</span>
                  </div>
                  <div><strong>Product</strong><a href="/site/services">Services</a><a href="/site/pricing">Pricing</a><a href="/site/blog">Blog</a></div>
                  <div><strong>Company</strong><a href="/site/about">About</a><a href="/site/careers">Careers</a><a href="/site/contact">Contact</a></div>
                  <div><strong>Account</strong><a href="/site/login">Sign in</a><a href="/">New story</a><a href="/edit">Edit site</a></div>
                  <div class="copy">© 2026 %s · Privacy · Terms · Built by Agentic bots</div>
                </footer>
                </body>
                </html>
                """.formatted(
                brand, title,
                brand,
                on(current, "home"), on(current, "about"), on(current, "services"), on(current, "pricing"),
                on(current, "blog"), on(current, "careers"), on(current, "contact"),
                body, brand, brand
        );
    }

    private static String on(String current, String name) {
        return current.equals(name) ? "active" : "";
    }

    private static String homeBody(String brand, String story) {
        return """
                <section class="hero">
                  <div>
                    <h1>Build faster. Ship smarter. Grow with %s.</h1>
                    <p>%s</p>
                    <div class="actions">
                      <a class="btn btn-primary" href="/site/contact">Talk to sales</a>
                      <a class="btn btn-ghost" href="/site/services">Explore services</a>
                    </div>
                  </div>
                  <div class="visual" role="img" aria-label="Product visual"></div>
                </section>
                <section class="section">
                  <h2>Why teams choose %s</h2>
                  <p class="lead">A complete website experience — not a single form — generated from your prompt.</p>
                  <div class="grid-3">
                    <article class="card"><h3>Launch ready</h3><p>Home, about, services, pricing, blog, careers, and contact pages included.</p></article>
                    <article class="card"><h3>Modern design</h3><p>Clean company aesthetic inspired by Google-quality product sites.</p></article>
                    <article class="card"><h3>Connected flows</h3><p>Navigation, CTAs, and contact capture work across every page.</p></article>
                  </div>
                </section>
                <section class="section alt">
                  <h2>Popular journeys</h2>
                  <div class="grid-2">
                    <a class="card" href="/site/pricing"><h3>See pricing →</h3><p>Compare plans and pick what fits your team.</p></a>
                    <a class="card" href="/site/careers"><h3>Join the team →</h3><p>Explore open roles and apply in minutes.</p></a>
                  </div>
                </section>
                """.formatted(brand, story, brand);
    }

    private static String aboutBody(String brand) {
        return """
                <section class="section">
                  <h2>About %s</h2>
                  <p class="lead">We help ambitious teams turn ideas into polished digital products — starting with the website itself.</p>
                  <div class="grid-3">
                    <article class="card"><h3>Our mission</h3><p>Make professional web experiences available from a single user story.</p></article>
                    <article class="card"><h3>Our approach</h3><p>Agentic bots generate UI, tests, and flows so humans can focus on strategy.</p></article>
                    <article class="card"><h3>Our promise</h3><p>Clear design, fast pages, and practical conversion paths on every screen.</p></article>
                  </div>
                </section>
                """.formatted(brand);
    }

    private static String servicesBody(String brand) {
        return """
                <section class="section">
                  <h2>Services</h2>
                  <p class="lead">Everything %s offers to help you launch and scale.</p>
                  <div class="grid-3">
                    <article class="card"><h3>Product websites</h3><p>Multi-page marketing sites with pricing, blog, and lead capture.</p></article>
                    <article class="card"><h3>Auth experiences</h3><p>Google-style sign-in and onboarding flows that feel familiar.</p></article>
                    <article class="card"><h3>Booking & commerce</h3><p>Appointments, catalogs, and checkout journeys ready to demo.</p></article>
                    <article class="card"><h3>Support portals</h3><p>Contact and ticket forms connected to confirmation pages.</p></article>
                    <article class="card"><h3>HR tools</h3><p>Leave requests, onboarding forms, and internal workflows.</p></article>
                    <article class="card"><h3>Automation</h3><p>Unit and functional tests generated alongside every screen.</p></article>
                  </div>
                </section>
                """.formatted(brand);
    }

    private static String pricingBody(String brand) {
        return """
                <section class="section">
                  <h2>Pricing</h2>
                  <p class="lead">Simple plans for teams of every size. Upgrade anytime.</p>
                  <div class="grid-3">
                    <article class="card">
                      <h3>Starter</h3>
                      <div class="price">$0 <span>/mo</span></div>
                      <ul class="list"><li>1 generated site</li><li>Core pages</li><li>Offline templates</li></ul>
                      <p style="margin-top:1rem"><a class="btn btn-ghost" href="/site/contact">Choose Starter</a></p>
                    </article>
                    <article class="card" style="border-color:#1a73e8;box-shadow:0 12px 30px rgba(26,115,232,.15)">
                      <h3>Growth</h3>
                      <div class="price">$49 <span>/mo</span></div>
                      <ul class="list"><li>Unlimited stories</li><li>LLM enhancement</li><li>Priority support</li></ul>
                      <p style="margin-top:1rem"><a class="btn btn-primary" href="/site/contact">Choose Growth</a></p>
                    </article>
                    <article class="card">
                      <h3>Enterprise</h3>
                      <div class="price">Custom</div>
                      <ul class="list"><li>SSO & security review</li><li>Dedicated workspace</li><li>SLA + onboarding</li></ul>
                      <p style="margin-top:1rem"><a class="btn btn-ghost" href="/site/contact">Talk to sales</a></p>
                    </article>
                  </div>
                </section>
                """;
    }

    private static String blogBody(String brand) {
        return """
                <section class="section">
                  <h2>Blog</h2>
                  <p class="lead">Ideas from the %s team on product, design, and agentic workflows.</p>
                  <div class="grid-3">
                    <article class="card"><h3>From user story to website in minutes</h3><p>How multi-agent pipelines turn prompts into shippable screens.</p></article>
                    <article class="card"><h3>Why multi-page beats single forms</h3><p>Visitors need journeys — home, pricing, careers, and contact working together.</p></article>
                    <article class="card"><h3>Design notes: Google-like clarity</h3><p>Whitespace, strong CTAs, and familiar patterns reduce friction.</p></article>
                  </div>
                </section>
                """.formatted(brand);
    }

    private static String careersBody(String brand) {
        return """
                <section class="section">
                  <h2>Careers at %s</h2>
                  <p class="lead">Help us build the future of agentic product creation.</p>
                  <div class="grid-2">
                    <article class="card"><h3>Senior Frontend Engineer</h3><p>Remote · Full-time</p><p style="margin-top:.75rem"><a class="btn btn-primary" href="/site/contact">Apply</a></p></article>
                    <article class="card"><h3>Product Designer</h3><p>Hybrid · Full-time</p><p style="margin-top:.75rem"><a class="btn btn-primary" href="/site/contact">Apply</a></p></article>
                    <article class="card"><h3>QA Automation Engineer</h3><p>Remote · Full-time</p><p style="margin-top:.75rem"><a class="btn btn-primary" href="/site/contact">Apply</a></p></article>
                    <article class="card"><h3>Customer Success Lead</h3><p>On-site · Full-time</p><p style="margin-top:.75rem"><a class="btn btn-primary" href="/site/contact">Apply</a></p></article>
                  </div>
                </section>
                """.formatted(brand);
    }

    private static String contactBody(String brand) {
        return """
                <section class="section">
                  <h2>Contact %s</h2>
                  <p class="lead">Tell us what you want to build. We’ll respond within one business day.</p>
                  <div class="grid-2">
                    <form class="card" method="post" action="/site/contact">
                      <label for="field1">Work email</label>
                      <input id="field1" name="field1" placeholder="you@company.com"/>
                      <label for="field2">Company</label>
                      <input id="field2" name="field2" placeholder="Acme Inc."/>
                      <label for="field3">Message</label>
                      <input id="field3" name="field3" placeholder="We need a full marketing website..."/>
                      <button type="submit">Send message</button>
                    </form>
                    <article class="card">
                      <h3>Other ways to reach us</h3>
                      <ul class="list">
                        <li>hello@%s.example</li>
                        <li>+1 (555) 010-2026</li>
                        <li>Mon–Fri · 9am–6pm</li>
                      </ul>
                    </article>
                  </div>
                </section>
                """.formatted(brand, brand.toLowerCase().replaceAll("[^a-z0-9]", ""));
    }
}
