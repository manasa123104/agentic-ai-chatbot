from openpyxl import Workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.worksheet.datavalidation import DataValidation
from collections import Counter
from datetime import date
from pathlib import Path

wb = Workbook()
ws = wb.active
ws.title = "Future Enhancements"

headers = [
    "ID",
    "Category",
    "Enhancement",
    "Description",
    "Priority",
    "Effort (S/M/L)",
    "Status",
    "Suggested Owner",
    "Dependencies",
    "Acceptance Criteria",
    "Notes",
]

rows = [
    ["FE-001", "Core Pipeline", "Persist pipeline run history",
     "Save every user story, bot logs, and generated file paths to a local DB or JSON store so users can reopen past runs.",
     "High", "M", "Todo", "Backend", "None",
     "User can list past runs and reopen any result from chat UI", "Useful for demos and debugging"],
    ["FE-002", "Core Pipeline", "Parallel bot execution where safe",
     "Run Bot 2 (unit) and Bot 3 (functional) in parallel after Bot 1 finishes generation.",
     "Medium", "M", "Todo", "Backend", "FE-001 optional",
     "Total pipeline time reduced vs sequential on multi-core machines", "Keep Bot 1 sequential"],
    ["FE-003", "Core Pipeline", "Incremental regeneration",
     "Allow regenerating only UI, only tests, or only one page without wiping the whole generated-app.",
     "High", "L", "Todo", "Backend", "None",
     "User can choose regenerate UI / unit / functional independently", "Avoids losing manual edits"],
    ["FE-004", "Core Pipeline", "Story clarification chat",
     "If story is vague, ask 2–3 clarifying questions in the chatbot before generating.",
     "High", "M", "Todo", "Full stack", "LLM optional",
     "Ambiguous stories trigger clarifying Q&A then generation", "Improves output quality"],
    ["FE-005", "UI Generator", "Component library templates",
     "Add reusable header/nav/footer/form/card components so generated sites share consistent design tokens.",
     "High", "M", "Todo", "Frontend gen", "None",
     "Generated pages reuse shared partials and CSS variables", "Reduces template duplication"],
    ["FE-006", "UI Generator", "Responsive + accessibility defaults",
     "Ensure generated HTML includes ARIA labels, keyboard focus styles, and mobile breakpoints.",
     "High", "M", "Todo", "Frontend gen", "FE-005",
     "Lighthouse a11y score > 90 on sample pages", "WCAG-oriented"],
    ["FE-007", "UI Generator", "Image / asset pipeline",
     "Support uploading brand images or fetching placeholder assets into generated pages automatically.",
     "Medium", "M", "Todo", "Full stack", "Site editor",
     "Uploaded logo appears across generated pages", "Extend current image editing"],
    ["FE-008", "UI Generator", "Multi-theme packs",
     "Offer theme packs (Google-like, corporate blue, ecommerce, SaaS dark) selectable before generation.",
     "Medium", "M", "Todo", "Frontend gen", "FE-005",
     "User picks theme; all pages follow pack", "Beyond current color/font editor"],
    ["FE-009", "Big Site", "Sitemap + route map UI",
     "Show a visual sitemap of generated multi-page sites with clickable preview links.",
     "High", "S", "Todo", "Frontend", "BigSiteTemplates",
     "Chat shows page list with /site/* links after big-site generation", "Helps discoverability"],
    ["FE-010", "Big Site", "Cross-page data consistency",
     "Shared nav labels, footer, and brand colors applied across all generated pages automatically.",
     "High", "M", "Todo", "Frontend gen", "FE-005",
     "Changing brand once updates all pages", "Partial today via SiteStyle"],
    ["FE-011", "Big Site", "CRUD page generator",
     "From a story about products/users, generate list + create + edit + detail pages wired together.",
     "High", "L", "Todo", "Backend + gen", "StoryAnalyzer",
     "Story mentioning entities creates 4 related pages + controller routes", "Major feature"],
    ["FE-012", "Testing Bots", "Auto-run tests in chatbot UI",
     "After generation, run Maven tests and stream pass/fail summary into chat bubbles.",
     "High", "M", "Todo", "Backend", "scripts",
     "Chat shows green/red test summary with failing method names", "Currently mostly offline scripts"],
    ["FE-013", "Testing Bots", "Coverage report generation",
     "Produce JaCoCo HTML coverage report for generated unit tests and link it from the UI.",
     "Medium", "M", "Todo", "Backend", "FE-012",
     "Coverage % shown after test run", "Nice for demos"],
    ["FE-014", "Testing Bots", "Playwright / Selenium functional suite",
     "Upgrade Bot 3 from MockMvc-style checks to real browser E2E tests for multi-page flows.",
     "Medium", "L", "Todo", "QA eng", "FE-012",
     "Login → dashboard → settings flow passes in headless browser", "Higher fidelity"],
    ["FE-015", "Testing Bots", "Flaky test detection",
     "Retry failing functional tests once and flag flaky cases in the report.",
     "Low", "S", "Todo", "Backend", "FE-012",
     "Report distinguishes hard fail vs flaky", "Quality of life"],
    ["FE-016", "Auth / Security", "Real Google OAuth2 login",
     "Replace demo Google sign-in with Spring Security OAuth2 client using client ID/secret.",
     "High", "M", "Todo", "Backend", "Google Cloud project",
     "Users can sign in with real Google account and see profile email", "Currently demo-oriented"],
    ["FE-017", "Auth / Security", "Role-based access for generated apps",
     "Generate Spring Security config with USER/ADMIN roles from stories that mention permissions.",
     "Medium", "L", "Todo", "Backend gen", "FE-016",
     "Admin-only routes return 403 for USER role", "Story-aware security"],
    ["FE-018", "Auth / Security", "Secrets management",
     "Move API keys fully to env/.env with validation warnings in UI when missing.",
     "High", "S", "Todo", "Backend", "None",
     "UI warns if OPENAI_API_KEY missing; never commits secrets", ".env.example already exists"],
    ["FE-019", "Chat UX", "Streaming agent progress",
     "Stream Bot 1/2/3 step logs live (SSE/WebSocket) instead of waiting for full pipeline completion.",
     "High", "M", "Todo", "Full stack", "None",
     "User sees live step cards while pipeline runs", "Better perceived speed"],
    ["FE-020", "Chat UX", "Example story gallery",
     "Add curated innovative story cards (banking, hospital, travel, learning) that one-click fill the prompt.",
     "Medium", "S", "Todo", "Frontend", "None",
     "At least 8 examples launch generation correctly", "Marketing + onboarding"],
    ["FE-021", "Chat UX", "Export generated site ZIP",
     "One-click download of generated-app as ZIP from the chat UI.",
     "High", "S", "Todo", "Backend", "None",
     "ZIP contains controllers, templates, tests, pom", "Easy handoff"],
    ["FE-022", "Chat UX", "Inline page preview iframe",
     "Embed /site preview beside chat so users see pages without leaving the chatbot.",
     "Medium", "M", "Todo", "Frontend", "GeneratedSiteController",
     "Preview updates after generation and style edits", "Split-pane UX"],
    ["FE-023", "Site Editor", "Undo / redo style changes",
     "History stack for colors, fonts, footer, and images.",
     "Medium", "M", "Todo", "Frontend", "SiteStyleService",
     "Ctrl+Z restores previous style snapshot", "Editor polish"],
    ["FE-024", "Site Editor", "Live CSS custom properties panel",
     "Expose spacing, radius, and shadow tokens in editor, not only colors/fonts.",
     "Medium", "M", "Todo", "Frontend", "FE-005",
     "Changing radius updates all generated components", "Design system feel"],
    ["FE-025", "Site Editor", "Per-page content editor",
     "Edit headings, body copy, and CTA text per page without regenerating from story.",
     "High", "L", "Todo", "Full stack", "Big site",
     "User edits Home headline; other pages unchanged", "Content CMS-lite"],
    ["FE-026", "LLM", "Provider switch (OpenAI / Azure / local)",
     "Support OpenAI, Azure OpenAI, and local Ollama endpoints from config UI.",
     "Medium", "M", "Todo", "Backend", "LlmClient",
     "User selects provider; generation works offline with Ollama", "Flexibility"],
    ["FE-027", "LLM", "Prompt quality scoring",
     "Score story quality and suggest improvements before generation.",
     "Low", "S", "Todo", "Backend", "LLM optional",
     "Score 1–10 shown with tips", "Helps beginners"],
    ["FE-028", "LLM", "Self-healing failed generation",
     "If compilation/tests fail, feed errors back to LLM and retry generation once.",
     "High", "L", "Todo", "Backend", "FE-012, LLM",
     "Failed compile triggers one automatic repair pass", "Agentic loop"],
    ["FE-029", "DevOps", "Dockerize chatbot + generated apps",
     "Provide Dockerfile and docker-compose for one-command local/demo deploy.",
     "Medium", "M", "Todo", "DevOps", "None",
     "docker compose up serves UI on 8080", "Easier sharing"],
    ["FE-030", "DevOps", "CI pipeline for main project",
     "GitHub Actions: build, unit test, and smoke-test pipeline API on every PR.",
     "High", "S", "Todo", "DevOps", "GitHub repo",
     "PR checks must pass before merge", "Repo already on GitHub"],
    ["FE-031", "DevOps", "Publish generated app as deployable artifact",
     "Optional button to push generated-app to a new GitHub repo or GitHub Pages.",
     "Medium", "L", "Todo", "Full stack", "gh auth",
     "One click creates repo and pushes generated site", "Wow-factor demo"],
    ["FE-032", "Data / Persistence", "SQLite or H2 for styles & history",
     "Replace in-memory SiteStyle and logs with durable storage.",
     "Medium", "M", "Todo", "Backend", "FE-001",
     "Restart keeps styles and history", "Production readiness"],
    ["FE-033", "API", "OpenAPI / Swagger docs",
     "Expose Swagger UI for /api/pipeline and related endpoints.",
     "Low", "S", "Todo", "Backend", "None",
     "Swagger available at /swagger-ui.html", "Developer experience"],
    ["FE-034", "API", "Webhook after pipeline complete",
     "POST results JSON to a user-configured webhook URL when generation finishes.",
     "Low", "S", "Todo", "Backend", "FE-001",
     "Webhook receives success payload with file list", "Integrations"],
    ["FE-035", "Docs", "Architecture diagram + agent sequence",
     "Add Mermaid sequence diagram of Bot1→Bot2→Bot3 to README.",
     "Medium", "S", "Todo", "Docs", "None",
     "README shows clear agent flow", "Onboarding"],
    ["FE-036", "Docs", "Video demo script / sample walkthrough",
     "Short walkthrough: innovative prompt → big site → edit style → Google login → tests.",
     "Low", "S", "Todo", "Docs", "None",
     "5–7 step demo script checked into docs/", "For presentations"],
    ["FE-037", "Product", "Multi-user workspaces",
     "Support multiple named projects/workspaces instead of single generated-app folder.",
     "Medium", "L", "Todo", "Full stack", "FE-032",
     "User can switch between Project A and Project B", "Scales beyond demo"],
    ["FE-038", "Product", "Prompt templates marketplace",
     "Save/share successful user stories as templates others can reuse.",
     "Low", "M", "Todo", "Full stack", "FE-020",
     "User can save current story as template", "Community feature"],
    ["FE-039", "Quality", "Golden-story regression suite",
     "Maintain 10 golden user stories; assert generated routes/pages/tests exist.",
     "High", "M", "Todo", "QA", "FE-030",
     "CI fails if golden stories regress", "Protects quality"],
    ["FE-040", "Quality", "Generated code linting",
     "Run Checkstyle/Spotless on generated Java and HTML validators on templates.",
     "Medium", "S", "Todo", "Backend", "None",
     "Invalid HTML or style violations fail Bot 1", "Cleaner output"],
]

header_fill = PatternFill("solid", fgColor="1F4E79")
header_font = Font(bold=True, color="FFFFFF", name="Calibri", size=11)
alt_fill = PatternFill("solid", fgColor="D6E3F0")
thin = Border(
    left=Side(style="thin", color="B0B0B0"),
    right=Side(style="thin", color="B0B0B0"),
    top=Side(style="thin", color="B0B0B0"),
    bottom=Side(style="thin", color="B0B0B0"),
)
wrap = Alignment(wrap_text=True, vertical="top")
priority_fills = {
    "High": PatternFill("solid", fgColor="F4CCCC"),
    "Medium": PatternFill("solid", fgColor="FFE6A7"),
    "Low": PatternFill("solid", fgColor="D9EAD3"),
}
status_fills = {
    "Todo": PatternFill("solid", fgColor="CFE2F3"),
    "In Progress": PatternFill("solid", fgColor="FFF2CC"),
    "Done": PatternFill("solid", fgColor="D9EAD3"),
    "Deferred": PatternFill("solid", fgColor="EAD1DC"),
}

ws.append(headers)
for col, _h in enumerate(headers, 1):
    cell = ws.cell(1, col)
    cell.fill = header_fill
    cell.font = header_font
    cell.alignment = Alignment(wrap_text=True, vertical="center", horizontal="center")
    cell.border = thin

for r in rows:
    ws.append(r)

for row_idx in range(2, ws.max_row + 1):
    for col_idx in range(1, len(headers) + 1):
        cell = ws.cell(row_idx, col_idx)
        cell.alignment = wrap
        cell.border = thin
        cell.font = Font(name="Calibri", size=10)
        if row_idx % 2 == 0 and col_idx not in (5, 7):
            cell.fill = alt_fill
    p = ws.cell(row_idx, 5).value
    if p in priority_fills:
        ws.cell(row_idx, 5).fill = priority_fills[p]
    s = ws.cell(row_idx, 7).value
    if s in status_fills:
        ws.cell(row_idx, 7).fill = status_fills[s]

widths = [10, 16, 34, 62, 12, 14, 12, 14, 18, 52, 28]
for i, w in enumerate(widths, 1):
    ws.column_dimensions[get_column_letter(i)].width = w
ws.row_dimensions[1].height = 28
ws.freeze_panes = "A2"
ws.auto_filter.ref = f"A1:K{ws.max_row}"

dv_priority = DataValidation(type="list", formula1='"High,Medium,Low"', allow_blank=False)
dv_effort = DataValidation(type="list", formula1='"S,M,L"', allow_blank=False)
dv_status = DataValidation(type="list", formula1='"Todo,In Progress,Done,Deferred"', allow_blank=False)
ws.add_data_validation(dv_priority)
ws.add_data_validation(dv_effort)
ws.add_data_validation(dv_status)
dv_priority.add(f"E2:E{ws.max_row}")
dv_effort.add(f"F2:F{ws.max_row}")
dv_status.add(f"G2:G{ws.max_row}")

# Summary sheet
summary = wb.create_sheet("Summary", 0)
summary["A1"] = "Agentic AI Chatbot — Future Enhancements Todo List"
summary["A1"].font = Font(bold=True, size=16, color="1F4E79", name="Calibri")
summary.merge_cells("A1:D1")

summary["A3"] = "Generated on"
summary["B3"] = date.today().isoformat()
summary["A4"] = "Project"
summary["B4"] = "agentic-ai-chatbot"
summary["A5"] = "GitHub"
summary["B5"] = "https://github.com/manasa123104/agentic-ai-chatbot"
summary["A6"] = "Total items"
summary["B6"] = len(rows)

summary["A8"] = "Priority"
summary["B8"] = "Count"
summary["A8"].font = Font(bold=True, color="FFFFFF")
summary["B8"].font = Font(bold=True, color="FFFFFF")
summary["A8"].fill = header_fill
summary["B8"].fill = header_fill

pri = Counter(r[4] for r in rows)
cat = Counter(r[1] for r in rows)
effort = Counter(r[5] for r in rows)

row = 9
for p in ["High", "Medium", "Low"]:
    summary[f"A{row}"] = p
    summary[f"B{row}"] = pri[p]
    summary[f"A{row}"].fill = priority_fills[p]
    row += 1

summary["A13"] = "Category"
summary["B13"] = "Count"
summary["A13"].font = Font(bold=True, color="FFFFFF")
summary["B13"].font = Font(bold=True, color="FFFFFF")
summary["A13"].fill = header_fill
summary["B13"].fill = header_fill

row = 14
for c, n in sorted(cat.items()):
    summary[f"A{row}"] = c
    summary[f"B{row}"] = n
    row += 1

summary["D8"] = "Effort"
summary["E8"] = "Count"
summary["D8"].font = Font(bold=True, color="FFFFFF")
summary["E8"].font = Font(bold=True, color="FFFFFF")
summary["D8"].fill = header_fill
summary["E8"].fill = header_fill
summary["D9"] = "S (Small)"
summary["E9"] = effort["S"]
summary["D10"] = "M (Medium)"
summary["E10"] = effort["M"]
summary["D11"] = "L (Large)"
summary["E11"] = effort["L"]

summary["A28"] = "How to use"
summary["A28"].font = Font(bold=True, size=12, color="1F4E79")
summary["A29"] = "1. Open the 'Future Enhancements' sheet."
summary["A30"] = "2. Filter by Priority / Category / Status using the header dropdowns."
summary["A31"] = "3. Update Status (Todo → In Progress → Done) as you complete items."
summary["A32"] = "4. Start with High + Small/Medium items for quick wins (FE-009, FE-018, FE-021, FE-030)."
summary["A33"] = "5. Tackle Large items (CRUD generator, real OAuth, self-healing) as milestone features."

summary.column_dimensions["A"].width = 28
summary.column_dimensions["B"].width = 55
summary.column_dimensions["D"].width = 14
summary.column_dimensions["E"].width = 12

# Quick wins sheet
qw = wb.create_sheet("Quick Wins")
qw_headers = ["ID", "Enhancement", "Priority", "Effort", "Why start here"]
qw.append(qw_headers)
quick = [
    ["FE-009", "Sitemap + route map UI", "High", "S", "Makes multi-page sites discoverable immediately"],
    ["FE-018", "Secrets management polish", "High", "S", "Security hygiene with low effort"],
    ["FE-021", "Export generated site ZIP", "High", "S", "Great demo handoff feature"],
    ["FE-030", "GitHub Actions CI", "High", "S", "Protects the public GitHub repo"],
    ["FE-020", "Example story gallery", "Medium", "S", "Improves first-run experience"],
    ["FE-033", "OpenAPI / Swagger docs", "Low", "S", "Helps API consumers quickly"],
    ["FE-035", "Architecture diagram in README", "Medium", "S", "Better onboarding for new contributors"],
    ["FE-015", "Flaky test detection", "Low", "S", "Small reliability boost"],
]
for r in quick:
    qw.append(r)
for col, _h in enumerate(qw_headers, 1):
    cell = qw.cell(1, col)
    cell.fill = header_fill
    cell.font = header_font
    cell.border = thin
for row_idx in range(2, qw.max_row + 1):
    for col_idx in range(1, 6):
        cell = qw.cell(row_idx, col_idx)
        cell.border = thin
        cell.alignment = wrap
        if col_idx == 3 and cell.value in priority_fills:
            cell.fill = priority_fills[cell.value]
qw.column_dimensions["A"].width = 10
qw.column_dimensions["B"].width = 36
qw.column_dimensions["C"].width = 12
qw.column_dimensions["D"].width = 10
qw.column_dimensions["E"].width = 52
qw.freeze_panes = "A2"

out = Path(__file__).resolve().parents[1] / "Future_Enhancements_Todo_List.xlsx"
wb.save(out)
print(out)
print(f"items={len(rows)}")
