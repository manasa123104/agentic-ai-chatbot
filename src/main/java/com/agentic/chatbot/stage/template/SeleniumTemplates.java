package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;
import com.agentic.chatbot.stage.StagePrompts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Offline Automation Tester output: Selenium WebDriver tests in Java (JUnit 5) and Ruby (RSpec), page-object style.
 */
public final class SeleniumTemplates {

    public static final String JAVA_ROOT = "selenium-java/";
    public static final String RUBY_ROOT = "selenium-ruby/";

    /** One acceptance-criteria scenario from the user stories. */
    public record Scenario(String title, List<String> steps) {
    }

    private SeleniumTemplates() {
    }

    public static String feature(StageDomain domain, String stories) {
        return StageSample.detect(stories, domain).featureName(stories);
    }

    public static String uiPackage(StageDomain domain, String stories) {
        return CodeTemplates.packageFor(domain, stories) + ".ui";
    }

    public static Map<String, String> java(StageDomain domain, String stories, String baseUrl) {
        Map<String, String> files = new LinkedHashMap<>();
        StageSample sample = StageSample.detect(stories, domain);
        String dir = JAVA_ROOT + "src/test/java/";
        switch (sample) {
            case FUND_TRANSFER -> {
                files.put(dir + "com/bank/transfer/ui/FundTransferPage.java", FUND_TRANSFER_PAGE);
                files.put(dir + "com/bank/transfer/ui/FundTransferSeleniumTest.java", FUND_TRANSFER_TEST.replace("{{BASE_URL}}", baseUrl));
            }
            case SHIPMENT_TRACKING -> {
                files.put(dir + "com/logistics/tracking/ui/ShipmentTrackingPage.java", TRACKING_PAGE);
                files.put(dir + "com/logistics/tracking/ui/ShipmentTrackingSeleniumTest.java", TRACKING_TEST.replace("{{BASE_URL}}", baseUrl));
            }
            case GENERIC -> {
                String pkg = uiPackage(domain, stories);
                String feature = sample.featureName(stories);
                String pkgDir = dir + pkg.replace('.', '/') + "/";
                files.put(pkgDir + feature + "Page.java", genericJavaPage(pkg, feature, path(feature)));
                files.put(pkgDir + feature + "SeleniumTest.java", genericJavaTest(pkg, feature, baseUrl, scenarios(stories)));
            }
        }
        return files;
    }

    public static Map<String, String> ruby(StageDomain domain, String stories, String baseUrl) {
        Map<String, String> files = new LinkedHashMap<>();
        StageSample sample = StageSample.detect(stories, domain);
        String spec = RUBY_ROOT + "spec/";
        switch (sample) {
            case FUND_TRANSFER -> {
                files.put(spec + "pages/fund_transfer_page.rb", RUBY_FUND_TRANSFER_PAGE);
                files.put(spec + "fund_transfer_spec.rb", RUBY_FUND_TRANSFER_SPEC);
            }
            case SHIPMENT_TRACKING -> {
                files.put(spec + "pages/shipment_tracking_page.rb", RUBY_TRACKING_PAGE);
                files.put(spec + "shipment_tracking_spec.rb", RUBY_TRACKING_SPEC);
            }
            case GENERIC -> {
                String feature = sample.featureName(stories);
                String snake = StagePrompts.snake(feature);
                files.put(spec + "pages/" + snake + "_page.rb", genericRubyPage(feature, path(feature)));
                files.put(spec + snake + "_spec.rb", genericRubySpec(feature, snake, scenarios(stories)));
            }
        }
        return files;
    }

    /** Build and run files that do not depend on the model: Maven pom, Gemfile, spec_helper, READMEs. */
    public static Map<String, String> javaScaffold(String groupId) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(JAVA_ROOT + "pom.xml", JAVA_POM.replace("{{GROUP}}", groupId));
        files.put(JAVA_ROOT + "README.md", """
                # Selenium tests (Java, JUnit 5)

                Requires Chrome. Selenium Manager downloads the matching driver automatically.

                    mvn test -Dapp.baseUrl=http://localhost:8080
                    mvn test -Dheadless=false        # watch the browser

                The page objects define the element ids the application must expose.
                """);
        return files;
    }

    public static Map<String, String> rubyScaffold(String baseUrl) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put(RUBY_ROOT + "Gemfile", """
                source 'https://rubygems.org'

                gem 'rspec', '~> 3.13'
                gem 'selenium-webdriver', '~> 4.27'
                """);
        files.put(RUBY_ROOT + ".rspec", "--require spec_helper\n--format documentation\n");
        files.put(RUBY_ROOT + "spec/spec_helper.rb", RUBY_SPEC_HELPER.replace("{{BASE_URL}}", baseUrl));
        files.put(RUBY_ROOT + "README.md", """
                # Selenium tests (Ruby, RSpec)

                Requires Ruby 3.x and Chrome.

                    bundle install
                    APP_BASE_URL=http://localhost:8080 bundle exec rspec
                    HEADLESS=false bundle exec rspec   # watch the browser
                """);
        return files;
    }

    /** Scenarios with their Given/When/Then steps, in story order. */
    public static List<Scenario> scenarios(String stories) {
        List<Scenario> out = new ArrayList<>();
        String title = null;
        List<String> steps = new ArrayList<>();
        for (String raw : (stories == null ? "" : stories).split("\\R")) {
            String line = raw.strip().replaceAll("^[-*]\\s+", "").replace("**", "");
            if (line.matches("(?i)^(scenario|scenario outline):.*")) {
                if (title != null) {
                    out.add(new Scenario(title, List.copyOf(steps)));
                }
                title = line.substring(line.indexOf(':') + 1).strip();
                steps.clear();
            } else if (title != null && line.matches("(?i)^(given|when|then|and|but)\\b.*")) {
                steps.add(line);
            } else if (title != null && line.startsWith("#")) {
                out.add(new Scenario(title, List.copyOf(steps)));
                title = null;
                steps.clear();
            }
        }
        if (title != null) {
            out.add(new Scenario(title, List.copyOf(steps)));
        }
        if (out.isEmpty()) {
            out.add(new Scenario("Page loads", List.of("Given the application is running", "When I open the page",
                    "Then I see the page title")));
        }
        return out;
    }

    private static String path(String feature) {
        return "/" + StagePrompts.snake(feature).replace('_', '-');
    }

    private static String methodName(String title, int index, List<String> used) {
        StringBuilder sb = new StringBuilder();
        for (String w : title.replaceAll("[^A-Za-z0-9 ]", " ").trim().split("\\s+")) {
            if (w.isEmpty()) {
                continue;
            }
            String lower = w.toLowerCase(Locale.ROOT);
            sb.append(sb.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
        }
        String name = sb.isEmpty() || !Character.isLetter(sb.charAt(0)) ? "scenario" + index : sb.toString();
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        String unique = name;
        int n = 2;
        while (used.contains(unique)) {
            unique = name + n++;
        }
        used.add(unique);
        return unique;
    }

    private static String javaString(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String genericJavaPage(String pkg, String feature, String path) {
        return """
                package PKG;

                import org.openqa.selenium.By;
                import org.openqa.selenium.WebDriver;
                import org.openqa.selenium.support.ui.ExpectedConditions;
                import org.openqa.selenium.support.ui.WebDriverWait;

                import java.time.Duration;

                /**
                 * Page object for the FEATURE screen (PATH). Locators are the contract with the UI:
                 * a form with id "main-form", a submit button with id "submit" and a message area with id "message".
                 */
                public class FEATUREPage {

                    private static final By FORM = By.id("main-form");
                    private static final By SUBMIT = By.id("submit");
                    private static final By MESSAGE = By.id("message");

                    private final WebDriver driver;
                    private final WebDriverWait wait;

                    /** @param driver browser session */
                    public FEATUREPage(WebDriver driver) {
                        this.driver = driver;
                        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                    }

                    /** Opens the screen and waits until the form is visible. */
                    public FEATUREPage open(String baseUrl) {
                        driver.get(baseUrl + "PATH");
                        wait.until(ExpectedConditions.visibilityOfElementLocated(FORM));
                        return this;
                    }

                    /** Types into a field identified by its id. */
                    public FEATUREPage fill(String fieldId, String value) {
                        var field = driver.findElement(By.id(fieldId));
                        field.clear();
                        field.sendKeys(value);
                        return this;
                    }

                    /** Submits the form. */
                    public FEATUREPage submit() {
                        driver.findElement(SUBMIT).click();
                        return this;
                    }

                    /** @return text of the confirmation or error message */
                    public String message() {
                        return wait.until(ExpectedConditions.visibilityOfElementLocated(MESSAGE)).getText();
                    }

                    /** @return browser tab title */
                    public String title() {
                        return driver.getTitle();
                    }
                }
                """.replace("PKG", pkg).replace("FEATURE", feature).replace("PATH", path);
    }

    private static String genericJavaTest(String pkg, String feature, String baseUrl, List<Scenario> scenarios) {
        StringBuilder tests = new StringBuilder();
        List<String> used = new ArrayList<>();
        int i = 0;
        for (Scenario s : scenarios) {
            i++;
            tests.append("\n    /** Scenario: ").append(s.title().replace("*/", "").replace('\\', '/')).append(" */\n");
            tests.append("    @Test\n    @DisplayName(\"").append(javaString(s.title())).append("\")\n");
            tests.append("    void ").append(methodName(s.title(), i, used)).append("() {\n");
            for (String step : s.steps()) {
                tests.append("        // ").append(step.replace('\\', '/')).append('\n');
            }
            tests.append("        // TODO: fill the form fields for this scenario, e.g. page.fill(\"fieldId\", \"value\").submit();\n");
            tests.append("        assertFalse(page.title().isBlank(), \"page should load\");\n    }\n");
        }
        return """
                package PKG;

                import org.junit.jupiter.api.AfterEach;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.DisplayName;
                import org.junit.jupiter.api.Test;
                import org.openqa.selenium.WebDriver;
                import org.openqa.selenium.chrome.ChromeDriver;
                import org.openqa.selenium.chrome.ChromeOptions;

                import static org.junit.jupiter.api.Assertions.assertFalse;

                /**
                 * Selenium functional tests for FEATURE, one test per acceptance-criteria scenario.
                 * Run: mvn test -Dapp.baseUrl={{URL}}
                 */
                class FEATURESeleniumTest {

                    private static final String BASE_URL = System.getProperty("app.baseUrl", "{{URL}}");

                    private WebDriver driver;
                    private FEATUREPage page;

                    @BeforeEach
                    void openBrowser() {
                        ChromeOptions options = new ChromeOptions();
                        if (Boolean.parseBoolean(System.getProperty("headless", "true"))) {
                            options.addArguments("--headless=new");
                        }
                        driver = new ChromeDriver(options);
                        page = new FEATUREPage(driver).open(BASE_URL);
                    }

                    @AfterEach
                    void closeBrowser() {
                        if (driver != null) {
                            driver.quit();
                        }
                    }
                TESTS}
                """.replace("PKG", pkg).replace("FEATURE", feature).replace("{{URL}}", javaString(baseUrl))
                .replace("TESTS", tests.toString());
    }

    private static String genericRubyPage(String feature, String path) {
        return """
                # Page object for the FEATURE screen (PATH).
                # Locators are the contract with the UI: form#main-form, button#submit and #message.
                class FEATUREPage
                  FORM = { id: 'main-form' }.freeze
                  SUBMIT = { id: 'submit' }.freeze
                  MESSAGE = { id: 'message' }.freeze

                  def initialize(driver)
                    @driver = driver
                    @wait = Selenium::WebDriver::Wait.new(timeout: 10)
                  end

                  # Opens the screen and waits until the form is visible.
                  def open(base_url)
                    @driver.navigate.to("#{base_url}PATH")
                    @wait.until { @driver.find_element(FORM).displayed? }
                    self
                  end

                  # Types into a field identified by its id.
                  def fill(field_id, value)
                    field = @driver.find_element(id: field_id)
                    field.clear
                    field.send_keys(value)
                    self
                  end

                  def submit
                    @driver.find_element(SUBMIT).click
                    self
                  end

                  def message
                    @wait.until { @driver.find_element(MESSAGE).displayed? }
                    @driver.find_element(MESSAGE).text
                  end

                  def title
                    @driver.title
                  end
                end
                """.replace("FEATURE", feature).replace("PATH", path);
    }

    private static String genericRubySpec(String feature, String snake, List<Scenario> scenarios) {
        StringBuilder sb = new StringBuilder();
        sb.append("require_relative 'spec_helper'\nrequire_relative 'pages/").append(snake).append("_page'\n\n");
        sb.append("# Selenium functional tests for ").append(feature).append(", one example per acceptance-criteria scenario.\n");
        sb.append("RSpec.describe '").append(feature.replace("'", "")).append("' do\n");
        sb.append("  let(:page) { ").append(feature).append("Page.new(@driver).open(BASE_URL) }\n");
        for (Scenario s : scenarios) {
            sb.append("\n  it '").append(s.title().replace("\\", "").replace("'", "\\'")).append("' do\n");
            for (String step : s.steps()) {
                sb.append("    # ").append(step).append('\n');
            }
            sb.append("    # TODO: fill the form for this scenario, e.g. page.fill('fieldId', 'value').submit\n");
            sb.append("    expect(page.title).not_to be_empty\n  end\n");
        }
        sb.append("end\n");
        return sb.toString();
    }

    private static final String JAVA_POM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>{{GROUP}}</groupId>
                <artifactId>selenium-tests</artifactId>
                <version>1.0.0</version>
                <description>Selenium functional tests generated by BOT. Run: mvn test -Dapp.baseUrl=http://localhost:8080</description>

                <properties>
                    <maven.compiler.release>17</maven.compiler.release>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>org.seleniumhq.selenium</groupId>
                        <artifactId>selenium-java</artifactId>
                        <version>4.27.0</version>
                        <scope>test</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.11.4</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>

                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-surefire-plugin</artifactId>
                            <version>3.5.2</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    private static final String RUBY_SPEC_HELPER = """
            require 'rspec'
            require 'selenium-webdriver'

            # Base URL of the application under test; override with APP_BASE_URL.
            BASE_URL = ENV.fetch('APP_BASE_URL', '{{BASE_URL}}')

            RSpec.configure do |config|
              config.before(:each) do
                options = Selenium::WebDriver::Chrome::Options.new
                options.add_argument('--headless=new') unless ENV['HEADLESS'] == 'false'
                @driver = Selenium::WebDriver.for(:chrome, options: options)
              end

              config.after(:each) do
                @driver&.quit
              end
            end
            """;

    private static final String FUND_TRANSFER_PAGE = """
            package com.bank.transfer.ui;

            import org.openqa.selenium.By;
            import org.openqa.selenium.WebDriver;
            import org.openqa.selenium.WebElement;
            import org.openqa.selenium.support.ui.ExpectedConditions;
            import org.openqa.selenium.support.ui.WebDriverWait;

            import java.time.Duration;

            /**
             * Page object for the fund transfer screen (/transfer).
             * The element ids below are the contract between the UI and these tests.
             */
            public class FundTransferPage {

                private static final By FROM_ACCOUNT = By.id("fromAccount");
                private static final By TO_ACCOUNT = By.id("toAccount");
                private static final By AMOUNT = By.id("amount");
                private static final By REFERENCE = By.id("reference");
                private static final By SUBMIT = By.id("transferButton");
                private static final By CONFIRMATION = By.id("confirmation");
                private static final By ERROR = By.id("error");

                private final WebDriver driver;
                private final WebDriverWait wait;

                /** @param driver browser session */
                public FundTransferPage(WebDriver driver) {
                    this.driver = driver;
                    this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                }

                /** Opens the transfer screen and waits for the form. */
                public FundTransferPage open(String baseUrl) {
                    driver.get(baseUrl + "/transfer");
                    wait.until(ExpectedConditions.visibilityOfElementLocated(FROM_ACCOUNT));
                    return this;
                }

                /** Fills the form and submits a transfer. */
                public FundTransferPage transfer(String from, String to, String amount, String reference) {
                    type(FROM_ACCOUNT, from);
                    type(TO_ACCOUNT, to);
                    type(AMOUNT, amount);
                    type(REFERENCE, reference);
                    driver.findElement(SUBMIT).click();
                    return this;
                }

                /** @return confirmation text, which includes the transaction id */
                public String confirmation() {
                    return wait.until(ExpectedConditions.visibilityOfElementLocated(CONFIRMATION)).getText();
                }

                /** @return the error message shown when a transfer is refused */
                public String error() {
                    return wait.until(ExpectedConditions.visibilityOfElementLocated(ERROR)).getText();
                }

                private void type(By locator, String value) {
                    WebElement field = driver.findElement(locator);
                    field.clear();
                    field.sendKeys(value);
                }
            }
            """;

    private static final String FUND_TRANSFER_TEST = """
            package com.bank.transfer.ui;

            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Test;
            import org.openqa.selenium.WebDriver;
            import org.openqa.selenium.chrome.ChromeDriver;
            import org.openqa.selenium.chrome.ChromeOptions;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertTrue;

            /**
             * Selenium functional tests for the fund transfer user stories.
             * Test data: ACC-1001 (active, 1000.00), ACC-2002 (active), ACC-3003 (frozen).
             * Run: mvn test -Dapp.baseUrl={{BASE_URL}}
             */
            class FundTransferSeleniumTest {

                private static final String BASE_URL = System.getProperty("app.baseUrl", "{{BASE_URL}}");

                private WebDriver driver;
                private FundTransferPage page;

                @BeforeEach
                void openBrowser() {
                    ChromeOptions options = new ChromeOptions();
                    if (Boolean.parseBoolean(System.getProperty("headless", "true"))) {
                        options.addArguments("--headless=new");
                    }
                    driver = new ChromeDriver(options);
                    page = new FundTransferPage(driver).open(BASE_URL);
                }

                @AfterEach
                void closeBrowser() {
                    if (driver != null) {
                        driver.quit();
                    }
                }

                /** Scenario: Successful transfer */
                @Test
                @DisplayName("Successful transfer shows a confirmation with a TXN id")
                void successfulTransfer() {
                    page.transfer("ACC-1001", "ACC-2002", "250.00", "Rent");
                    assertTrue(page.confirmation().contains("TXN"));
                }

                /** Scenario: Insufficient funds */
                @Test
                @DisplayName("Insufficient funds is refused")
                void insufficientFunds() {
                    page.transfer("ACC-1001", "ACC-2002", "5000.00", "Too much");
                    assertEquals("Insufficient funds", page.error());
                }

                /** Scenario: Same source and destination */
                @Test
                @DisplayName("Transfer to the same account is refused")
                void sameAccount() {
                    page.transfer("ACC-1001", "ACC-1001", "10.00", "Loop");
                    assertEquals("Cannot transfer to the same account", page.error());
                }

                /** Scenario: Zero or negative amount */
                @Test
                @DisplayName("Zero amount is refused")
                void invalidAmount() {
                    page.transfer("ACC-1001", "ACC-2002", "0.00", "Nothing");
                    assertTrue(page.error().startsWith("Enter a valid amount"));
                }

                /** Scenario: Frozen source account */
                @Test
                @DisplayName("Frozen account cannot send money")
                void frozenAccount() {
                    page.transfer("ACC-3003", "ACC-2002", "10.00", "Blocked");
                    assertEquals("Account ACC-3003 is not active", page.error());
                }

                /** Scenario: Unknown beneficiary */
                @Test
                @DisplayName("Unknown beneficiary is refused")
                void unknownBeneficiary() {
                    page.transfer("ACC-1001", "ACC-9999", "10.00", "Who");
                    assertEquals("Account not found: ACC-9999", page.error());
                }
            }
            """;

    private static final String TRACKING_PAGE = """
            package com.logistics.tracking.ui;

            import org.openqa.selenium.By;
            import org.openqa.selenium.WebDriver;
            import org.openqa.selenium.WebElement;
            import org.openqa.selenium.support.ui.ExpectedConditions;
            import org.openqa.selenium.support.ui.WebDriverWait;

            import java.time.Duration;
            import java.util.List;

            /**
             * Page object for the public tracking screen (/track).
             * The element ids below are the contract between the UI and these tests.
             */
            public class ShipmentTrackingPage {

                private static final By TRACKING_NUMBER = By.id("trackingNumber");
                private static final By TRACK = By.id("trackButton");
                private static final By STATUS = By.id("status");
                private static final By LOCATION = By.id("location");
                private static final By ETA = By.id("eta");
                private static final By EVENTS = By.cssSelector("#events li");
                private static final By ERROR = By.id("error");

                private final WebDriver driver;
                private final WebDriverWait wait;

                /** @param driver browser session */
                public ShipmentTrackingPage(WebDriver driver) {
                    this.driver = driver;
                    this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
                }

                /** Opens the tracking screen. */
                public ShipmentTrackingPage open(String baseUrl) {
                    driver.get(baseUrl + "/track");
                    wait.until(ExpectedConditions.visibilityOfElementLocated(TRACKING_NUMBER));
                    return this;
                }

                /** Enters a tracking number and searches. */
                public ShipmentTrackingPage track(String trackingNumber) {
                    WebElement field = driver.findElement(TRACKING_NUMBER);
                    field.clear();
                    field.sendKeys(trackingNumber);
                    driver.findElement(TRACK).click();
                    return this;
                }

                /** @return current status, e.g. IN_TRANSIT */
                public String status() {
                    return wait.until(ExpectedConditions.visibilityOfElementLocated(STATUS)).getText();
                }

                /** @return current location */
                public String location() {
                    return driver.findElement(LOCATION).getText();
                }

                /** @return estimated delivery date as shown */
                public String eta() {
                    return driver.findElement(ETA).getText();
                }

                /** @return scan history rows, oldest first */
                public List<String> events() {
                    return driver.findElements(EVENTS).stream().map(WebElement::getText).toList();
                }

                /** @return the error message for a bad or unknown tracking number */
                public String error() {
                    return wait.until(ExpectedConditions.visibilityOfElementLocated(ERROR)).getText();
                }
            }
            """;

    private static final String TRACKING_TEST = """
            package com.logistics.tracking.ui;

            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.BeforeEach;
            import org.junit.jupiter.api.DisplayName;
            import org.junit.jupiter.api.Test;
            import org.openqa.selenium.WebDriver;
            import org.openqa.selenium.chrome.ChromeDriver;
            import org.openqa.selenium.chrome.ChromeOptions;

            import static org.junit.jupiter.api.Assertions.assertEquals;
            import static org.junit.jupiter.api.Assertions.assertFalse;

            /**
             * Selenium functional tests for the shipment tracking user stories.
             * Test data: LG0000000001 is IN_TRANSIT at "Lonavala hub".
             * Run: mvn test -Dapp.baseUrl={{BASE_URL}}
             */
            class ShipmentTrackingSeleniumTest {

                private static final String BASE_URL = System.getProperty("app.baseUrl", "{{BASE_URL}}");

                private WebDriver driver;
                private ShipmentTrackingPage page;

                @BeforeEach
                void openBrowser() {
                    ChromeOptions options = new ChromeOptions();
                    if (Boolean.parseBoolean(System.getProperty("headless", "true"))) {
                        options.addArguments("--headless=new");
                    }
                    driver = new ChromeDriver(options);
                    page = new ShipmentTrackingPage(driver).open(BASE_URL);
                }

                @AfterEach
                void closeBrowser() {
                    if (driver != null) {
                        driver.quit();
                    }
                }

                /** Scenario: Known tracking number */
                @Test
                @DisplayName("Known tracking number shows status, location, ETA and history")
                void showsShipmentDetails() {
                    page.track("LG0000000001");
                    assertEquals("IN_TRANSIT", page.status());
                    assertEquals("Lonavala hub", page.location());
                    assertFalse(page.eta().isBlank());
                    assertFalse(page.events().isEmpty());
                }

                /** Scenario: Malformed tracking number */
                @Test
                @DisplayName("Malformed tracking number is explained")
                void malformedTrackingNumber() {
                    page.track("ABC123");
                    assertEquals("Tracking number must be LG followed by 10 digits", page.error());
                }

                /** Scenario: Unknown tracking number */
                @Test
                @DisplayName("Unknown tracking number is reported")
                void unknownTrackingNumber() {
                    page.track("LG9999999999");
                    assertEquals("Shipment not found: LG9999999999", page.error());
                }

                /** Scenario: lower-case input is accepted */
                @Test
                @DisplayName("Tracking number is case-insensitive")
                void lowerCaseTrackingNumber() {
                    page.track("lg0000000001");
                    assertEquals("IN_TRANSIT", page.status());
                }
            }
            """;

    private static final String RUBY_FUND_TRANSFER_PAGE = """
            # Page object for the fund transfer screen (/transfer).
            # The element ids are the contract between the UI and these tests.
            class FundTransferPage
              FROM_ACCOUNT = { id: 'fromAccount' }.freeze
              TO_ACCOUNT = { id: 'toAccount' }.freeze
              AMOUNT = { id: 'amount' }.freeze
              REFERENCE = { id: 'reference' }.freeze
              SUBMIT = { id: 'transferButton' }.freeze
              CONFIRMATION = { id: 'confirmation' }.freeze
              ERROR = { id: 'error' }.freeze

              def initialize(driver)
                @driver = driver
                @wait = Selenium::WebDriver::Wait.new(timeout: 10)
              end

              # Opens the transfer screen and waits for the form.
              def open(base_url)
                @driver.navigate.to("#{base_url}/transfer")
                @wait.until { @driver.find_element(FROM_ACCOUNT).displayed? }
                self
              end

              # Fills the form and submits a transfer.
              def transfer(from:, to:, amount:, reference: '')
                type(FROM_ACCOUNT, from)
                type(TO_ACCOUNT, to)
                type(AMOUNT, amount)
                type(REFERENCE, reference)
                @driver.find_element(SUBMIT).click
                self
              end

              def confirmation
                visible_text(CONFIRMATION)
              end

              def error
                visible_text(ERROR)
              end

              private

              def type(locator, value)
                field = @driver.find_element(locator)
                field.clear
                field.send_keys(value)
              end

              def visible_text(locator)
                @wait.until { @driver.find_element(locator).displayed? }
                @driver.find_element(locator).text
              end
            end
            """;

    private static final String RUBY_FUND_TRANSFER_SPEC = """
            require_relative 'spec_helper'
            require_relative 'pages/fund_transfer_page'

            # Selenium functional tests for the fund transfer user stories.
            # Test data: ACC-1001 (active, 1000.00), ACC-2002 (active), ACC-3003 (frozen).
            RSpec.describe 'Fund transfer' do
              let(:page) { FundTransferPage.new(@driver).open(BASE_URL) }

              # Scenario: Successful transfer
              it 'shows a confirmation with a TXN id' do
                page.transfer(from: 'ACC-1001', to: 'ACC-2002', amount: '250.00', reference: 'Rent')
                expect(page.confirmation).to include('TXN')
              end

              # Scenario: Insufficient funds
              it 'refuses a transfer larger than the balance' do
                page.transfer(from: 'ACC-1001', to: 'ACC-2002', amount: '5000.00')
                expect(page.error).to eq('Insufficient funds')
              end

              # Scenario: Same source and destination
              it 'refuses a transfer to the same account' do
                page.transfer(from: 'ACC-1001', to: 'ACC-1001', amount: '10.00')
                expect(page.error).to eq('Cannot transfer to the same account')
              end

              # Scenario: Zero or negative amount
              it 'refuses a zero amount' do
                page.transfer(from: 'ACC-1001', to: 'ACC-2002', amount: '0.00')
                expect(page.error).to start_with('Enter a valid amount')
              end

              # Scenario: Frozen source account
              it 'blocks a frozen account from sending money' do
                page.transfer(from: 'ACC-3003', to: 'ACC-2002', amount: '10.00')
                expect(page.error).to eq('Account ACC-3003 is not active')
              end

              # Scenario: Unknown beneficiary
              it 'refuses an unknown beneficiary' do
                page.transfer(from: 'ACC-1001', to: 'ACC-9999', amount: '10.00')
                expect(page.error).to eq('Account not found: ACC-9999')
              end
            end
            """;

    private static final String RUBY_TRACKING_PAGE = """
            # Page object for the public tracking screen (/track).
            # The element ids are the contract between the UI and these tests.
            class ShipmentTrackingPage
              TRACKING_NUMBER = { id: 'trackingNumber' }.freeze
              TRACK = { id: 'trackButton' }.freeze
              STATUS = { id: 'status' }.freeze
              LOCATION = { id: 'location' }.freeze
              ETA = { id: 'eta' }.freeze
              EVENTS = { css: '#events li' }.freeze
              ERROR = { id: 'error' }.freeze

              def initialize(driver)
                @driver = driver
                @wait = Selenium::WebDriver::Wait.new(timeout: 10)
              end

              # Opens the tracking screen.
              def open(base_url)
                @driver.navigate.to("#{base_url}/track")
                @wait.until { @driver.find_element(TRACKING_NUMBER).displayed? }
                self
              end

              # Enters a tracking number and searches.
              def track(number)
                field = @driver.find_element(TRACKING_NUMBER)
                field.clear
                field.send_keys(number)
                @driver.find_element(TRACK).click
                self
              end

              def status
                @wait.until { @driver.find_element(STATUS).displayed? }
                @driver.find_element(STATUS).text
              end

              def location
                @driver.find_element(LOCATION).text
              end

              def eta
                @driver.find_element(ETA).text
              end

              def events
                @driver.find_elements(EVENTS).map(&:text)
              end

              def error
                @wait.until { @driver.find_element(ERROR).displayed? }
                @driver.find_element(ERROR).text
              end
            end
            """;

    private static final String RUBY_TRACKING_SPEC = """
            require_relative 'spec_helper'
            require_relative 'pages/shipment_tracking_page'

            # Selenium functional tests for the shipment tracking user stories.
            # Test data: LG0000000001 is IN_TRANSIT at "Lonavala hub".
            RSpec.describe 'Shipment tracking' do
              let(:page) { ShipmentTrackingPage.new(@driver).open(BASE_URL) }

              # Scenario: Known tracking number
              it 'shows status, location, ETA and history' do
                page.track('LG0000000001')
                expect(page.status).to eq('IN_TRANSIT')
                expect(page.location).to eq('Lonavala hub')
                expect(page.eta).not_to be_empty
                expect(page.events).not_to be_empty
              end

              # Scenario: Malformed tracking number
              it 'explains a malformed tracking number' do
                page.track('ABC123')
                expect(page.error).to eq('Tracking number must be LG followed by 10 digits')
              end

              # Scenario: Unknown tracking number
              it 'reports an unknown tracking number' do
                page.track('LG9999999999')
                expect(page.error).to eq('Shipment not found: LG9999999999')
              end

              it 'accepts a lower-case tracking number' do
                page.track('lg0000000001')
                expect(page.status).to eq('IN_TRANSIT')
              end
            end
            """;
}
