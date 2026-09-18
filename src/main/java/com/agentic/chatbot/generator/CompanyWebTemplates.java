package com.agentic.chatbot.generator;

/**
 * Professional Google / company website HTML templates for generated pages.
 */
final class CompanyWebTemplates {

    private CompanyWebTemplates() {
    }

    static String googleLoginPage(String route) {
        return """
                <!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title th:text="${title}">Sign in</title>
                  <link rel="preconnect" href="https://fonts.googleapis.com"/>
                  <link href="https://fonts.googleapis.com/css2?family=Google+Sans:wght@400;500;700&family=Roboto:wght@400;500&display=swap" rel="stylesheet"/>
                  <style>
                    :root {
                      --bg: #f0f4f9;
                      --card: #ffffff;
                      --accent: #1a73e8;
                      --text: #202124;
                      --muted: #5f6368;
                      --line: #dadce0;
                      --footer: #f8f9fa;
                    }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0; min-height: 100vh; display: flex; flex-direction: column;
                      font-family: "Google Sans", Roboto, Arial, sans-serif;
                      background: var(--bg); color: var(--text);
                    }
                    .wrap { flex: 1; display: grid; place-items: center; padding: 2rem 1rem 4rem; }
                    .card {
                      width: min(450px, 100%%); background: var(--card); border: 1px solid var(--line);
                      border-radius: 8px; padding: 48px 40px 36px; box-shadow: 0 1px 2px rgba(60,64,67,.1);
                    }
                    .logo { display: flex; justify-content: center; margin-bottom: 1rem; }
                    .logo svg { width: 75px; height: 24px; }
                    h1 { margin: 0; text-align: center; font-size: 24px; font-weight: 400; }
                    .subtitle { text-align: center; color: var(--muted); font-size: 16px; margin: .5rem 0 1.75rem; font-weight: 400; }
                    label { display: block; position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
                    .field { margin-bottom: 1rem; }
                    input {
                      width: 100%%; padding: 13px 15px; border: 1px solid var(--line); border-radius: 4px;
                      font: 16px Roboto, Arial, sans-serif; color: var(--text); outline: none; background: #fff;
                    }
                    input:focus { border-color: var(--accent); box-shadow: inset 0 0 0 1px var(--accent); }
                    .error { color: #d93025; font-size: 13px; margin: 0 0 .75rem; }
                    .actions { display: flex; justify-content: space-between; align-items: center; margin-top: 1.75rem; gap: 1rem; }
                    .link { color: var(--accent); text-decoration: none; font-size: 14px; font-weight: 500; }
                    .link:hover { text-decoration: underline; }
                    button[type=submit] {
                      background: var(--accent); color: #fff; border: 0; border-radius: 4px; padding: 10px 24px;
                      font: 500 14px "Google Sans", Roboto, Arial, sans-serif; cursor: pointer;
                    }
                    button[type=submit]:hover { background: #1765cc; box-shadow: 0 1px 2px rgba(0,0,0,.2); }
                    .divider { display: flex; align-items: center; gap: .75rem; margin: 1.4rem 0 .9rem; color: var(--muted); font-size: 12px; }
                    .divider::before, .divider::after { content: ""; flex: 1; height: 1px; background: var(--line); }
                    .google-btn {
                      width: 100%%; display: flex; align-items: center; justify-content: center; gap: .65rem;
                      padding: 10px 12px; border: 1px solid var(--line); border-radius: 4px; background: #fff;
                      color: #3c4043; font: 500 14px "Google Sans", Roboto, Arial, sans-serif; text-decoration: none;
                    }
                    .google-btn:hover { background: #f8f9fa; box-shadow: 0 1px 2px rgba(60,64,67,.15); }
                    .site-footer {
                      display: flex; flex-wrap: wrap; justify-content: space-between; gap: .75rem;
                      padding: 1rem 1.5rem; background: var(--footer); color: var(--muted); font-size: 12px;
                      border-top: 1px solid var(--line);
                    }
                    .site-footer nav { display: flex; gap: 1.25rem; }
                    .site-footer a { color: var(--muted); text-decoration: none; }
                    .site-footer a:hover { text-decoration: underline; }
                    @media (max-width: 480px) {
                      .card { padding: 32px 24px 28px; border: 0; box-shadow: none; background: transparent; }
                      .wrap { padding-top: 1rem; }
                    }
                  </style>
                </head>
                <body>
                <div class="wrap">
                  <div class="card">
                    <div class="logo" aria-hidden="true">
                      <svg width="40" height="40" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
                        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                        <path fill="#fff" d="M24 19v10h15.6c.4-1.9.6-3.9.6-5.9 0-1.4-.1-2.8-.4-4.1H24z"/>
                      </svg>
                    </div>
                    <h1 th:text="${title}">Sign in</h1>
                    <p class="subtitle">to continue to your workspace</p>
                    <div class="error" th:if="${error}" th:text="${error}"></div>
                    <form th:action="@{%s}" th:object="${form}" method="post">
                      <div class="field">
                        <label for="field1">Email or phone</label>
                        <input id="field1" th:field="*{field1}" autocomplete="username" placeholder="Email or phone"/>
                      </div>
                      <div class="field">
                        <label for="field2">Password</label>
                        <input id="field2" type="password" th:field="*{field2}" autocomplete="current-password" placeholder="Enter your password"/>
                      </div>
                      <div class="actions">
                        <a class="link" href="/auth/google?page=login">Create account</a>
                        <button type="submit">Next</button>
                      </div>
                    </form>
                    <div class="divider">or</div>
                    <a class="google-btn" href="/auth/google?page=login">
                      <svg width="18" height="18" viewBox="0 0 48 48" aria-hidden="true">
                        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
                      </svg>
                      Sign in with Google
                    </a>
                  </div>
                </div>
                <footer class="site-footer">
                  <span>© 2026 Company</span>
                  <nav>
                    <a href="#">Help</a>
                    <a href="#">Privacy</a>
                    <a href="#">Terms</a>
                  </nav>
                </footer>
                </body>
                </html>
                """.formatted(route);
    }

    static String companyWebsitePage(String title, String story, String route) {
        return """
                <!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title th:text="${title}">%s</title>
                  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet"/>
                  <style>
                    :root {
                      --bg: #ffffff; --card: #ffffff; --accent: #1a73e8; --text: #202124; --muted: #5f6368;
                      --line: #e8eaed; --footer: #202124; --hero: #e8f0fe;
                    }
                    * { box-sizing: border-box; }
                    body { margin: 0; font-family: Inter, Roboto, Arial, sans-serif; color: var(--text); background: var(--bg); }
                    .topnav {
                      display: flex; align-items: center; justify-content: space-between; gap: 1rem;
                      padding: .85rem 5vw; border-bottom: 1px solid var(--line); background: #fff; position: sticky; top: 0; z-index: 10;
                    }
                    .brand { display: flex; align-items: center; gap: .6rem; font-weight: 700; letter-spacing: -.02em; }
                    .brand-mark {
                      width: 32px; height: 32px; border-radius: 8px; background: linear-gradient(135deg,#4285F4,#34A853 55%%,#FBBC05,#EA4335);
                    }
                    .topnav nav { display: flex; gap: 1.25rem; flex-wrap: wrap; }
                    .topnav a { color: var(--muted); text-decoration: none; font-size: .92rem; font-weight: 500; }
                    .topnav a:hover { color: var(--text); }
                    .cta-nav {
                      background: var(--accent); color: #fff !important; padding: .55rem 1rem; border-radius: 999px; font-size: .88rem;
                    }
                    .hero {
                      display: grid; grid-template-columns: 1.1fr .9fr; gap: 2.5rem; align-items: center;
                      padding: 4.5rem 5vw 3.5rem; background: linear-gradient(180deg, var(--hero), #fff 70%%);
                    }
                    .hero h1 { margin: 0 0 1rem; font-size: clamp(2rem, 4vw, 3.2rem); line-height: 1.15; letter-spacing: -.03em; }
                    .hero .story { color: var(--muted); font-size: 1.08rem; line-height: 1.6; margin: 0 0 1.5rem; max-width: 36rem; }
                    .hero-actions { display: flex; gap: .75rem; flex-wrap: wrap; }
                    .btn {
                      display: inline-flex; align-items: center; justify-content: center; border: 0; border-radius: 999px;
                      padding: .8rem 1.3rem; font-weight: 600; font-size: .95rem; cursor: pointer; text-decoration: none;
                    }
                    .btn-primary { background: var(--accent); color: #fff; }
                    .btn-ghost { background: #fff; color: var(--accent); border: 1px solid #c2e7ff; }
                    .hero-visual {
                      min-height: 280px; border-radius: 24px;
                      background:
                        radial-gradient(circle at 30%% 30%%, #4285F4, transparent 45%%),
                        radial-gradient(circle at 70%% 40%%, #34A853, transparent 40%%),
                        radial-gradient(circle at 50%% 80%%, #FBBC05, transparent 45%%),
                        #e8f0fe;
                      box-shadow: 0 20px 50px rgba(26,115,232,.18);
                    }
                    .features {
                      display: grid; grid-template-columns: repeat(3, 1fr); gap: 1.25rem; padding: 1rem 5vw 3.5rem;
                    }
                    .feature {
                      border: 1px solid var(--line); border-radius: 16px; padding: 1.35rem; background: var(--card);
                    }
                    .feature h3 { margin: 0 0 .45rem; font-size: 1.05rem; }
                    .feature p { margin: 0; color: var(--muted); line-height: 1.5; font-size: .94rem; }
                    .panel-wrap { padding: 0 5vw 3.5rem; }
                    .panel {
                      border: 1px solid var(--line); border-radius: 20px; padding: 1.75rem; background: #fff;
                      box-shadow: 0 8px 30px rgba(32,33,36,.06); max-width: 720px;
                    }
                    .panel h2 { margin: 0 0 1rem; font-size: 1.35rem; }
                    .ok { background: #e6f4ea; color: #137333; padding: .75rem 1rem; border-radius: 8px; margin-bottom: 1rem; }
                    label { display: block; margin: .8rem 0 .35rem; font-size: .85rem; color: var(--muted); font-weight: 500; }
                    input {
                      width: 100%%; padding: .75rem .9rem; border: 1px solid var(--line); border-radius: 8px; font: inherit;
                    }
                    input:focus { outline: 2px solid #aecbfa; border-color: var(--accent); }
                    button[type=submit] {
                      margin-top: 1.15rem; background: var(--accent); color: #fff; border: 0; border-radius: 999px;
                      padding: .75rem 1.35rem; font-weight: 600; cursor: pointer;
                    }
                    .site-footer {
                      background: var(--footer); color: #9aa0a6; padding: 2.5rem 5vw; display: grid;
                      grid-template-columns: 1.2fr 1fr 1fr 1fr; gap: 1.5rem;
                    }
                    .site-footer strong { color: #fff; display: block; margin-bottom: .7rem; }
                    .site-footer a { color: #9aa0a6; text-decoration: none; display: block; margin: .35rem 0; font-size: .9rem; }
                    .site-footer a:hover { color: #fff; }
                    .copy { grid-column: 1 / -1; border-top: 1px solid #3c4043; margin-top: 1rem; padding-top: 1rem; font-size: .85rem; }
                    @media (max-width: 900px) {
                      .hero, .features, .site-footer { grid-template-columns: 1fr; }
                      .hero-visual { min-height: 200px; }
                    }
                  </style>
                </head>
                <body>
                <header class="topnav">
                  <div class="brand"><span class="brand-mark"></span><span th:text="${title}">%s</span></div>
                  <nav>
                    <a href="#product">Product</a>
                    <a href="#solutions">Solutions</a>
                    <a href="#form">Get started</a>
                  </nav>
                  <a class="cta-nav" href="#form">Contact sales</a>
                </header>

                <section class="hero">
                  <div>
                    <h1 th:text="${title}">%s</h1>
                    <p class="story" th:text="${story}">%s</p>
                    <div class="hero-actions">
                      <a class="btn btn-primary" href="#form">Get started</a>
                      <a class="btn btn-ghost" href="#product">Learn more</a>
                    </div>
                  </div>
                  <div class="hero-visual" role="img" aria-label="Product visual"></div>
                </section>

                <section class="features" id="product">
                  <article class="feature">
                    <h3>Built for teams</h3>
                    <p>Collaborate securely with modern workflows inspired by leading company platforms.</p>
                  </article>
                  <article class="feature" id="solutions">
                    <h3>Enterprise ready</h3>
                    <p>Clean design, fast forms, and reliable experiences across desktop and mobile.</p>
                  </article>
                  <article class="feature">
                    <h3>Simple onboarding</h3>
                    <p>Generated from your user story so the first screen matches what you asked for.</p>
                  </article>
                </section>

                <section class="panel-wrap" id="form">
                  <div class="panel">
                    <h2>Get started</h2>
                    <div class="ok" th:if="${success}">Thanks — your request was submitted.</div>
                    <form th:action="@{%s}" th:object="${form}" method="post">
                      <label for="field1">Work email</label>
                      <input id="field1" th:field="*{field1}" placeholder="name@company.com"/>
                      <label for="field2">Company name</label>
                      <input id="field2" th:field="*{field2}" placeholder="Acme Inc."/>
                      <button type="submit">Continue</button>
                    </form>
                  </div>
                </section>

                <footer class="site-footer">
                  <div>
                    <strong th:text="${title}">%s</strong>
                    <span>A modern company experience generated from your prompt.</span>
                  </div>
                  <div><strong>Product</strong><a href="#">Overview</a><a href="#">Pricing</a><a href="#">Security</a></div>
                  <div><strong>Company</strong><a href="#">About</a><a href="#">Careers</a><a href="#">Blog</a></div>
                  <div><strong>Support</strong><a href="#">Help Center</a><a href="#">Contact</a><a href="#">Status</a></div>
                  <div class="copy">© 2026 Company · Privacy · Terms</div>
                </footer>
                </body>
                </html>
                """.formatted(title, title, title, story, route, title);
    }

    static String companyDashboardPage(String backRoute) {
        return """
                <!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                  <meta charset="UTF-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title th:text="${title}">Dashboard</title>
                  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet"/>
                  <style>
                    :root { --bg:#f8f9fa; --card:#fff; --accent:#1a73e8; --text:#202124; --muted:#5f6368; --line:#e8eaed; }
                    body { margin:0; font-family:Inter,Roboto,Arial,sans-serif; background:var(--bg); color:var(--text); }
                    .bar { display:flex; justify-content:space-between; align-items:center; padding:.9rem 5vw; background:#fff; border-bottom:1px solid var(--line); }
                    .brand { font-weight:700; display:flex; gap:.55rem; align-items:center; }
                    .dot { width:28px; height:28px; border-radius:50%%; background:conic-gradient(#4285F4,#34A853,#FBBC05,#EA4335,#4285F4); }
                    main { padding:2rem 5vw; }
                    .welcome { background:var(--card); border:1px solid var(--line); border-radius:16px; padding:1.75rem; max-width:920px; box-shadow:0 8px 24px rgba(32,33,36,.06); }
                    h1 { margin:0 0 .5rem; font-size:1.75rem; }
                    p { color:var(--muted); margin:0 0 1.25rem; line-height:1.5; }
                    a.signout { color:var(--accent); text-decoration:none; font-weight:600; }
                    .cards { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:1rem; margin-top:1.5rem; }
                    .card { background:#fff; border:1px solid var(--line); border-radius:12px; padding:1rem; }
                    .card strong { display:block; margin-bottom:.35rem; }
                    .card span { color:var(--muted); font-size:.9rem; }
                    .apps { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:.75rem; margin-top:1.25rem; }
                    .app { display:block; padding:1rem; border-radius:12px; background:#e8f0fe; color:#174ea6; text-decoration:none; font-weight:600; }
                    @media (max-width:800px){ .cards,.apps{grid-template-columns:1fr;} }
                  </style>
                </head>
                <body>
                <header class="bar">
                  <div class="brand"><span class="dot"></span> Workspace</div>
                  <a class="signout" th:href="@{%s}">Sign out</a>
                </header>
                <main>
                  <div class="welcome">
                    <h1 th:text="${title}">Dashboard</h1>
                    <p th:text="${message}">You're signed in. This is your generated homepage after login.</p>
                    <div class="cards">
                      <div class="card"><strong>Overview</strong><span>Your account is ready.</span></div>
                      <div class="card"><strong>Activity</strong><span>No new alerts.</span></div>
                      <div class="card"><strong>Team</strong><span>Invite colleagues anytime.</span></div>
                    </div>
                    <div class="apps">
                      <a class="app" href="/site/appointment-booking">Open appointment booking</a>
                      <a class="app" href="/">Start a new user story</a>
                    </div>
                  </div>
                </main>
                </body>
                </html>
                """.formatted(backRoute);
    }

    static String pageFor(StoryAnalyzer.Analysis a, String story, String route) {
        return switch (a.domain()) {
            case LOGIN, REGISTER -> googleLoginPage(route);
            case APPOINTMENT -> appointmentPage(a.headline(), story, route, a.fieldLabels());
            case ECOMMERCE -> checkoutPage(a.headline(), story, route, a.fieldLabels());
            case CATALOG -> catalogPage(a.headline(), story, route);
            case HR -> serviceFormPage(a.headline(), "HR portal", story, route, a.fieldLabels(), "#0f9d58");
            case SUPPORT -> serviceFormPage(a.headline(), "Help Center", story, route, a.fieldLabels(), "#1a73e8");
            case FINANCE -> serviceFormPage(a.headline(), "Secure banking", story, route, a.fieldLabels(), "#188038");
            case DASHBOARD -> companyDashboardPage(route);
            case LANDING, GENERIC -> companyWebsitePage(a.headline(), story, route);
        };
    }

    static String confirmationFor(StoryAnalyzer.Analysis a, String backRoute) {
        return switch (a.domain()) {
            case LOGIN, REGISTER -> companyDashboardPage(backRoute);
            case APPOINTMENT -> confirmationPage("Appointment confirmed",
                    "Your booking request was received. We’ll email the clinic details shortly.",
                    backRoute,
                    new String[]{"Status: Pending confirmation", "Bring ID and insurance card", "Arrive 10 minutes early"});
            case ECOMMERCE -> confirmationPage("Order placed",
                    "Thanks! Your order is being prepared.",
                    backRoute,
                    new String[]{"Order #: AG-2048", "Delivery: 3–5 days", "Track in your email"});
            case HR -> confirmationPage("Request submitted",
                    "HR will review your request within 1–2 business days.",
                    backRoute,
                    new String[]{"Ticket created", "Manager notified", "Check status in portal"});
            case SUPPORT -> confirmationPage("Ticket created",
                    "Our support team will reply soon.",
                    backRoute,
                    new String[]{"Priority: Normal", "Channel: Web", "Keep this reference"});
            case FINANCE -> confirmationPage("Transfer initiated",
                    "Your transfer is processing securely.",
                    backRoute,
                    new String[]{"Reference generated", "Usually settles in minutes", "Fraud checks applied"});
            default -> confirmationPage(a.headline() + " — submitted",
                    "Your action completed on the generated webpage.",
                    backRoute,
                    new String[]{"Page created", "Forms connected", "Tests generated"});
        };
    }

    static String appointmentPage(String title, String story, String route, String[] labels) {
        return serviceFormPage(title, "HealthClinic", story, route, labels, "#00897b");
    }

    static String checkoutPage(String title, String story, String route, String[] labels) {
        return serviceFormPage(title, "Shop", story, route, labels, "#e37400");
    }

    static String catalogPage(String title, String story, String route) {
        return """
                <!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                  <meta charset="UTF-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title th:text="${title}">%s</title>
                  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap" rel="stylesheet"/>
                  <style>
                    :root { --accent:#1a73e8; --text:#202124; --muted:#5f6368; --line:#e8eaed; }
                    body{margin:0;font-family:Inter,Arial,sans-serif;color:var(--text);background:#f8f9fa}
                    header{display:flex;justify-content:space-between;align-items:center;padding:1rem 5vw;background:#fff;border-bottom:1px solid var(--line)}
                    .brand{font-weight:700}
                    .grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:1rem;padding:1.5rem 5vw 3rem}
                    .item{background:#fff;border:1px solid var(--line);border-radius:16px;overflow:hidden}
                    .pic{height:140px;background:linear-gradient(135deg,#e8f0fe,#d2e3fc)}
                    .body{padding:1rem}
                    .body h3{margin:0 0 .35rem;font-size:1rem}
                    .body p{margin:0 0 .8rem;color:var(--muted);font-size:.9rem}
                    .body button,.body a.btn{background:var(--accent);color:#fff;border:0;border-radius:999px;padding:.55rem 1rem;text-decoration:none;font-size:.85rem;font-weight:600}
                    .search{padding:1.25rem 5vw 0}
                    .search form{display:flex;gap:.5rem;background:#fff;border:1px solid var(--line);border-radius:999px;padding:.4rem .4rem .4rem 1rem}
                    .search input{flex:1;border:0;outline:none;font:inherit}
                    .search button{border:0;background:var(--accent);color:#fff;border-radius:999px;padding:.65rem 1.1rem;font-weight:600}
                    .ok{margin:1rem 5vw 0;background:#e6f4ea;color:#137333;padding:.75rem 1rem;border-radius:8px}
                    @media(max-width:800px){.grid{grid-template-columns:1fr}}
                  </style>
                </head>
                <body>
                <header><div class="brand" th:text="${title}">%s</div><a href="#search">Search</a></header>
                <div class="search" id="search">
                  <div class="ok" th:if="${success}">Showing matching products.</div>
                  <form th:action="@{%s}" th:object="${form}" method="post">
                    <input th:field="*{field1}" placeholder="Search products"/>
                    <input type="hidden" th:field="*{field2}" value="all"/>
                    <button type="submit">Search</button>
                  </form>
                </div>
                <p style="padding:1rem 5vw 0;color:#5f6368" th:text="${story}">%s</p>
                <section class="grid">
                  <article class="item"><div class="pic"></div><div class="body"><h3>Product Alpha</h3><p>Popular pick for new customers.</p><a class="btn" href="#">View</a></div></article>
                  <article class="item"><div class="pic"></div><div class="body"><h3>Product Beta</h3><p>Best value this week.</p><a class="btn" href="#">View</a></div></article>
                  <article class="item"><div class="pic"></div><div class="body"><h3>Product Gamma</h3><p>Premium quality option.</p><a class="btn" href="#">View</a></div></article>
                </section>
                </body></html>
                """.formatted(title, title, route, story);
    }

    static String serviceFormPage(String title, String brand, String story, String route, String[] labels, String accent) {
        String l1 = labels.length > 0 ? labels[0] : "Field 1";
        String l2 = labels.length > 1 ? labels[1] : "Field 2";
        String l3 = labels.length > 2 && labels[2] != null && !labels[2].isBlank() ? labels[2] : null;
        String l4 = labels.length > 3 && labels[3] != null && !labels[3].isBlank() ? labels[3] : null;
        String extraFields = "";
        if (l3 != null) {
            extraFields += """
                    <label for="field3">%s</label>
                    <input id="field3" th:field="*{field3}" placeholder="%s"/>
                    """.formatted(l3, l3);
        }
        if (l4 != null) {
            extraFields += """
                    <label for="field4">%s</label>
                    <input id="field4" th:field="*{field4}" placeholder="%s"/>
                    """.formatted(l4, l4);
        }
        return """
                <!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                  <meta charset="UTF-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title th:text="${title}">%s</title>
                  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap" rel="stylesheet"/>
                  <style>
                    :root { --accent:%s; --text:#202124; --muted:#5f6368; --line:#e8eaed; --bg:#f8fafc; }
                    body{margin:0;font-family:Inter,Arial,sans-serif;background:var(--bg);color:var(--text)}
                    header{display:flex;justify-content:space-between;align-items:center;padding:1rem 5vw;background:#fff;border-bottom:1px solid var(--line)}
                    .brand{font-weight:700;display:flex;gap:.55rem;align-items:center}
                    .mark{width:28px;height:28px;border-radius:8px;background:var(--accent)}
                    main{max-width:720px;margin:0 auto;padding:2rem 1.25rem 3rem}
                    .hero{margin-bottom:1.25rem}
                    .hero h1{margin:0 0 .5rem;font-size:clamp(1.6rem,3vw,2.2rem)}
                    .hero p{margin:0;color:var(--muted);line-height:1.5}
                    .panel{background:#fff;border:1px solid var(--line);border-radius:18px;padding:1.5rem;box-shadow:0 10px 30px rgba(32,33,36,.06)}
                    .ok{background:#e6f4ea;color:#137333;padding:.75rem 1rem;border-radius:8px;margin-bottom:1rem}
                    label{display:block;margin:.85rem 0 .35rem;font-size:.85rem;color:var(--muted);font-weight:600}
                    input,select,textarea{width:100%%;box-sizing:border-box;padding:.75rem .9rem;border:1px solid var(--line);border-radius:10px;font:inherit}
                    button{margin-top:1.2rem;background:var(--accent);color:#fff;border:0;border-radius:999px;padding:.8rem 1.35rem;font-weight:700;cursor:pointer}
                    .slots{display:grid;grid-template-columns:repeat(3,1fr);gap:.5rem;margin-top:.5rem}
                    .slot{border:1px solid var(--line);border-radius:10px;padding:.65rem;text-align:center;background:#f8f9fa;font-size:.85rem}
                    footer{padding:1.25rem 5vw;color:var(--muted);font-size:.85rem;text-align:center}
                  </style>
                </head>
                <body>
                <header>
                  <div class="brand"><span class="mark"></span>%s</div>
                  <a href="/" style="color:var(--accent);text-decoration:none;font-weight:600">New story</a>
                </header>
                <main>
                  <div class="hero">
                    <h1 th:text="${title}">%s</h1>
                    <p class="story" th:text="${story}">%s</p>
                  </div>
                  <div class="panel">
                    <div class="ok" th:if="${success}">Submitted successfully — your webpage action completed.</div>
                    <form th:action="@{%s}" th:object="${form}" method="post">
                      <label for="field1">%s</label>
                      <input id="field1" th:field="*{field1}" placeholder="%s"/>
                      <label for="field2">%s</label>
                      <input id="field2" th:field="*{field2}" placeholder="%s"/>
                      %s
                      <div class="slots" aria-hidden="true">
                        <div class="slot">Today</div><div class="slot">Tomorrow</div><div class="slot">This week</div>
                      </div>
                      <button type="submit">Continue</button>
                    </form>
                  </div>
                </main>
                <footer class="site-footer">Generated webpage for your user story · © 2026</footer>
                </body></html>
                """.formatted(title, accent, brand, title, story, route, l1, l1, l2, l2, extraFields);
    }

    static String confirmationPage(String title, String message, String backRoute, String[] bullets) {
        StringBuilder lis = new StringBuilder();
        for (String b : bullets) {
            lis.append("<li>").append(b).append("</li>");
        }
        return """
                <!DOCTYPE html>
                <html lang="en" xmlns:th="http://www.thymeleaf.org">
                <head>
                  <meta charset="UTF-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title th:text="${title}">%s</title>
                  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700&display=swap" rel="stylesheet"/>
                  <style>
                    body{margin:0;font-family:Inter,Arial,sans-serif;background:#f8f9fa;color:#202124}
                    main{max-width:640px;margin:3rem auto;padding:0 1.25rem}
                    .card{background:#fff;border:1px solid #e8eaed;border-radius:18px;padding:1.75rem;box-shadow:0 10px 28px rgba(32,33,36,.08)}
                    h1{margin:0 0 .5rem}
                    p{color:#5f6368;line-height:1.5}
                    ul{padding-left:1.1rem;color:#3c4043}
                    a{display:inline-block;margin-top:1rem;background:#1a73e8;color:#fff;text-decoration:none;padding:.7rem 1.1rem;border-radius:999px;font-weight:600}
                  </style>
                </head>
                <body>
                <main>
                  <div class="card">
                    <h1 th:text="${title}">%s</h1>
                    <p th:text="${message}">%s</p>
                    <ul>%s</ul>
                    <a th:href="@{%s}">Back to page</a>
                  </div>
                </main>
                </body></html>
                """.formatted(title, title, message, lis, backRoute);
    }
}
