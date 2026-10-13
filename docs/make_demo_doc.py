"""Builds docs/BOT_Demo_Guide.docx: verified prompts, file-by-file code guide, drawbacks and enhancements."""
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Inches, Pt, RGBColor
from PIL import Image, ImageDraw, ImageFont

DOCS = Path(__file__).resolve().parent
FONTS = Path("C:/Windows/Fonts")
ACCENT = RGBColor(0x43, 0x38, 0xCA)
GREY = RGBColor(0x47, 0x55, 0x69)
RED = RGBColor(0xB9, 0x1C, 0x1C)


# ---------------------------------------------------------------- drawing

def font(name, size):
    try:
        return ImageFont.truetype(str(FONTS / name), size)
    except OSError:
        return ImageFont.load_default()


def draw_pipeline(path):
    steps = [
        (["Prompt"], "End-user request"),
        (["1 Business", "Analyst"], "User story"),
        (["2 Java", "Developer"], "Website code"),
        (["3 Java Unit", "Tester"], "JUnit tests"),
        (["4 Functional", "Testing"], "MockMvc tests"),
        (["5 Developer", "Tester"], "Verify + train"),
        (["Output"], "Stories + code"),
    ]
    bw, bh, gap = 300, 200, 40
    w = len(steps) * bw + (len(steps) - 1) * gap + 60
    img = Image.new("RGB", (w, bh + 60), "white")
    d = ImageDraw.Draw(img)
    title, body = font("segoeuib.ttf", 34), font("segoeui.ttf", 30)
    for i, (lines, sub) in enumerate(steps):
        x, y = 30 + i * (bw + gap), 30
        edge = i in (0, len(steps) - 1)
        d.rounded_rectangle([x, y, x + bw, y + bh], radius=24,
                            fill=(241, 245, 249) if edge else (238, 242, 255),
                            outline=(100, 116, 139) if edge else (67, 56, 202), width=4)
        for li, line in enumerate(lines):
            lw = d.textlength(line, font=title)
            d.text((x + (bw - lw) / 2, y + 22 + li * 44), line, font=title, fill=(30, 27, 75))
        sw = d.textlength(sub, font=body)
        d.text((x + (bw - sw) / 2, y + 140), sub, font=body, fill=(71, 85, 105))
        if i < len(steps) - 1:
            ax, ay = x + bw + 6, y + bh / 2
            d.polygon([(ax, ay - 14), (ax + gap - 12, ay), (ax, ay + 14)], fill=(67, 56, 202))
    img.save(path)


def draw_routing(path):
    w, h = 1900, 560
    img = Image.new("RGB", (w, h), "white")
    d = ImageDraw.Draw(img)
    t, b = font("segoeuib.ttf", 32), font("segoeui.ttf", 27)

    def box(x, y, bw, bh, head, sub, fill, edge):
        d.rounded_rectangle([x, y, x + bw, y + bh], radius=22, fill=fill, outline=edge, width=4)
        hw = d.textlength(head, font=t)
        d.text((x + (bw - hw) / 2, y + 18), head, font=t, fill=(30, 27, 75))
        for i, line in enumerate(sub.split("\n")):
            lw = d.textlength(line, font=b)
            d.text((x + (bw - lw) / 2, y + 66 + i * 34), line, font=b, fill=(71, 85, 105))

    def arrow(x1, y1, x2, y2):
        d.line([(x1, y1), (x2, y2)], fill=(67, 56, 202), width=5)
        d.polygon([(x2, y2), (x2 - 18, y2 - 11), (x2 - 18, y2 + 11)], fill=(67, 56, 202))

    box(30, 200, 300, 150, "Chat message", "POST /api/pipeline", (241, 245, 249), (100, 116, 139))
    box(420, 200, 360, 150, "1. Conversion?", "fromChatMessage()", (238, 242, 255), (67, 56, 202))
    box(870, 200, 360, 150, "2. Edit?", "looksLikeEdit() and\na site already exists", (238, 242, 255), (67, 56, 202))
    box(1320, 200, 330, 150, "3. New website", "PipelineOrchestrator\n(5 bots)", (238, 242, 255), (67, 56, 202))
    box(420, 20, 360, 130, "Code Converter", "CodeConversionService", (236, 253, 245), (5, 150, 105))
    box(870, 400, 360, 130, "Site Editor", "SiteEditService", (236, 253, 245), (5, 150, 105))
    arrow(330, 275, 418, 275)
    arrow(780, 275, 868, 275)
    arrow(1230, 275, 1318, 275)
    d.line([(600, 200), (600, 152)], fill=(5, 150, 105), width=5)
    d.polygon([(600, 150), (589, 168), (611, 168)], fill=(5, 150, 105))
    d.text((612, 160), "yes", font=b, fill=(5, 150, 105))
    d.line([(1050, 350), (1050, 398)], fill=(5, 150, 105), width=5)
    d.polygon([(1050, 400), (1039, 382), (1061, 382)], fill=(5, 150, 105))
    d.text((1062, 358), "yes", font=b, fill=(5, 150, 105))
    d.text((790, 238), "no", font=b, fill=(100, 116, 139))
    d.text((1240, 238), "no", font=b, fill=(100, 116, 139))
    img.save(path)


# ---------------------------------------------------------------- docx helpers

def shade(cell, hex_color):
    tc_pr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), hex_color)
    tc_pr.append(shd)


def repeat_header(row):
    tr_pr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:tblHeader")
    el.set(qn("w:val"), "true")
    tr_pr.append(el)


def table(doc, header, rows, widths=None, mono_cols=(), size=9.5):
    t = doc.add_table(rows=1, cols=len(header))
    t.style = "Table Grid"
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    repeat_header(t.rows[0])
    for i, h in enumerate(header):
        c = t.rows[0].cells[i]
        c.text = ""
        r = c.paragraphs[0].add_run(h)
        r.bold = True
        r.font.size = Pt(size)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        shade(c, "4338CA")
    for n, row in enumerate(rows):
        cells = t.add_row().cells
        for i, val in enumerate(row):
            cells[i].text = ""
            r = cells[i].paragraphs[0].add_run(str(val))
            r.font.size = Pt(size - 0.5 if i in mono_cols else size)
            if i in mono_cols:
                r.font.name = "Consolas"
            if n % 2 == 1:
                shade(cells[i], "F8FAFC")
    if widths:
        t.autofit = False
        for i, wd in enumerate(widths):
            t.columns[i].width = Inches(wd)
        for row in t.rows:
            for i, wd in enumerate(widths):
                row.cells[i].width = Inches(wd)
    doc.add_paragraph()
    return t


def code(doc, text, fill="F1F5F9", size=9):
    t = doc.add_table(rows=1, cols=1)
    t.style = "Table Grid"
    c = t.rows[0].cells[0]
    shade(c, fill)
    c.text = ""
    r = c.paragraphs[0].add_run(text.strip("\n"))
    r.font.name = "Consolas"
    r.font.size = Pt(size)
    doc.add_paragraph()


def prompt_box(doc, text):
    code(doc, text, fill="EEF2FF", size=9.5)


def para(doc, text, bold_lead=None, italic=False, color=None):
    p = doc.add_paragraph()
    if bold_lead:
        p.add_run(bold_lead).bold = True
    r = p.add_run(text)
    r.italic = italic
    if color:
        r.font.color.rgb = color
    return p


def note(doc, text):
    return para(doc, text, italic=True, color=GREY)


def warn(doc, text):
    p = doc.add_paragraph()
    r = p.add_run("Watch out: ")
    r.bold = True
    r.font.color.rgb = RED
    p.add_run(text)


def say(doc, text):
    p = doc.add_paragraph()
    r = p.add_run("What to say: ")
    r.bold = True
    r.font.color.rgb = ACCENT
    p.add_run(text).italic = True


def bullets(doc, items):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        if isinstance(item, tuple):
            p.add_run(item[0]).bold = True
            p.add_run(item[1])
        else:
            p.add_run(item)


def numbered(doc, items):
    for n, item in enumerate(items, 1):
        p = doc.add_paragraph()
        p.paragraph_format.left_indent = Inches(0.35)
        p.paragraph_format.first_line_indent = Inches(-0.25)
        p.paragraph_format.space_after = Pt(4)
        p.add_run(f"{n}.  ")
        if isinstance(item, tuple):
            p.add_run(item[0]).bold = True
            p.add_run(item[1])
        else:
            p.add_run(item)


def page_break(doc):
    doc.add_paragraph().add_run().add_break(WD_BREAK.PAGE)


# ---------------------------------------------------------------- content

def build():
    pipeline_png = DOCS / "bot-pipeline.png"
    routing_png = DOCS / "bot-routing.png"
    draw_pipeline(pipeline_png)
    draw_routing(routing_png)

    doc = Document()
    styles = doc.styles
    styles["Normal"].font.name = "Calibri"
    styles["Normal"].font.size = Pt(11)
    for name, size in (("Heading 1", 18), ("Heading 2", 14), ("Heading 3", 12)):
        styles[name].font.color.rgb = ACCENT
        styles[name].font.size = Pt(size)
    for s in doc.sections:
        s.left_margin = s.right_margin = Inches(0.8)
        s.top_margin = s.bottom_margin = Inches(0.8)

    # ------------------------------------------------------------ title
    h = doc.add_heading("BOT", 0)
    h.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = p.add_run("Demo guide, prompt catalogue and code walkthrough")
    r.font.size = Pt(16)
    r.font.color.rgb = GREY
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.add_run("Every prompt in this document was run against the live application and works as described.").italic = True

    doc.add_heading("Contents", 1)
    for line in [
        "1. What BOT is",
        "2. Before the demo",
        "3. How the code is organised",
        "4. How BOT decides what to do with a message",
        "5. The five bots",
        "6. Prompt catalogue (all verified)",
        "7. Files BOT generates - what each one does",
        "8. BOT's own source code - every file explained",
        "9. Demo script for the meeting",
        "10. Drawbacks (found and verified)",
        "11. Enhancements that can be made",
        "12. Questions your manager may ask",
        "13. Technology stack",
    ]:
        doc.add_paragraph(line, style="List Bullet")

    # ------------------------------------------------------------ 1
    page_break(doc)
    doc.add_heading("1. What BOT is", 1)
    para(doc, "BOT is a Spring Boot (Java 17) web application that turns a plain-English request into working "
              "software. A user types something like \"Create a planetarium website called OrionDome\", and five "
              "bots run one after another: a Business Analyst writes the user story, a Java Developer generates "
              "the website code, two tester bots generate unit and functional tests, and a Developer Tester "
              "verifies the result and records the prompt so BOT gets better context over time.")
    para(doc, "BOT can also edit the generated website from chat (\"Switch to dark mode\") and convert code "
              "between 15 programming languages, with 4 ready-made sample programs in every language.")
    bullets(doc, [
        ("Input: ", "an end-user prompt in plain English."),
        ("Output: ", "a user story, a multi-page Java website or a single form page, JUnit and MockMvc tests, a workflow diagram."),
        ("Also: ", "chat-based site editing and code conversion (chat or the /convert page)."),
        ("Runs locally: ", "websites come from built-in templates (no AI needed); code conversion uses a local AI model (Ollama) or OpenAI."),
    ])

    # ------------------------------------------------------------ 2
    doc.add_heading("2. Before the demo", 1)
    numbered(doc, [
        ("Start BOT: ", "in the project folder run  java -jar target/agentic-ai-chatbot-1.0.0.jar  "
                        "(or scripts/run-web.ps1) and open http://localhost:8080."),
        ("Pick the AI model size: ", "this laptop has 16 GB RAM but often only ~2 GB free. The 7B model needs about "
                                     "3 GB free and fails with \"unable to allocate buffer\" otherwise. For the demo, start BOT with "
                                     "the smaller model:  $env:CONVERTER_MODEL=\"qwen2.5-coder:1.5b\"  (conversions then take ~10 seconds)."),
        ("Check the model: ", "http://localhost:8080/api/convert/status should show \"llmAvailable\": true."),
        ("Warm up: ", "run one custom conversion before the meeting so the model is loaded."),
        ("Reset the demo site: ", "click the \"Java music site\" chip so the first thing your manager sees is a clean site."),
        ("Open three tabs: ", "http://localhost:8080 (chat), /convert (converter), /code (generated code)."),
    ])

    # ------------------------------------------------------------ 3
    doc.add_heading("3. How the code is organised", 1)
    para(doc, "The pipeline every new-website request goes through:")
    doc.add_picture(str(pipeline_png), width=Inches(6.9))
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    para(doc, "All Java source lives in src/main/java/com/agentic/chatbot/, split into six packages:")
    table(doc, ["Package", "Responsibility"], [
        ["web", "Pages and REST APIs the browser talks to (chat, converter, code viewer, generated-site preview, editor)."],
        ["agent", "The five bots and the PipelineOrchestrator that runs them in order."],
        ["generator", "Writes the actual Java, HTML and test files; detects topic, page type and theme."],
        ["service", "Business logic: conversion, sample library, chat edits, styles, chat history, training memory, system prompt."],
        ["llm", "Connections to AI models: OpenAI and local Ollama."],
        ["model", "Plain data objects passed between layers (requests, responses, logs)."],
    ], widths=[1.1, 5.8])

    # ------------------------------------------------------------ 4
    doc.add_heading("4. How BOT decides what to do with a message", 1)
    para(doc, "Every chat message goes to PipelineApiController (POST /api/pipeline), which checks three things in this order:")
    doc.add_picture(str(routing_png), width=Inches(6.9))
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    numbered(doc, [
        ("Is it a code conversion? ", "Starts with convert / translate / port / rewrite / show / give me and names a target "
                                      "language (\"... to Java\", \"... in COBOL\")."),
        ("Is it an edit? ", "Only if a site already exists. Words like change, update, make it, switch to, use, a colour "
                            "name, font, image, theme or dark mode in a short message."),
        ("Otherwise a new website: ", "the five-bot pipeline runs."),
    ])
    warn(doc, "because edits are checked before new websites, a short new-site prompt that mentions a colour "
              "(\"... blue colors, Poppins font.\") is treated as an edit of the old site. Start new-site prompts with "
              "\"As a ..., I want ...\" or keep colours out of short prompts (see Drawbacks).")

    # ------------------------------------------------------------ 5
    doc.add_heading("5. The five bots", 1)
    para(doc, "Every bot implements the same two-method interface, so the orchestrator treats them identically:")
    code(doc, """
public interface AgentBot {
    String name();
    AgentResult run(AgentContext context);
}""")
    para(doc, "AgentContext is the shared notebook passed from bot to bot (prompt, refined story, output folder, "
              "system prompt). AgentResult says whether the bot succeeded and which files it wrote.")
    table(doc, ["#", "Bot (class)", "What it does", "Files it writes"], [
        ["1", "Business Analyst\n(BusinessAnalystBot)",
         "Turns the prompt into a user story with acceptance criteria. Uses OpenAI if a key is set; otherwise a "
         "template that picks a persona (restaurant owner, traveler, music student, wedding couple, end user).",
         "USER_STORY.md"],
        ["2", "Java Developer\n(UiGeneratorBot)",
         "Calls ScreenCodeGenerator: detects topic, brand and page type, then writes a 7-page website or a single "
         "form page with matching theme, colours, fonts and images.",
         "Controller, Form DTO, HTML templates, pom.xml, SITE_STYLE.json, SITE_META.json"],
        ["3", "Java Unit Tester\n(UnitTestBot)", "Generates a JUnit 5 test class for the controller.", "<Name>ControllerTest.java"],
        ["4", "Functional Testing\n(FunctionalTestBot)", "Generates MockMvc tests that open and submit the page like a browser.",
         "<Name>FunctionalTest.java"],
        ["5", "Developer Tester\n(ModelTrainerBot)",
         "Checks a user story and Java code exist, saves the prompt to the training memory, writes the workflow "
         "diagram and a model snapshot.", "WORKFLOW.md, BOT_MODEL.json"],
    ], widths=[0.3, 1.5, 3.4, 1.7])
    para(doc, "The heart of PipelineOrchestrator.run() (simplified):")
    code(doc, """
steps.add(execute(businessAnalystBot, context));   // 1) user story
if (failed) return fail("Business Analyst bot failed");
steps.add(execute(uiGeneratorBot, context));       // 2) Java website code
if (failed) return fail("Java Developer bot failed");
if (request.isRunUnitTests())       steps.add(execute(unitTestBot, context));        // 3)
if (request.isRunFunctionalTests()) steps.add(execute(functionalTestBot, context));  // 4)
steps.add(execute(modelTrainerBot, context));      // 5) verify + train
response.setPreviewUrl(previewUrl);  response.setCodeUrl("/code");""")
    para(doc, "", bold_lead="How BOT \"trains\":")
    bullets(doc, [
        ("BotModelTrainingService ", "saves each prompt (up to 200) in data/bot-training.json. State: untrained (0), "
                                     "warming-up (1-4), training (5+)."),
        ("SystemPromptService ", "builds the AI instructions from BOT's base rules + the user's system prompt from the "
                                 "home page + the model state + the 5 most recent prompts."),
    ])
    note(doc, "This is in-context learning: the AI receives better examples and instructions. It does not retrain the "
              "model's weights. Note that the system prompt only reaches an AI when OpenAI is configured.")

    # ------------------------------------------------------------ 6
    page_break(doc)
    doc.add_heading("6. Prompt catalogue (all verified)", 1)
    note(doc, "Each prompt below was sent to the running application on 13 Oct 2026 and produced the stated result.")

    doc.add_heading("6.1 Multi-page websites (7 pages + contact form)", 2)
    para(doc, "A topic word makes BOT generate a full site with Home, About, Services, Pricing, Blog, Careers, Contact "
              "and a contact-success page (13 files). The topic also chooses the theme: images, colours and font.")
    table(doc, ["Theme", "Words that trigger it", "Verified prompt"], [
        ["Music", "music, piano, guitar, violin, concert, choir, orchestra, musician",
         "As a music teacher, I want a website called HarmonyNotes for piano and guitar lessons"],
        ["Restaurant", "restaurant, cafe, dining, food, menu, bistro, chef",
         "As a restaurant owner, I want a multi-page website called OliveGrove with menu, pricing and reservations pages"],
        ["Restaurant", "(cafe)", "Create a cafe website called BeanBox"],
        ["Travel", "travel, tour, hotel, resort, vacation, beach, surf",
         "As a traveler, I want a website called SkyTrail for tours and hotel packages"],
        ["Travel", "(hotel)", "Create a hotel website called SeaBreeze"],
        ["Wedding", "wedding, bridal, bride, groom", "As a couple, I want a wedding planning website called EverAfter"],
        ["Fitness", "gym, fitness, workout, yoga", "As a gym owner, I want a fitness website called IronPeak"],
        ["Pets", "pet, dog, cat, puppy, kitten, vet", "As a pet owner, I want a pet boarding website called PawHaven"],
        ["Books", "bookstore, library, books, reading", "Build a multi-page bookstore website called PageTurner"],
        ["Garden", "garden, nursery, plant, florist, flower", "Create a garden nursery website called GreenLeaf"],
        ["Gaming", "gaming, esport, gamer, console", "Create an esports gaming website called NeonRift"],
        ["Space", "planetarium, astronomy, space, galaxy", "Create a planetarium website called OrionDome"],
        ["Cleaning", "cleaning, cleaner, maid, housekeeping", "Create a cleaning services website called LeafShine"],
        ["Hospital", "hospital, clinic (doctor, medical for theme)", "Create a hospital website called CarePlus"],
        ["Bank", "bank, finance, loan, insurance + \"company website\"", "Build a company website for a bank called TrustOne"],
        ["Tech (default)", "full website, company website, multi-page, landing", "Build a full website for a software startup called NovaCloud"],
    ], widths=[1.0, 2.2, 3.7], mono_cols=(2,), size=9)
    para(doc, "Home-page chips (one click, also verified):")
    for chip in [
        "Java restaurant site - As a Business Analyst, generate a full multi-page restaurant website called OliveGrove in Java ... Use warm orange colors and Playfair font.",
        "Java travel site - Generate Java website code for a travel company called SkyTrail Travels: Spring Boot controllers, Thymeleaf pages ... blue colors, Poppins font, and travel-themed images.",
        "Java music site - Generate Java code for a music academy website called Harmony Notes (Spring Boot + Thymeleaf) ...",
        "Java wedding site - As a Java developer prompt: build a wedding planning website called EverAfter ...",
    ]:
        doc.add_paragraph(chip, style="List Bullet")

    doc.add_heading("6.2 Single-page forms", 2)
    para(doc, "Without a topic word, BOT builds one form page plus a success page (8 files). The words decide the form type and its fields.")
    table(doc, ["Page type", "Trigger words", "Fields generated", "Verified prompt"], [
        ["Login", "login, sign in, authenticate", "Email or phone, Password",
         "As a user, I want a login page that redirects to the dashboard"],
        ["Registration", "register, sign up, create an account", "Full name, Email, Password",
         "As a new user, I want a registration page so I can create an account"],
        ["Appointment", "appointment, booking, schedule, doctor", "Name, Date, Time, Reason",
         "As a patient, I want an appointment booking page with a doctor"],
        ["Checkout", "cart, checkout, payment, order, shop, buy", "Name, Address, Card/UPI, Phone",
         "As a shopper, I want a checkout page to pay for my order"],
        ["Catalog", "catalog, product list, browse, search", "Search, Category",
         "As a customer, I want a product catalog page to browse items"],
        ["HR request", "leave, hr, employee, onboarding", "Employee, Request type, From date, Notes",
         "As an employee, I want a leave request form for HR"],
        ["Support", "ticket, support, contact, feedback", "Name, Email, Subject, Message",
         "As a support agent, I want a contact support page for customer complaints"],
        ["Money transfer", "bank, transfer, loan, finance", "From, To/UPI, Amount, Remarks",
         "As a customer, I want a money transfer page for my bank"],
        ["Dashboard", "dashboard, workspace, portal", "Name, Email, Details, Notes",
         "As a manager, I want a dashboard page for my team"],
    ], widths=[1.0, 1.6, 1.6, 2.7], mono_cols=(3,), size=9)
    para(doc, "Redirects: phrases like \"redirects to the dashboard\", \"go to the profile page\" or \"navigate to the "
              "receipt\" create the destination page too. Login and registration go to /dashboard by default.")

    doc.add_heading("6.3 Tips for writing website prompts", 2)
    bullets(doc, [
        ("Name the brand: ", "\"called OliveGrove\" or \"named PawHaven\". Use one word in CamelCase - "
                             "\"called Harmony Notes\" becomes just \"Harmony\"."),
        ("Start with \"As a ..., I want ...\": ", "this is always treated as a new website, never as an edit."),
        ("Avoid colour words in short prompts ", "when a site already exists - they are read as edits."),
        ("Topic words win over form words: ", "\"hotel check-in form\" builds a full hotel website, and \"clinic\" builds "
                                              "a hospital website; use \"doctor\" for a booking form."),
    ])

    doc.add_heading("6.4 Editing the generated website", 2)
    para(doc, "Run these after a site exists. Each one was verified on the music site.")
    table(doc, ["Prompt", "What changes"], [
        ["Change the website color to blue and use Poppins font", "Accent colour blue (#1565c0), font Poppins"],
        ["Make it dark green", "Accent colour green (#2e7d32)"],
        ["Change the website color to orange", "Accent colour orange (#ef6c00)"],
        ["Use a pink theme with Playfair font", "Pink accent and background, Playfair Display font"],
        ["Switch to dark mode", "Dark background (#0f172a), light text"],
        ["Change the accent color to #0f766e", "Any exact hex colour"],
        ["Use a larger font  /  Use a smaller font", "Body text 18px / 14px"],
        ["Change the images to music photos", "Re-generates pages with theme images in every card"],
        ["Change the image to image: https://images.unsplash.com/photo-...", "Uses your own image URL in the hero and cards"],
    ], widths=[3.4, 3.5], mono_cols=(0,), size=9)
    para(doc, "Supported colour names: red, blue, green, orange, purple, violet, teal, turquoise, pink, gold, yellow, "
              "black, white. Fonts: Poppins, Playfair (or \"serif\"), Montserrat, Roboto, Lato, Open Sans.")
    warn(doc, "\"Make the font larger\" does nothing - the code only recognises \"larger font\", \"bigger font\" or "
              "\"increase font\". Also, edits do not stack: each colour edit starts again from the original theme.")
    para(doc, "There is also a visual editor at http://localhost:8080/edit (colours, fonts, logo, hero image, "
              "background upload, footer, Google sign-in button).")

    doc.add_heading("6.5 Code conversion in chat - built-in samples (instant, offline)", 2)
    para(doc, "Samples: Hello World, Factorial, Binary Search, FizzBuzz. If no source language is given, the Java version is used.")
    table(doc, ["Verified prompt", "Result"], [
        ["Show FizzBuzz in COBOL", "Java -> COBOL"],
        ["Translate binary search from Java to Rust", "Java -> Rust"],
        ["Show Hello World in PL/SQL", "Java -> PL/SQL"],
        ["Show factorial in Go", "Java -> Go"],
        ["Show me binary search in Kotlin", "Java -> Kotlin"],
        ["Convert factorial from Python to C#", "Python -> C#"],
        ["Give me FizzBuzz in Swift", "Java -> Swift"],
        ["Can you show Hello World in TypeScript?", "Java -> TypeScript"],
        ["Please translate factorial from Java to Ruby", "Java -> Ruby"],
        ["Show binary search in C++", "Java -> C++"],
        ["Show FizzBuzz in PHP", "Java -> PHP"],
        ["Convert Hello World to JavaScript", "Java -> JavaScript"],
        ["Show Hello World in golang", "Java -> Go (alias)"],
    ], widths=[4.2, 2.7], mono_cols=(0,), size=9)
    warn(doc, "\"Show factorial in mainframe COBOL\" is NOT recognised (only exact language names or aliases work) - "
              "it falls through and generates a website instead.")

    doc.add_heading("6.6 Code conversion in chat - your own code (local AI)", 2)
    para(doc, "Put the instruction on the first line, then the code. The source language can be named or detected automatically. "
              "All three were verified with qwen2.5-coder:1.5b (8-14 seconds each).")
    prompt_box(doc, "Convert this Python code to Java:\n"
                    "def average(nums):\n    if not nums:\n        return 0\n    return sum(nums) / len(nums)\n\n"
                    "print(average([4, 8, 15, 16, 23, 42]))")
    prompt_box(doc, "Translate this JavaScript code into Go:\n"
                    "function greet(name) {\n  return 'Hello, ' + name + '!';\n}\nconsole.log(greet('Team'));")
    prompt_box(doc, "Convert to Rust:\n"
                    "def is_even(n):\n    return n % 2 == 0\n\nprint(is_even(10))")
    note(doc, "The last one has no source language - BOT detected Python by itself.")
    table(doc, ["Language", "Names you can type"], [
        ["Java", "java, jdk"], ["Python", "python, py, python3"], ["JavaScript", "javascript, js, node, nodejs"],
        ["TypeScript", "typescript, ts"], ["C#", "c#, csharp, cs, c sharp, .net, dotnet"], ["C++", "c++, cpp, cplusplus"],
        ["C", "c, ansi c, c99"], ["Go", "go, golang"], ["Rust", "rust, rs"], ["Kotlin", "kotlin, kt"],
        ["Swift", "swift"], ["PHP", "php"], ["Ruby", "ruby, rb"],
        ["PL/SQL", "plsql, pl/sql, sql, oracle, oracle sql"], ["COBOL", "cobol, cbl, mainframe, z/os"],
    ], widths=[1.4, 5.5], mono_cols=(1,), size=9)

    doc.add_heading("6.7 The converter page (http://localhost:8080/convert)", 2)
    numbered(doc, [
        "Choose From and To languages (or Auto-detect), paste code or Load sample, click Convert (Ctrl+Enter).",
        "Swap reverses the direction; Copy and Download save the result.",
        "Under the result: an explanation of the differences and the command to run the code.",
        "\"Sample programs in every language\" gallery: view any of the 60 samples and click Use as source.",
    ])

    # ------------------------------------------------------------ 7
    page_break(doc)
    doc.add_heading("7. Files BOT generates - what each one does", 1)
    para(doc, "All output goes to the generated-app/ folder. Example names below are for the music site "
              "(MusicAcademy) and the appointment form (AppointmentBooking). In the table, java/ means "
              "src/main/java/com/generated/, templates/ means src/main/resources/templates/ and test/ means "
              "src/test/java/com/generated/.")
    table(doc, ["File", "Written by", "What it does"], [
        ["USER_STORY.md", "Bot 1", "The original prompt and the refined user story with acceptance criteria."],
        ["java/app/GeneratedAppApplication.java", "Bot 2",
         "Spring Boot entry point (main method) for the generated app; scans all com.generated packages."],
        ["java/musicacademy/MusicAcademyController.java", "Bot 2 (site)",
         "Maps the website URLs: / and /home, /about, /services, /pricing, /blog, /careers, /contact; handles the "
         "contact form POST and redirects to /contact-success."],
        ["java/appointmentbooking/AppointmentBookingController.java", "Bot 2 (form)",
         "GET shows the form page; POST receives the submitted fields and redirects to the success or destination page."],
        ["java/appointmentbooking/AppointmentBookingForm.java", "Bot 2 (form)",
         "Data object (DTO) holding the four form fields (field1-field4) with getters and setters."],
        ["templates/home.html, about.html, services.html, pricing.html, blog.html, careers.html, contact.html",
         "Bot 2 (site)",
         "The seven Thymeleaf pages of the website with navigation, hero image, themed cards, colours and fonts."],
        ["templates/contact-success.html", "Bot 2 (site)", "Confirmation page shown after the contact form is sent."],
        ["templates/appointment-booking.html", "Bot 2 (form)", "The form page with the fields for the detected page type."],
        ["templates/appointment-booking-success.html", "Bot 2 (form)", "Confirmation page after submitting the form."],
        ["pom.xml", "Bot 2", "Maven build file for the generated app (Spring Boot web, Thymeleaf, test dependencies)."],
        ["src/main/resources/application.yml", "Bot 2", "Configuration for the generated app."],
        ["SITE_STYLE.json", "Bot 2 / edits", "Title, colours, font, sizes, hero image. Edits and the visual editor change this file."],
        ["SITE_META.json", "Bot 2", "Preview URL, main page path, redirect target and page list - used by BOT to show the preview."],
        ["test/musicacademy/MusicAcademyControllerTest.java", "Bot 3",
         "JUnit 5 + MockMvc: checks the page returns 200 with the right view, and that submitting the form causes no server error."],
        ["test/musicacademy/MusicAcademyFunctionalTest.java", "Bot 4",
         "End-to-end MockMvc tests: user can open the screen and submit the main form flow."],
        ["WORKFLOW.md", "Bot 5", "Mermaid workflow diagram of the five bots plus a short description of each."],
        ["BOT_MODEL.json", "Bot 5", "Snapshot: training state, prompt count, the dynamic system prompt, lists of Java and HTML files."],
        ["conversions/converted_output.<ext>", "Converter", "The latest converted code for each target language (e.g. .java, .py, .cbl)."],
        ["uploads/", "Visual editor", "Logo, hero and background images uploaded in /edit."],
    ], widths=[2.5, 0.9, 3.5], mono_cols=(0,), size=8.5)

    # ------------------------------------------------------------ 8
    page_break(doc)
    doc.add_heading("8. BOT's own source code - every file explained", 1)
    note(doc, "Paths are relative to src/main/java/com/agentic/chatbot/ unless shown otherwise.")

    doc.add_heading("8.1 Application and agents", 2)
    table(doc, ["File", "What it does"], [
        ["AgenticChatbotApplication.java", "Spring Boot entry point - starts the web server on port 8080."],
        ["agent/AgentBot.java", "Two-method interface every bot implements: name() and run(context)."],
        ["agent/AgentContext.java", "Shared data passed between bots: prompt, refined story, output folder, API key, system prompts, generated files."],
        ["agent/AgentResult.java", "Result of one bot: success flag, summary text, files written. ok() / fail() helpers."],
        ["agent/PipelineOrchestrator.java", "Runs the 5 bots in order, stops on critical failure, times each step, builds the response (preview link, code link, user story, file list)."],
        ["agent/BusinessAnalystBot.java", "Bot 1. Refines the prompt into a user story (OpenAI if configured, else template with persona). Writes USER_STORY.md."],
        ["agent/UiGeneratorBot.java", "Bot 2. Calls ScreenCodeGenerator to write the website code."],
        ["agent/UnitTestBot.java", "Bot 3. Calls UnitTestGenerator to write JUnit tests."],
        ["agent/FunctionalTestBot.java", "Bot 4. Calls FunctionalTestGenerator to write MockMvc tests."],
        ["agent/ModelTrainerBot.java", "Bot 5. Verifies story and code exist, records the prompt for training, writes WORKFLOW.md and BOT_MODEL.json."],
        ["config/PipelineCommandLineRunner.java", "Script mode: java -jar ... --story=\"...\" runs the pipeline from the command line (--skip-unit, --skip-functional, --exit)."],
        ["config/WebConfig.java", "Serves uploaded images from generated-app/uploads at /uploads/**."],
    ], widths=[2.4, 4.5], mono_cols=(0,), size=9)

    doc.add_heading("8.2 Generators (write the website and tests)", 2)
    table(doc, ["File", "What it does"], [
        ["generator/StoryAnalyzer.java", "Reads the prompt and decides the domain (login, appointment, checkout, landing ...), page type, feature name, Java package, URL and redirect target."],
        ["generator/ScreenCodeGenerator.java", "Main code writer. Uses OpenAI if configured; otherwise decides between a 7-page site and a single form page, and writes controller, DTO, templates, pom.xml, SITE_STYLE.json and SITE_META.json. Also picks the brand name."],
        ["generator/BigSiteTemplates.java", "HTML for the 7-page website (home, about, services, pricing, blog, careers, contact) with themed cards and images."],
        ["generator/CompanyWebTemplates.java", "HTML for single form pages (login, registration, booking, checkout ...) and confirmation pages."],
        ["generator/SiteThemePack.java", "14 themes (music, restaurant, travel ...): images, colours, fonts and copy. Also applies colour/font/dark-mode changes from edit prompts."],
        ["generator/UnitTestGenerator.java", "Writes the JUnit 5 controller test for the generated page."],
        ["generator/FunctionalTestGenerator.java", "Writes the MockMvc functional test that opens and submits the page."],
    ], widths=[2.4, 4.5], mono_cols=(0,), size=9)

    doc.add_heading("8.3 Services (business logic)", 2)
    table(doc, ["File", "What it does"], [
        ["service/CodeConversionService.java", "Converts code: understands chat instructions, detects the source language, uses the sample library first and the AI model otherwise, saves converted_output.<ext>."],
        ["service/CodeSampleLibrary.java", "The 15 languages (names, aliases, file extension, run command) and loads the 60 sample programs from resources/code-samples."],
        ["service/SiteEditService.java", "Decides whether a message is an edit and applies it: colours, fonts, dark mode, font size, images, custom image URL."],
        ["service/SiteStyleService.java", "Reads and writes SITE_STYLE.json and stores uploaded images."],
        ["service/ChatHistoryService.java", "Saves every chat (prompt + result) to data/chat-history.json for the history sidebar."],
        ["service/BotModelTrainingService.java", "Training memory: stores prompts in data/bot-training.json and reports untrained / warming-up / training."],
        ["service/SystemPromptService.java", "Builds the dynamic system prompt from base rules, user system prompt, model state and recent prompts."],
    ], widths=[2.4, 4.5], mono_cols=(0,), size=9)

    doc.add_heading("8.4 AI model clients", 2)
    table(doc, ["File", "What it does"], [
        ["llm/LlmClient.java", "Interface for any AI model: isAvailable(), complete(system, user), describe()."],
        ["llm/OpenAiLlmClient.java", "Calls the OpenAI Chat Completions API when OPENAI_API_KEY is set (model gpt-4o-mini by default)."],
        ["llm/OllamaLlmClient.java", "Calls a local Ollama server's chat API."],
        ["llm/LocalLlmDetector.java", "Finds a running Ollama, picks the largest installed coding model under 10 GB (or CONVERTER_MODEL), re-checks every 30 seconds."],
    ], widths=[2.4, 4.5], mono_cols=(0,), size=9)

    doc.add_heading("8.5 Web controllers (URLs)", 2)
    table(doc, ["File", "URLs", "What it does"], [
        ["web/ChatController.java", "/  /run  /results", "Home chat page with chips and system prompt box; form-based run and results page."],
        ["web/PipelineApiController.java", "POST /api/pipeline", "Main chat API: routes to converter, site editor or the 5-bot pipeline; saves history."],
        ["web/CodeViewerController.java", "/code", "Shows the user story, workflow diagram and every generated file with its code."],
        ["web/GeneratedSiteController.java", "/site/{page}", "Previews the generated pages inside BOT with the saved style; handles form submits and redirects."],
        ["web/SiteEditorController.java", "/edit  /edit/{page}", "Visual editor: colours, fonts, text, logo/hero/background upload, footer, Google button."],
        ["web/CodeConverterController.java", "/convert", "The converter page with languages, samples and gallery."],
        ["web/CodeConversionApiController.java", "/api/convert, /languages, /samples, /status", "REST API for conversion, language list, samples and AI status."],
        ["web/ChatHistoryApiController.java", "/api/history", "List, open, delete and clear chat history."],
        ["web/GoogleAuthController.java", "/auth/google ...", "Google sign-in for generated login pages; demo sign-in when no Google client ID is configured."],
    ], widths=[2.2, 1.6, 3.1], mono_cols=(0, 1), size=8.5)

    doc.add_heading("8.6 Data models", 2)
    table(doc, ["File", "What it holds"], [
        ["model/PipelineRequest.java", "Prompt, system prompt, run-unit-tests and run-functional-tests flags."],
        ["model/PipelineResponse.java", "Success, message, bot steps, preview URL, code URL, user story, generated files, conversion result."],
        ["model/BotStepLog.java", "One bot's result: name, success, summary, files, duration in ms."],
        ["model/ChatHistoryEntry.java", "One saved chat: id, time, prompt, response, conversion."],
        ["model/SiteStyle.java", "Website look: title, heading, colours, font, sizes, logo/hero/background images, footer."],
        ["model/ConversionRequest.java / ConversionResponse.java", "Converter input (source, target, code, instructions) and output (code, explanation, run command, file)."],
        ["model/ProgrammingLanguage.java", "Language id, display name, extension, run command, aliases."],
        ["model/CodeSample.java", "Sample id, title, description, and its code in every language."],
    ], widths=[2.6, 4.3], mono_cols=(0,), size=9)

    doc.add_heading("8.7 Pages, styles, resources, tests and scripts", 2)
    table(doc, ["File", "What it does"], [
        ["resources/templates/index.html", "Chat home page: bot workflow, example chips, system prompt box, results with links."],
        ["resources/templates/code.html", "Code viewer page (user story banner, workflow, file list, code)."],
        ["resources/templates/convert.html", "Converter page with From/To, swap, samples, copy/download and gallery."],
        ["resources/templates/editor.html", "Visual site editor form."],
        ["resources/templates/google-demo.html", "Demo Google account chooser for local sign-in."],
        ["resources/static/css/*.css", "Styles: style.css (chat), code.css, convert.css, editor.css, workflow.css."],
        ["resources/code-samples/<sample>/<lang>.<ext>", "60 hand-written sample programs (4 samples x 15 languages)."],
        ["resources/application.yml", "Settings: port, output folder, data files, OpenAI, Ollama converter, Google sign-in."],
        ["src/test/java/... (5 classes, 27 tests)", "StoryAnalyzerTest, CodeConversionServiceTest, CodeSampleLibraryTest, LocalLlmDetectorTest, OpenAiLlmClientTest."],
        ["scripts/run-web.ps1", "Starts BOT on Windows."],
        ["scripts/run-pipeline.ps1", "Runs the pipeline once from the command line."],
        ["scripts/run-generated-tests.ps1", "Runs mvn test inside generated-app."],
        ["pom.xml, README.md, .env.example", "BOT's Maven build, project readme, example environment variables."],
        ["data/chat-history.json, data/bot-training.json", "Saved chats and training memory."],
        ["poster/, docs/", "QR-code poster and this document (with the Python scripts that build them)."],
    ], widths=[2.6, 4.3], mono_cols=(0,), size=9)

    # ------------------------------------------------------------ 9
    page_break(doc)
    doc.add_heading("9. Demo script for the meeting (about 12 minutes)", 1)
    steps = [
        ("Generate a website (3 min) - http://localhost:8080",
         "Click the \"Java music site\" chip, or type: As a music teacher, I want a website called HarmonyNotes for piano and guitar lessons",
         "One sentence in, five bots run in sequence. The Business Analyst wrote the story, the Java Developer the site, two testers the tests. Notice the images match the topic.",
         "Open website, then User stories + Java code."),
        ("Show the code (2 min) - http://localhost:8080/code",
         None,
         "This is a standard Spring Boot project: controller, Thymeleaf pages, tests and a pom.xml.",
         "User story at the top, workflow diagram, click the controller, a page and a test."),
        ("Edit in plain English (1 min)",
         "Switch to dark mode    then    Use a pink theme with Playfair font",
         "BOT recognises an edit and updates the existing site instead of starting over.",
         "Open website again."),
        ("A different kind of page (1 min)",
         "As a user, I want a login page that redirects to the dashboard",
         "Without a topic word BOT builds a single form page, here a login that goes to a dashboard.",
         None),
        ("Convert code in chat (2 min)",
         "Show FizzBuzz in COBOL    then paste the \"Convert this Python code to Java\" example (6.6)",
         "Samples are instant and offline. Custom code goes to a local AI model, so code never leaves the machine.",
         None),
        ("Converter page (2 min) - http://localhost:8080/convert",
         "Java -> Python, Load sample FizzBuzz, Convert, Swap, then the gallery",
         "Useful for legacy modernisation (COBOL, PL/SQL to Java) and onboarding to new languages.",
         None),
        ("Wrap-up (1 min)",
         None,
         "BOT remembers each prompt and feeds recent ones back as context. Next steps are in the enhancements list.",
         "QR poster (poster/bot-poster.png)."),
    ]
    for n, (title, prompt, talk, show) in enumerate(steps, 1):
        doc.add_heading(f"Step {n} - {title}", 3)
        if prompt:
            prompt_box(doc, prompt)
        say(doc, talk)
        if show:
            para(doc, show, bold_lead="Show: ")

    # ------------------------------------------------------------ 10
    page_break(doc)
    doc.add_heading("10. Drawbacks (found and verified)", 1)
    note(doc, "Each item below was reproduced on the running application or confirmed in the code.")
    table(doc, ["#", "Drawback", "Example / evidence", "Impact"], [
        ["1", "Topic detection matches parts of words",
         "\"Create an education website\" gets PET images, because \"education\" contains \"cat\". Same for \"application\", \"competition\" (pet).",
         "Wrong images and theme"],
        ["2", "Short new-site prompts with colours are treated as edits",
         "\"Generate Java website code for a travel company ... blue colors, Poppins font.\" (144 chars) only recoloured the old site.",
         "No new site generated"],
        ["3", "Edits do not stack", "After \"Switch to dark mode\", the next edit resets the colours to the original theme.",
         "Users must repeat edits"],
        ["4", "Font-size wording is strict", "\"Make the font larger\" does nothing; only \"larger/bigger/increase font\" works.",
         "Edit silently ignored"],
        ["5", "Topic words override page type", "\"hotel check-in form\" builds a full hotel website; \"clinic\" builds a hospital site.",
         "Wrong kind of output"],
        ["6", "Brand names are one word", "\"called Harmony Notes\" shows \"Harmony\"; form pages use generic titles.",
         "Branding looks off"],
        ["7", "generated-app/ is never cleaned", "Controllers from earlier runs remain; several map /home, so the generated project would fail to start on its own (duplicate URL mappings).",
         "Generated project not runnable as-is"],
        ["8", "Generated tests do not match multi-page sites", "Tests call /music-academy, but the site only has /home, /about ...; tests are generated but never executed by the pipeline.",
         "Tests would fail if run"],
        ["9", "Business Analyst story is not used by the developer bot", "UiGeneratorBot generates from the original prompt, not the refined story.",
         "BA step has no effect on code"],
        ["10", "Website generation is template-based without OpenAI", "Local Ollama is only used by the converter; the BA, developer and system prompt need OpenAI.",
         "\"Training\" has no effect offline"],
        ["11", "Local AI model needs free memory", "qwen2.5-coder:7b failed with \"unable to allocate CPU buffer\" when ~1.8 GB RAM was free.",
         "Custom conversion fails"],
        ["12", "Language names must be exact", "\"mainframe COBOL\" is not recognised and falls through to website generation.",
         "Unexpected website"],
        ["13", "Generic form fields", "Form DTOs use field1-field4 instead of real names (email, date ...).",
         "Code needs rework"],
        ["14", "Single user, no security", "No login for BOT itself, one shared output folder, history in JSON files.",
         "Not ready for a team server"],
        ["15", "Converted output is overwritten", "One converted_output file per language; the next conversion replaces it.",
         "Results lost unless downloaded"],
    ], widths=[0.3, 1.7, 3.3, 1.6], size=8.5)

    # ------------------------------------------------------------ 11
    doc.add_heading("11. Enhancements that can be made", 1)
    doc.add_heading("Quick wins (days)", 2)
    table(doc, ["Enhancement", "Fixes drawback"], [
        ["Match whole words in topic and domain detection (regex \\bcat\\b), and add an education theme", "1"],
        ["Check for \"generate / build / create ... website\" before treating a message as an edit", "2"],
        ["Apply edits on top of the current SITE_STYLE.json instead of the original theme", "3"],
        ["Accept more phrasings: \"make the font larger\", \"bigger text\", \"mainframe COBOL\"", "4, 12"],
        ["Let explicit form words (\"form\", \"page\") win over topic words", "5"],
        ["Capture multi-word brand names (\"called Harmony Notes\")", "6"],
        ["Clean generated-app/ (or create a new sub-folder per run) before generating", "7"],
        ["Generate tests from the actual routes, and run mvn test as part of Bot 5", "8"],
        ["Timestamp converted files, or keep a conversion history", "15"],
        ["Automatically fall back to a smaller model when the 7B model cannot load", "11"],
    ], widths=[5.6, 1.3], size=9)
    doc.add_heading("Medium term (weeks)", 2)
    bullets(doc, [
        ("Use local Ollama for all bots: ", "Business Analyst and Java Developer could run on the local model too, "
                                            "making the dynamic system prompt and training useful offline (10)."),
        ("Feed the refined user story to the developer bot ", "so the BA step really shapes the code (9)."),
        ("Real form fields: ", "generate DTOs with named, validated fields (@NotBlank, @Email) instead of field1-4 (13)."),
        ("Database layer: ", "generate JPA entities, repositories and an H2/PostgreSQL configuration from the user story."),
        ("Download as ZIP: ", "one-click download of the generated project, ready to open in an IDE."),
        ("Whole-project conversion: ", "upload a folder or ZIP and convert every file; side-by-side diff view."),
        ("Run converted code: ", "execute samples in a sandbox (Docker) and show the output to prove correctness."),
    ])
    doc.add_heading("Long term", 2)
    bullets(doc, [
        ("Team deployment: ", "user accounts, per-user projects and history in a database, HTTPS, role-based access (14)."),
        ("Real model training: ", "fine-tune a small code model on accepted outputs, or add retrieval (RAG) over past "
                                  "user stories and company coding standards."),
        ("CI/CD integration: ", "push generated projects to a Git repository and run the tests in a pipeline."),
        ("Quality gates: ", "static analysis (SonarQube, Checkstyle) and security scanning of generated code."),
        ("More outputs: ", "React/Angular front ends, REST API-only projects, mobile layouts."),
    ])

    # ------------------------------------------------------------ 12
    doc.add_heading("12. Questions your manager may ask", 1)
    for q, a in [
        ("Is the generated code real?",
         "Yes - Spring Boot controllers, Thymeleaf pages, a pom.xml and JUnit/MockMvc tests. Today it needs cleanup "
         "before it runs standalone (drawbacks 7 and 8)."),
        ("Does it need the internet or a paid API?",
         "No. Websites come from built-in templates and conversion uses a free local model. OpenAI is optional."),
        ("Is our code safe?", "With the local model, code never leaves the machine."),
        ("Does the model really train?",
         "It learns through context (stored prompts become examples in the system prompt). It does not retrain model weights."),
        ("How accurate is the conversion?",
         "Samples are hand-written and exact. AI conversions are good for small programs but should be reviewed."),
        ("How is BOT tested?", "27 automated unit tests, plus it generates tests for every site it builds."),
        ("What would it take to roll out to the team?",
         "The quick wins in section 11, then accounts and a database for multi-user use."),
    ]:
        p = doc.add_paragraph()
        p.add_run("Q: " + q).bold = True
        doc.add_paragraph("A: " + a)

    # ------------------------------------------------------------ 13
    doc.add_heading("13. Technology stack", 1)
    table(doc, ["Area", "Technology"], [
        ["Language / framework", "Java 17, Spring Boot 3.4, Thymeleaf"],
        ["Build", "Maven (target/agentic-ai-chatbot-1.0.0.jar)"],
        ["AI models", "Ollama (qwen2.5-coder 1.5b / 7b, local) or OpenAI gpt-4o-mini (optional)"],
        ["Testing", "JUnit 5, Spring MockMvc"],
        ["Storage", "JSON files in data/ (chat history, training memory)"],
    ], widths=[2.0, 4.9])

    out = DOCS / "BOT_Complete_Guide.docx"
    doc.save(out)
    print("Wrote", out)


if __name__ == "__main__":
    build()
