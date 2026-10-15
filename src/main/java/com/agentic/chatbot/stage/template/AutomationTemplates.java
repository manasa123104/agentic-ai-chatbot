package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageAutomation;
import com.agentic.chatbot.stage.StageDomain;
import com.agentic.chatbot.stage.StagePrompts;
import com.agentic.chatbot.stage.template.SeleniumTemplates.Scenario;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline UI-automation output for Selenium Python (pytest) and Playwright (JavaScript / TypeScript): a page object
 * plus one test per Given/When/Then scenario. Java and Ruby Selenium live in {@link SeleniumTemplates}.
 */
public final class AutomationTemplates {

    private AutomationTemplates() {
    }

    public static boolean supports(StageAutomation a) {
        return a == StageAutomation.SELENIUM_PYTHON || a == StageAutomation.PLAYWRIGHT_JS
                || a == StageAutomation.PLAYWRIGHT_TS;
    }

    public static Map<String, String> tests(StageAutomation a, StageDomain domain, String stories, String baseUrl) {
        String feature = SeleniumTemplates.feature(domain, stories);
        List<Scenario> scenarios = SeleniumTemplates.scenarios(stories);
        return switch (a) {
            case SELENIUM_PYTHON -> python(feature, scenarios);
            case PLAYWRIGHT_JS -> playwright(feature, scenarios, false);
            case PLAYWRIGHT_TS -> playwright(feature, scenarios, true);
            default -> Map.of();
        };
    }

    /** Dependency and config files, not shown as stage output. */
    public static Map<String, String> scaffold(StageAutomation a, String baseUrl) {
        Map<String, String> files = new LinkedHashMap<>();
        String root = a.root();
        switch (a) {
            case SELENIUM_PYTHON -> {
                files.put(root + "requirements.txt", "pytest>=8.0\nselenium>=4.27\n");
                files.put(root + "pytest.ini", "[pytest]\npythonpath = .\ntestpaths = tests\n");
                files.put(root + "conftest.py", PY_CONFTEST.replace("{{BASE_URL}}", baseUrl));
                files.put(root + "README.md", """
                        # Selenium tests (Python, pytest)

                        Requires Python 3.10+ and Chrome. Selenium Manager downloads the driver automatically.

                            pip install -r requirements.txt
                            APP_BASE_URL=http://localhost:8080 pytest
                            HEADLESS=false pytest     # watch the browser
                        """);
            }
            case PLAYWRIGHT_JS, PLAYWRIGHT_TS -> {
                boolean ts = a == StageAutomation.PLAYWRIGHT_TS;
                files.put(root + "package.json", """
                        {
                          "name": "ui-tests",
                          "private": true,
                          "scripts": { "test": "playwright test" },
                          "devDependencies": {
                            "@playwright/test": "^1.48.0"%s
                          }
                        }
                        """.formatted(ts ? ",\n    \"typescript\": \"^5.6.0\"" : ""));
                files.put(root + "playwright.config." + (ts ? "ts" : "js"), (ts
                        ? "import { defineConfig } from '@playwright/test';\n\nexport default defineConfig({\n"
                        : "// @ts-check\nconst { defineConfig } = require('@playwright/test');\n\nmodule.exports = defineConfig({\n")
                        + "  testDir: './tests',\n"
                        + "  use: { baseURL: process.env.APP_BASE_URL || '" + baseUrl + "', headless: process.env.HEADLESS !== 'false' },\n"
                        + "});\n");
                files.put(root + "README.md", """
                        # Playwright tests (%s)

                        Requires Node.js 18+.

                            npm install
                            npx playwright install chromium
                            APP_BASE_URL=http://localhost:8080 npx playwright test
                        """.formatted(ts ? "TypeScript" : "JavaScript"));
            }
            default -> {
            }
        }
        return files;
    }

    private static Map<String, String> python(String feature, List<Scenario> scenarios) {
        String snake = StagePrompts.snake(feature);
        String root = StageAutomation.SELENIUM_PYTHON.root();
        String path = "/" + snake.replace('_', '-');
        Map<String, String> files = new LinkedHashMap<>();
        files.put(root + "pages/__init__.py", "");
        files.put(root + "pages/" + snake + "_page.py", """
                \"\"\"Page object for the %s page. The ids are the contract the application must expose.\"\"\"
                from selenium.webdriver.common.by import By
                from selenium.webdriver.support import expected_conditions as EC
                from selenium.webdriver.support.ui import WebDriverWait


                class %sPage:
                    PATH = "%s"
                    FORM = (By.ID, "%s-form")
                    SUBMIT = (By.ID, "submit")
                    MESSAGE = (By.ID, "message")

                    def __init__(self, driver, timeout=10):
                        self.driver = driver
                        self.wait = WebDriverWait(driver, timeout)

                    def open(self, base_url):
                        self.driver.get(base_url + self.PATH)
                        return self

                    def fill(self, field_id, value):
                        element = self.wait.until(EC.visibility_of_element_located((By.ID, field_id)))
                        element.clear()
                        element.send_keys(value)
                        return self

                    def submit(self):
                        self.wait.until(EC.element_to_be_clickable(self.SUBMIT)).click()
                        return self

                    def message(self):
                        return self.wait.until(EC.visibility_of_element_located(self.MESSAGE)).text

                    def title(self):
                        return self.driver.title
                """.formatted(feature, feature, path, snake.replace('_', '-')));
        StringBuilder spec = new StringBuilder();
        spec.append("\"\"\"UI tests for ").append(feature).append(": one test per acceptance-criteria scenario.\"\"\"\n")
                .append("from pages.").append(snake).append("_page import ").append(feature).append("Page\n");
        List<String> used = new ArrayList<>();
        for (Scenario s : scenarios) {
            String name = unique(snakeWords(s.title()), used);
            spec.append("\n\n# Scenario: ").append(s.title().replace("\n", " ")).append('\n')
                    .append("def test_").append(name).append("(driver, base_url):\n");
            for (String step : s.steps()) {
                spec.append("    # ").append(step).append('\n');
            }
            spec.append("    page = ").append(feature).append("Page(driver).open(base_url)\n")
                    .append("    assert page.title()  # TODO: drive the steps above through the page object\n");
        }
        files.put(root + "tests/test_" + snake + ".py", spec.toString());
        return files;
    }

    private static Map<String, String> playwright(String feature, List<Scenario> scenarios, boolean ts) {
        String ext = ts ? "ts" : "js";
        String root = (ts ? StageAutomation.PLAYWRIGHT_TS : StageAutomation.PLAYWRIGHT_JS).root();
        String kebab = StagePrompts.snake(feature).replace('_', '-');
        Map<String, String> files = new LinkedHashMap<>();
        String page = (ts ? "import { Page, expect } from '@playwright/test';\n\n"
                : "// @ts-check\nconst { expect } = require('@playwright/test');\n\n")
                + "/** Page object for the " + feature + " page. The ids are the contract the application must expose. */\n"
                + (ts ? "export " : "") + "class " + feature + "Page {\n"
                + (ts ? "  constructor(private readonly page: Page) {}\n" : "  constructor(page) {\n    this.page = page;\n  }\n")
                + "\n  async open() {\n    await this.page.goto('/" + kebab + "');\n    return this;\n  }\n"
                + "\n  async fill(id" + (ts ? ": string" : "") + ", value" + (ts ? ": string" : "") + ") {\n"
                + "    await this.page.locator('#' + id).fill(value);\n    return this;\n  }\n"
                + "\n  async submit() {\n    await this.page.locator('#submit').click();\n    return this;\n  }\n"
                + "\n  async expectMessage(text" + (ts ? ": string" : "") + ") {\n"
                + "    await expect(this.page.locator('#message')).toContainText(text);\n  }\n}\n"
                + (ts ? "" : "\nmodule.exports = { " + feature + "Page };\n");
        files.put(root + "tests/pages/" + kebab + ".page." + ext, page);
        StringBuilder spec = new StringBuilder();
        spec.append(ts
                ? "import { test, expect } from '@playwright/test';\nimport { " + feature + "Page } from './pages/" + kebab + ".page';\n"
                : "// @ts-check\nconst { test, expect } = require('@playwright/test');\nconst { " + feature
                + "Page } = require('./pages/" + kebab + ".page');\n");
        spec.append("\ntest.describe('").append(feature).append("', () => {\n");
        for (Scenario s : scenarios) {
            spec.append("  // Scenario: ").append(s.title().replace("\n", " ")).append('\n')
                    .append("  test('").append(s.title().replace("'", "\\'")).append("', async ({ page }) => {\n");
            for (String step : s.steps()) {
                spec.append("    // ").append(step).append('\n');
            }
            spec.append("    await new ").append(feature).append("Page(page).open();\n")
                    .append("    await expect(page).toHaveTitle(/.+/); // TODO: drive the steps above through the page object\n")
                    .append("  });\n\n");
        }
        spec.setLength(spec.length() - 1);
        spec.append("});\n");
        files.put(root + "tests/" + kebab + ".spec." + ext, spec.toString());
        return files;
    }

    private static String snakeWords(String title) {
        String s = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (s.isEmpty() || !Character.isLetter(s.charAt(0))) {
            s = "scenario_" + s;
        }
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String unique(String name, List<String> used) {
        String u = name;
        int n = 2;
        while (used.contains(u)) {
            u = name + "_" + n++;
        }
        used.add(u);
        return u;
    }

    private static final String PY_CONFTEST = """
            import os

            import pytest
            from selenium import webdriver


            @pytest.fixture
            def base_url():
                return os.environ.get("APP_BASE_URL", "{{BASE_URL}}").rstrip("/")


            @pytest.fixture
            def driver():
                options = webdriver.ChromeOptions()
                if os.environ.get("HEADLESS", "true") != "false":
                    options.add_argument("--headless=new")
                drv = webdriver.Chrome(options=options)
                yield drv
                drv.quit()
            """;
}
