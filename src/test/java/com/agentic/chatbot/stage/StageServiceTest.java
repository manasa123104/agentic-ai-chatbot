package com.agentic.chatbot.stage;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.BotModelTrainingService;
import com.agentic.chatbot.stage.StageResponse.StageFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageServiceTest {

    private static final LlmClient OFFLINE = new LlmClient() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new AssertionError("Offline stages must not call the model");
        }
    };

    @TempDir
    Path tmp;

    private StageService service(LlmClient llm) {
        BotModelTrainingService training = new BotModelTrainingService(tmp.resolve("bot-training.json").toString());
        training.load();
        return new StageService(new StageLlm(llm), training, tmp.resolve("generated-app").toString());
    }

    private static StageRequest request(String domain, String input, Map<String, String> options) {
        return new StageRequest(domain, input, options, null);
    }

    // ------------------------------------------------------------------ names and ids

    @Test
    void sixStagesWithSimpleNamesAndOldIdsAsAliases() {
        assertEquals(List.of("Business Analyst", "Developer", "Tester", "Data Analyst", "Deploy", "Trainer"),
                java.util.Arrays.stream(StageType.values()).map(StageType::title).toList());
        assertEquals(StageType.TESTER, StageType.fromId("unit-test").orElseThrow());
        assertEquals(StageType.TESTER, StageType.fromId("functional-test").orElseThrow());
        assertEquals(StageType.DEVELOPER, StageType.fromId("java-developer").orElseThrow());
        assertEquals(StageType.DATA_ANALYST, StageType.fromId("analyst").orElseThrow());
        assertEquals("unit", StageType.testerPart("unit-test").orElseThrow());
        assertEquals("automation", StageType.testerPart("functional-test").orElseThrow());
        assertTrue(StageType.testerPart("tester").isEmpty());
    }

    // ------------------------------------------------------------------ offline chains

    @Test
    void offlineBankingChainRunsAllSixStages() throws Exception {
        StageService service = service(OFFLINE);
        List<StageResponse> run = service.runAll(request("banking", "fund transfer between accounts", Map.of()));

        assertEquals(6, run.size());
        assertEquals(List.of("ba", "developer", "tester", "data-analyst", "deploy", "trainer"),
                run.stream().map(StageResponse::stage).toList());
        assertEquals(1, run.stream().map(StageResponse::runId).distinct().count(), "one run folder for the chain");
        run.subList(0, 5).forEach(r -> assertEquals("template", r.mode(), r.stage()));
        assertEquals("trainer", run.get(5).mode());
        run.forEach(r -> assertFalse(r.output().isBlank(), r.stage()));
        run.forEach(r -> assertFalse(r.files().isEmpty(), r.stage()));

        StageResponse ba = run.get(0);
        assertTrue(ba.output().contains("# Feature: Fund transfer"));
        assertEquals("developer", ba.nextStage());

        StageResponse dev = run.get(1);
        assertEquals("Developer", dev.stageTitle());
        assertTrue(paths(dev).contains("src/main/java/com/bank/transfer/FundTransferService.java"));
        assertTrue(content(dev, "FundTransferService.java").contains("// Business rule:"));
        assertTrue(dev.notes().stream().anyMatch(n -> n.startsWith("Compile check skipped")), dev.notes().toString());

        StageResponse tester = run.get(2);
        assertEquals("Tester", tester.stageTitle());
        assertTrue(paths(tester).contains("src/test/java/com/bank/transfer/FundTransferServiceTest.java"), "unit tests");
        assertTrue(paths(tester).contains("selenium-java/src/test/java/com/bank/transfer/ui/FundTransferSeleniumTest.java"));
        assertTrue(paths(tester).contains("selenium-ruby/spec/fund_transfer_spec.rb"), "automation tests from the run's stories");
        assertTrue(paths(tester).contains("manual-tests/manual-test-cases.md"), "manual (functional) cases are part of the default mode");
        assertTrue(tester.nextInput().contains("FundTransferService"), "data analyst / deploy get the developer's code");

        Path dir = service.runDir(ba.runId());
        assertCompiles(dir, List.of("src/main/java", "src/test/java"));
        assertCompiles(dir, List.of("selenium-java/src/test/java"));

        StageResponse analyst = run.get(3);
        assertEquals("Data Analyst", analyst.stageTitle());
        assertTrue(paths(analyst).containsAll(List.of("analytics/STATS.md", "analytics/dashboard.html", "analytics/metrics.json")),
                paths(analyst).toString());
        assertTrue(content(analyst, "dashboard.html").contains("chart.js"), content(analyst, "dashboard.html"));
        assertTrue(content(analyst, "dashboard.html").contains("High-level overview"), content(analyst, "dashboard.html"));
        assertTrue(content(analyst, "dashboard.html").contains("Weekly transfer volume"), content(analyst, "dashboard.html"));
        assertTrue(content(analyst, "dashboard.html").contains("Download PNG"), content(analyst, "dashboard.html"));
        assertTrue(content(analyst, "STATS.md").contains("What each graph shows"), content(analyst, "STATS.md"));
        assertTrue(content(analyst, "STATS.md").contains("Where the defect estimates come from"), content(analyst, "STATS.md"));
        assertTrue(content(analyst, "STATS.md").contains("stories.md"), content(analyst, "STATS.md"));
        assertTrue(content(analyst, "STATS.md").contains("FundTransferServiceTest.java"), content(analyst, "STATS.md"));
        assertTrue(content(analyst, "dashboard.html").contains("href=\"../stories.md\""), content(analyst, "dashboard.html"));
        assertTrue(analyst.nextInput().contains("FundTransferService"), "deploy still gets the developer's code");

        StageResponse deploy = run.get(4);
        assertTrue(paths(deploy).containsAll(List.of("Dockerfile", ".dockerignore", "deploy/github-workflows/ci-cd.yml",
                "scripts/build.sh", "scripts/build.ps1", "docker-compose.yml", "k8s/deployment.yaml", "k8s/service.yaml",
                "DEPLOY.md")), paths(deploy).toString());
        assertTrue(content(deploy, "Dockerfile").contains("maven:3.9-eclipse-temurin-17"));
        assertEquals("trainer", deploy.nextStage());

        StageResponse trainer = run.get(5);
        assertTrue(trainer.output().contains("# Training report"));
        assertTrue(trainer.output().contains("| Developer | template |"), trainer.output());
        assertEquals(0, service.trainingStats().get("total"), "Run all never auto-approves");

        Set<String> zip = zipEntries(service.zip(ba.runId(), null));
        assertTrue(zip.contains(ba.runId() + "/stories.md"));
        assertTrue(zip.contains(ba.runId() + "/Dockerfile"));
        assertTrue(zip.contains(ba.runId() + "/selenium-ruby/Gemfile"));
        Set<String> testerZip = zipEntries(service.zip(ba.runId(), StageType.TESTER));
        assertFalse(testerZip.contains(ba.runId() + "/stories.md"));
        assertTrue(testerZip.contains(ba.runId() + "/src/main/java/com/bank/transfer/FundTransferService.java"));
        assertTrue(testerZip.contains(ba.runId() + "/selenium-ruby/Gemfile"));
        Set<String> deployZip = zipEntries(service.zip(ba.runId(), StageType.DEPLOY));
        assertTrue(deployZip.contains(ba.runId() + "/Dockerfile") && deployZip.contains(ba.runId() + "/pom.xml"));
        assertTrue(zipEntries(service.zip(ba.runId(), StageType.TRAINER)).contains(ba.runId() + "/training/TRAINING_REPORT.md"));

        // business logic travels with every code ZIP: a companion per code file and a stage-level BUSINESS_LOGIC.md
        String logicName = ba.runId() + "/src/main/java/com/bank/transfer/FundTransferService.business-logic.md";
        assertTrue(zip.contains(logicName), zip.toString());
        assertTrue(zip.contains(ba.runId() + "/BUSINESS_LOGIC.md"));
        assertTrue(testerZip.contains(logicName) && testerZip.contains(ba.runId() + "/BUSINESS_LOGIC.md"));
        assertTrue(deployZip.contains(ba.runId() + "/BUSINESS_LOGIC.md"));
        String stageLogic = service.businessLogic(ba.runId(), StageType.DEVELOPER, null);
        assertTrue(stageLogic.contains("FundTransferService.java"), stageLogic);
        assertTrue(service.businessLogic(ba.runId(), null, "src/main/java/com/bank/transfer/FundTransferService.java")
                .startsWith("# Business logic: src/main/java/com/bank/transfer/FundTransferService.java"));
    }

    @Test
    void offlineLogisticsChainProducesCompilableCodeTestsAndSelenium() throws Exception {
        StageService service = service(OFFLINE);
        List<StageResponse> run = service.runAll(request("logistics", "shipment tracking", Map.of()));

        assertTrue(run.get(0).output().contains("# Feature: Shipment tracking"));
        assertTrue(paths(run.get(1)).contains("src/main/java/com/logistics/tracking/ShipmentTrackingService.java"));
        assertTrue(paths(run.get(2)).contains("src/test/java/com/logistics/tracking/ShipmentTrackingServiceTest.java"));
        assertTrue(paths(run.get(2)).contains("selenium-ruby/spec/shipment_tracking_spec.rb"));

        Path dir = service.runDir(run.get(0).runId());
        assertCompiles(dir, List.of("src/main/java", "src/test/java"));
        assertCompiles(dir, List.of("selenium-java/src/test/java"));
    }

    @Test
    void offlineGenericRequirementStillCompilesInAnyDomain() throws Exception {
        for (String[] c : new String[][]{{"banking", "Loan application approval"}, {"logistics", "Delivery slot booking"},
                {"Insurance", "Motor insurance claim submission"}}) {
            StageService service = service(OFFLINE);
            List<StageResponse> run = service.runAll(request(c[0], c[1], Map.of()));
            Path dir = service.runDir(run.get(0).runId());

            assertTrue(run.get(0).output().contains("Scenario:"), c[1]);
            assertTrue(run.get(0).notes().get(0).contains("generic"), run.get(0).notes().toString());
            assertTrue(paths(run.get(2)).stream().anyMatch(p -> p.endsWith("ServiceTest.java")), c[1]);
            assertCompiles(dir, List.of("src/main/java", "src/test/java"));
            assertCompiles(dir, List.of("selenium-java/src/test/java"));
        }
    }

    @Test
    void freeTextDomainWithPythonRunsOfflineEndToEnd() throws Exception {
        StageService service = service(OFFLINE);
        List<StageResponse> run = service.runAll(request("Healthcare", "Patient appointment booking",
                Map.of("language", "python")));
        Path dir = service.runDir(run.get(0).runId());

        StageResponse ba = run.get(0);
        assertEquals("healthcare", ba.domain());
        assertTrue(ba.output().contains("Domain: Healthcare"), ba.output());
        assertTrue(ba.output().contains("As a patient"), "domain-specific role");

        StageResponse dev = run.get(1);
        assertEquals("python", dev.language());
        assertTrue(paths(dev).contains("app/patient_appointment.py"), paths(dev).toString());
        String code = content(dev, "patient_appointment.py");
        assertTrue(code.contains("# Business rule:") && code.contains("\"\"\""), "commented Python");
        assertTrue(code.contains("HEA-"), "domain reference prefix");
        assertTrue(dev.notes().stream().anyMatch(n -> n.startsWith("Compile check skipped") || n.startsWith("Syntax check")),
                dev.notes().toString());

        StageResponse tester = run.get(2);
        assertTrue(paths(tester).contains("tests/test_patient_appointment.py"), paths(tester).toString());
        assertTrue(paths(tester).contains("selenium-python/tests/test_patient_appointment.py"), "Selenium in the code's language");
        assertTrue(Files.exists(dir.resolve("pytest.ini")) && Files.exists(dir.resolve("selenium-python/conftest.py")));

        StageResponse analyst = run.get(3);
        assertTrue(paths(analyst).contains("analytics/dashboard.html"), paths(analyst).toString());

        StageResponse deploy = run.get(4);
        assertTrue(content(deploy, "Dockerfile").contains("python:3.12-slim"));
        assertTrue(content(deploy, "Dockerfile").contains("\"python\", \"-m\", \"app.patient_appointment\""), "entry point found");
        assertTrue(content(deploy, "ci-cd.yml").contains("actions/setup-python@v5"));
        assertTrue(content(deploy, "ci-cd.yml").contains("python -m pytest -q"));

        assertTrue(run.get(5).output().contains("domain **Healthcare**"), run.get(5).output());

        // The generated Python suite really passes.
        Assumptions.assumeTrue(pytestAvailable(), "python with pytest not installed");
        Process p = new ProcessBuilder("python", "-m", "pytest", "-q", "tests").directory(dir.toFile())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), out);
    }

    @Test
    void offlineLanguagesWithoutTemplatesAskForAModel() {
        StageService service = service(OFFLINE);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.run(StageType.DEVELOPER,
                request("Telecom", "# Feature: SIM swap\nAs a subscriber, I want a SIM swap.", Map.of("language", "rust"))));
        assertTrue(e.getMessage().contains("Start Ollama") && e.getMessage().contains("Rust"), e.getMessage());
        assertTrue(e.getMessage().contains("Kotlin"), "lists the offline languages: " + e.getMessage());
        IllegalArgumentException scala = assertThrows(IllegalArgumentException.class, () -> service.run(StageType.DEVELOPER,
                request("Telecom", "# Feature: SIM swap", Map.of("language", "scala"))));
        assertTrue(scala.getMessage().contains("Scala code"), scala.getMessage());

        StageResponse deploy = service.run(StageType.DEPLOY, request("Telecom", "package main\n\nfunc main() {}\n",
                Map.of("language", "go")));
        assertTrue(content(deploy, "Dockerfile").contains("golang:1.22"), "deploy templates need no model");
        assertTrue(paths(deploy).contains("go.mod"));
    }

    @Test
    void developerUsesTheModelForGoCSharpAndCatalogLanguages() {
        FakeModel model = new FakeModel();
        StageService service = service(model);
        String stories = "# Feature: Patient appointment booking\nAs a patient, I want to book an appointment.";

        StageResponse go = service.run(StageType.DEVELOPER, request("Healthcare", stories, Map.of("language", "go")));
        assertEquals("llm", go.mode());
        assertEquals("go", go.language());
        assertTrue(paths(go).stream().anyMatch(p -> p.endsWith(".go")), paths(go).toString());
        assertTrue(model.systems.stream().anyMatch(s -> s.contains("Go developer for Healthcare")));

        StageResponse cs = service.run(StageType.DEVELOPER, request("Healthcare", stories, Map.of("language", "csharp")));
        assertTrue(paths(cs).contains("src/App/AppointmentService.cs"), paths(cs).toString());
        assertTrue(model.prompt("Write the complete C# implementation").contains("Patient appointment booking"));

        StageResponse scala = service.run(StageType.DEVELOPER, request("Insurance", stories, Map.of("language", "scala")));
        assertEquals("scala", scala.language());
        assertTrue(paths(scala).stream().anyMatch(p -> p.endsWith(".scala")), paths(scala).toString());

        StageResponse typed = service.run(StageType.DEVELOPER, request("Insurance", stories,
                Map.of("language", "other", "languageName", "Scala")));
        assertEquals("scala", typed.language(), "typed names resolve against the catalog");
    }

    @Test
    void offlineTemplatesCoverJavaScriptTypeScriptCSharpGoAndKotlin() {
        StageService service = service(OFFLINE);
        Map<String, String> ext = Map.of("javascript", ".js", "typescript", ".ts", "csharp", ".cs", "go", ".go", "kotlin", ".kt");
        for (String lang : List.of("javascript", "typescript", "csharp", "go", "kotlin")) {
            for (String[] c : List.of(new String[]{"Banking", "# Feature: Fund transfer\nAs a customer, I want to transfer money."},
                    new String[]{"Healthcare", "# Feature: Prescription refill\nAs a patient, I want a refill."})) {
                boolean fund = c[0].equals("Banking");
                StageResponse dev = service.run(StageType.DEVELOPER, request(c[0], c[1], Map.of("language", lang)));
                assertEquals("template", dev.mode(), lang);
                List<StageFile> code = dev.files().stream().filter(f -> f.path().endsWith(ext.get(lang))).toList();
                assertFalse(code.isEmpty(), lang + ": " + paths(dev));
                String all = code.stream().map(StageFile::content).reduce("", String::concat);
                assertFalse(all.contains("{{"), lang + " has unfilled placeholders");
                assertTrue(all.contains(fund ? "FundTransferService" : "PrescriptionRefillService"), lang + ": " + all);
                assertTrue(all.contains("Business rule"), lang);

                StageResponse tests = service.run(StageType.TESTER, new StageRequest(c[0], dev.output(),
                        Map.of("language", lang, "testerPart", "unit"), dev.runId()));
                String suite = tests.files().stream().filter(f -> f.path().endsWith(ext.get(lang)))
                        .map(StageFile::content).reduce("", String::concat);
                assertFalse(suite.isBlank(), lang + ": " + paths(tests));
                assertFalse(suite.contains("generated offline by BOT"), lang + " should get the full suite");
                assertFalse(suite.contains("{{"), lang);
                assertTrue(suite.contains(fund ? "DAILY_LIMIT" : "PrescriptionRefill")
                        || suite.contains(fund ? "DailyLimit" : "PrescriptionRefill"), lang + ": " + suite);
            }
        }
        StageResponse cs = service.run(StageType.TESTER, request("Banking",
                "=== FILE: src/App/Thing.cs ===\nnamespace App;\npublic class Thing { public int Twice(int x) => x * 2; }\n",
                Map.of("language", "csharp", "testerPart", "unit")));
        assertTrue(content(cs, "ThingTests.cs").contains("generated offline by BOT"), "other code gets a skeleton");
        assertTrue(paths(cs).contains("tests/App.Tests/App.Tests.csproj"));
    }

    // ------------------------------------------------------------------ deploy

    @Test
    void deployHasRecipesForCatalogLanguages() {
        StageService service = service(OFFLINE);
        StageResponse scala = service.run(StageType.DEPLOY, request("Retail", "object Main extends App {}", Map.of("language", "scala")));
        assertTrue(content(scala, "Dockerfile").contains("sbt -batch test assembly"));
        assertTrue(paths(scala).contains("build.sbt"));
        StageResponse elixir = service.run(StageType.DEPLOY, request("Retail", "defmodule Shop do\nend", Map.of("language", "elixir")));
        assertTrue(content(elixir, "Dockerfile").contains("mix test"));
        assertTrue(content(elixir, "mix.exs").contains("defmodule"));
        StageResponse lua = service.run(StageType.DEPLOY, request("Retail", "print('hi')", Map.of("language", "lua")));
        assertTrue(content(lua, "Dockerfile").contains("TODO"), "generic recipe for other catalog languages");
        assertTrue(content(lua, "DEPLOY.md").contains("Lua"));
    }

    @Test
    void deployStageWritesDockerfileAndWorkflowForJavaAndPython() throws Exception {
        StageService service = service(OFFLINE);
        String java = """
                package com.shop.app;

                /** Entry point. */
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """;
        StageResponse j = service.run(StageType.DEPLOY, request("Retail/E-commerce", java, Map.of("port", "9090")));
        assertEquals("deploy", j.stage());
        String dockerfile = content(j, "Dockerfile");
        assertTrue(dockerfile.contains("FROM maven:3.9-eclipse-temurin-17 AS build"));
        assertTrue(dockerfile.contains("CMD [\"java\", \"-cp\", \"app.jar\", \"com.shop.app.Main\"]"), dockerfile);
        assertTrue(dockerfile.contains("EXPOSE 9090"));
        String workflow = content(j, "ci-cd.yml");
        assertTrue(workflow.contains("actions/setup-java@v4") && workflow.contains("mvn -B -q test"), workflow);
        assertTrue(workflow.contains("docker/build-push-action@v6") && workflow.contains("vars.DEPLOY_ENABLED == 'true'"));
        assertTrue(content(j, "DEPLOY.md").contains("docker build -t"));
        Path dir = service.runDir(j.runId());
        assertTrue(Files.exists(dir.resolve("src/main/java/com/shop/app/Main.java")), "pasted code copied into the run");
        assertTrue(Files.exists(dir.resolve("pom.xml")));

        String python = """
                def greet(name):
                    \"\"\"Says hello.\"\"\"
                    return "Hello " + name


                if __name__ == "__main__":
                    print(greet("BOT"))
                """;
        StageResponse py = service.run(StageType.DEPLOY, request("Education", python,
                Map.of("language", "python", "kubernetes", "false", "compose", "false")));
        assertTrue(content(py, "Dockerfile").contains("FROM python:3.12-slim"));
        assertTrue(content(py, "ci-cd.yml").contains("actions/setup-python@v5"));
        assertFalse(paths(py).contains("k8s/deployment.yaml"), "Kubernetes is optional");
        assertFalse(paths(py).contains("docker-compose.yml"));
        assertTrue(paths(py).contains("requirements.txt"));
    }

    // ------------------------------------------------------------------ independence

    @Test
    void testerWritesManualFunctionalCasesFromStoriesAsStepsAndExpectedResults() throws Exception {
        StageService service = service(OFFLINE);
        StageResponse ba = service.run(StageType.BA, request("banking", "fund transfer between accounts", Map.of()));
        StageResponse manual = service.run(StageType.TESTER, new StageRequest("banking", "", Map.of("testerPart", "manual"), ba.runId())
                .withInput("public class FundTransferService { public void transfer(long cents) { } }", ba.runId()));

        assertTrue(paths(manual).contains("manual-tests/manual-test-cases.md"));
        assertTrue(paths(manual).stream().noneMatch(p -> p.startsWith("src/test/") || StageAutomation.isAutomationPath(p)),
                "manual mode writes no unit or automation tests: " + paths(manual));
        String cases = content(manual, "manual-test-cases.md");
        assertTrue(cases.contains("TC-001") && cases.contains("**Preconditions**") && cases.contains("**Steps**")
                && cases.contains("1. ") && cases.contains("**Expected result**"), cases);
        assertTrue(cases.contains("Written from the user stories"), "built from the run's Given/When/Then stories");
        assertThrows(IllegalArgumentException.class,
                () -> service.run(StageType.TESTER, request("banking", "just some words", Map.of("testerPart", "manual"))),
                "no stories and no code: nothing to write cases from");
    }

    @Test
    void businessAnalystCanPassUploadedStoriesStraightThrough() {
        StageService service = service(OFFLINE);
        String stories = "# Story 1: Login\nAs a user, I want to log in, so that I can see my account.\n"
                + "Scenario: Valid login\n  Given a registered user\n  When they sign in\n  Then they see the dashboard\n";
        StageResponse ba = service.run(StageType.BA, request("banking", stories, Map.of("baMode", "passthrough")));
        assertEquals("passthrough", ba.mode());
        assertEquals(stories.strip(), ba.output().strip(), "stories are kept exactly as given");
        StageResponse refineOffline = service.run(StageType.BA, request("banking", stories, Map.of("baMode", "refine")));
        assertEquals("passthrough", refineOffline.mode(), "no model: refine falls back to as-is");
        assertTrue(refineOffline.notes().stream().anyMatch(n -> n.contains("No AI model")), refineOffline.notes().toString());
    }

    @Test
    void exportFilesCopiesAStageForGitWithoutRunBookkeeping() throws Exception {
        StageService service = service(OFFLINE);
        List<StageResponse> run = service.runAll(request("banking", "fund transfer between accounts", Map.of()));
        String id = run.get(0).runId();
        Path dev = Files.createDirectories(tmp.resolve("export-dev"));
        assertTrue(service.exportFiles(id, StageType.DEVELOPER, dev) > 0);
        assertTrue(Files.exists(dev.resolve("src/main/java/com/bank/transfer/FundTransferService.java")));
        assertFalse(Files.exists(dev.resolve("stories.md")));
        Path all = Files.createDirectories(tmp.resolve("export-all"));
        service.exportFiles(id, null, all);
        assertTrue(Files.exists(all.resolve("stories.md")) && Files.exists(all.resolve("Dockerfile")));
        assertTrue(Files.exists(all.resolve("manual-tests/manual-test-cases.md")));
        assertFalse(Files.exists(all.resolve("run.json")));
        assertFalse(Files.exists(all.resolve("training")));
    }

    @Test
    void testerWorksAloneOnPastedCode() throws Exception {
        StageService service = service(OFFLINE);
        String pasted = """
                package com.bank.fees;

                public class FeeCalculator {
                    public long fee(long amountCents) {
                        return amountCents > 100_000 ? 0 : 250;
                    }
                }
                """;
        StageResponse unit = service.run(StageType.TESTER, request("banking", pasted, Map.of("testerPart", "unit")));

        assertEquals("template", unit.mode());
        assertTrue(paths(unit).contains("src/test/java/com/bank/fees/FeeCalculatorTest.java"));
        assertTrue(paths(unit).stream().noneMatch(StageAutomation::isAutomationPath), "unit part only");
        assertTrue(paths(unit).contains("src/main/java/com/bank/fees/FeeCalculator.java"), "code under test is copied into the run");
        assertTrue(content(unit, "FeeCalculatorTest.java").contains("fee(long amountCents)"), "TODO lists the method");
        assertTrue(unit.nextInput().contains("class FeeCalculator"), "deploy can take the same code");
        assertCompiles(service.runDir(unit.runId()), List.of("src/main/java", "src/test/java"));
    }

    @Test
    void testerFallsBackToTemplatesAndStopsCallingAFailingModel() {
        List<String> prompts = new ArrayList<>();
        LlmClient failing = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public synchronized String complete(String systemPrompt, String userPrompt) {
                prompts.add(userPrompt);
                throw new IllegalStateException("the local model did not answer within 90 seconds");
            }
        };
        String pasted = """
                package com.bank.transfer;

                public class TransferService {
                    public long transfer(long fromBalance, long amount) {
                        if (amount <= 0 || amount > fromBalance) throw new IllegalArgumentException("bad amount");
                        return fromBalance - amount;
                    }
                }
                """;
        StageResponse both = service(failing).run(StageType.TESTER, request("banking", pasted,
                Map.of("testerPart", "both", "automation", "both")));

        assertEquals(1, prompts.size(), "Tester asks the model once for unit tests");
        assertTrue(paths(both).contains("src/test/java/com/bank/transfer/TransferServiceTest.java"), paths(both).toString());
        assertTrue(paths(both).stream().anyMatch(p -> p.startsWith("selenium-java/")), paths(both).toString());
        assertTrue(paths(both).stream().anyMatch(p -> p.startsWith("selenium-ruby/")), paths(both).toString());
        assertTrue(both.notes().stream().anyMatch(n -> n.contains("did not answer within 90 seconds")), both.notes().toString());
        assertTrue(both.notes().stream().anyMatch(n -> n.contains("built-in template so Tester stays fast")), both.notes().toString());
    }

    @Test
    void stagesStartNewRunsUnlessARunIdIsGiven() {
        StageService service = service(OFFLINE);
        StageResponse a = service.run(StageType.BA, request("banking", "fund transfer", Map.of()));
        StageResponse b = service.run(StageType.BA, request("banking", "fund transfer", Map.of()));
        assertNotEquals(a.runId(), b.runId());

        StageResponse dev = service.run(StageType.DEVELOPER, new StageRequest("banking", a.output(), Map.of(), a.runId()));
        assertEquals(a.runId(), dev.runId());
    }

    @Test
    void testerHonoursAutomationChoiceAndBaseUrl() throws Exception {
        StageService service = service(OFFLINE);
        StageResponse ruby = service.run(StageType.TESTER, request("logistics", "# Feature: Shipment tracking\nScenario: Track\n  Given a parcel\n",
                Map.of("automation", "ruby", "testerPart", "automation", "baseUrl", "https://staging.example.com/")));
        assertTrue(paths(ruby).stream().allMatch(p -> p.startsWith("selenium-ruby/")), paths(ruby).toString());
        assertTrue(Files.readString(service.runDir(ruby.runId()).resolve("selenium-ruby/spec/spec_helper.rb"))
                .contains("https://staging.example.com'"));

        StageResponse java = service.run(StageType.TESTER, request("banking", "fund transfer",
                Map.of("automation", "java", "testerPart", "automation", "baseUrl", "javascript:alert(1)")));
        assertTrue(paths(java).stream().allMatch(p -> p.startsWith("selenium-java/")));
        assertTrue(content(java, "FundTransferSeleniumTest.java").contains("\"http://localhost:8080\""), "unsafe URL replaced");

        StageResponse playwright = service.run(StageType.TESTER, request("Travel",
                "# Feature: Flight booking\nScenario: Book a seat\n  Given a flight\n  When I book\n  Then I get a ticket\n",
                Map.of("language", "typescript", "testerPart", "automation")));
        assertTrue(paths(playwright).contains("playwright-ts/tests/flight-booking.spec.ts"), paths(playwright).toString());
        assertTrue(content(playwright, "flight-booking.spec.ts").contains("test('Book a seat'"));
    }

    @Test
    void rejectsEmptyInputAndCodeWithoutClasses() {
        StageService service = service(OFFLINE);
        assertThrows(IllegalArgumentException.class, () -> service.run(StageType.BA, request("banking", "  ", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> service.run(StageType.TESTER,
                request("banking", "just some words", Map.of("testerPart", "unit"))));
        assertThrows(IllegalArgumentException.class, () -> service.run(StageType.DATA_ANALYST, request("banking", " ", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> service.run(StageType.DEPLOY, request("banking", " ", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> service.runDir("..\\..\\windows"));
    }

    @Test
    void dataAnalystAloneWritesExplainedDashboard() {
        StageService service = service(OFFLINE);
        StageResponse analyst = service.run(StageType.DATA_ANALYST, request("banking",
                "package com.bank.transfer;\npublic class FundTransferService { public void transfer() {} }\n",
                Map.of("offline", "true")));
        assertTrue(paths(analyst).contains("analytics/dashboard.html"), paths(analyst).toString());
        assertTrue(content(analyst, "dashboard.html").contains("High-level overview"));
        assertTrue(content(analyst, "dashboard.html").contains("Weekly transfer volume"));
        assertTrue(content(analyst, "STATS.md").contains("What each graph shows"));
        assertTrue(content(analyst, "STATS.md").contains("No stories.md or Tester files were in this run yet"));
    }

    @Test
    void recordGuardrailWritesRunReport() throws Exception {
        StageService service = service(OFFLINE);
        StageResponse ba = service.run(StageType.BA, request("banking", "fund transfer between accounts", Map.of()));
        service.recordGuardrail(ba.runId(), "run_stage", com.agentic.chatbot.guardrail.GuardDecision.allow());
        Path report = service.runDir(ba.runId()).resolve("GUARDRAIL.md");
        assertTrue(Files.exists(report));
        String md = Files.readString(report);
        assertTrue(md.contains("not a seventh pipeline stage"), md);
        assertTrue(md.contains("run_stage"), md);
        Map<String, Object> run = service.runRecord(ba.runId());
        assertTrue(run.get("guardrail") instanceof java.util.List, run.toString());
    }

    // ------------------------------------------------------------------ chaining through the model

    @Test
    void chainedStagesFeedEachOtherThroughTheModel() throws Exception {
        FakeModel model = new FakeModel();
        StageService service = service(model);
        List<StageResponse> run = service.runAll(request("banking", "fund transfer with a ZEBRA42 limit", Map.of()));

        run.subList(0, 3).forEach(r -> assertEquals("llm", r.mode(), r.stage() + " " + r.notes()));
        assertEquals("template", run.get(3).mode(), "data analyst files are deterministic");
        assertEquals("template", run.get(4).mode(), "deploy files are deterministic");
        assertTrue(run.get(0).output().contains("ZEBRA42"));
        assertTrue(model.prompt("Write the complete Java 17 implementation").contains("ZEBRA42"),
                "developer writes every class from the BA stories in one call");
        assertEquals(1, model.users.stream().filter(u -> u.contains("Write the complete Java 17 implementation")).count(),
                "Java developer uses a single model call");
        assertTrue(paths(run.get(1)).contains("src/main/java/com/bank/transfer/TransferService.java"));
        assertTrue(run.get(1).notes().stream().anyMatch(n -> n.contains("in one model call")), run.get(1).notes().toString());
        assertTrue(run.get(1).notes().stream().anyMatch(n -> n.startsWith("Compile check skipped")), run.get(1).notes().toString());
        assertTrue(model.prompt("Write the JUnit 5 test class").contains("class TransferService"),
                "tester tests the developer's code");
        assertEquals(1, model.users.stream().filter(u -> u.contains("Write the JUnit 5 test class")).count(),
                "Java unit tests use a single model call");
        assertTrue(model.users.stream().noneMatch(u -> u.contains("Write Selenium WebDriver")),
                "automation uses the built-in template");
        assertTrue(paths(run.get(2)).contains("selenium-ruby/spec/fund_transfer_spec.rb"), paths(run.get(2)).toString());
        assertTrue(paths(run.get(2)).contains("selenium-java/src/test/java/com/bank/transfer/ui/FundTransferPage.java"));
        assertCompiles(service.runDir(run.get(0).runId()), List.of("src/main/java", "src/test/java"));
    }

    @Test
    void languageAndDomainReachThePrompts() {
        FakeModel model = new FakeModel();
        StageService service = service(model);
        String stories = "# Feature: Patient booking\nAs a patient, I want to book.\nScenario: Book\n  Given a slot\n";
        StageResponse dev = service.run(StageType.DEVELOPER, request("Healthcare", stories, Map.of("language", "python")));

        assertEquals("llm", dev.mode(), dev.notes().toString());
        String system = model.systems.get(model.systems.size() - 1);
        assertTrue(system.startsWith("You are a Python developer for Healthcare"), system);
        assertTrue(model.prompt("Write the complete Python implementation").contains("Patient booking"));
        assertTrue(paths(dev).contains("app/patient_booking.py"), paths(dev).toString());

        StageResponse tester = service.run(StageType.TESTER, new StageRequest("Healthcare",
                dev.output() + "\n=== FILE: stories.md ===\n" + stories, Map.of("language", "python"), dev.runId()));
        assertTrue(model.prompt("Write pytest unit tests in Python").contains("def book"), "unit tests see the code");
        assertTrue(model.users.stream().noneMatch(u -> u.contains("Write UI automation tests using")),
                "automation uses the built-in template");
        assertTrue(paths(tester).contains("tests/test_patient_booking.py"), paths(tester).toString());
        assertTrue(paths(tester).stream().anyMatch(p -> p.startsWith("selenium-python/tests/")), paths(tester).toString());

        service.run(StageType.BA, request("Space tourism", "Orbital trip booking", Map.of()));
        assertTrue(model.systems.get(model.systems.size() - 1)
                .startsWith("You are a Business Analyst for the Space tourism domain"));

        service.run(StageType.DEVELOPER, request("Banking", stories, Map.of("language", "other", "languageName", "Elixir")));
        assertTrue(model.systems.stream().anyMatch(s -> s.startsWith("You are a Elixir developer for Banking")
                || s.startsWith("You are an Elixir developer for Banking")));
    }

    @Test
    void modelFailureFallsBackToTheTemplate() {
        LlmClient broken = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("connection refused");
            }
        };
        StageResponse ba = service(broken).run(StageType.BA, request("logistics", "shipment tracking", Map.of()));
        assertEquals("template", ba.mode());
        assertTrue(ba.notes().get(0).contains("connection refused"), ba.notes().toString());
        assertTrue(ba.output().contains("Shipment tracking"));
    }

    @Test
    void developerCodeThatDoesNotCompileFallsBackToTheTemplateUnlessKept() {
        List<String> users = new ArrayList<>();
        LlmClient sloppy = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public synchronized String complete(String systemPrompt, String userPrompt) {
                users.add(userPrompt);
                return "FILE: Account.java\n```java\npackage com.bank.transfer;\npublic class Account { int x = ; }\n```\n"
                        + "FILE: TransferService.java\n```java\npackage com.bank.transfer;\npublic class TransferService { int y = ; }\n```";
            }
        };
        String stories = "# Feature: Fund transfer\nAs a customer, I want to transfer funds.";

        StageResponse strict = service(sloppy).run(StageType.DEVELOPER, request("banking", stories, Map.of()));
        assertEquals("llm", strict.mode());
        assertTrue(users.stream().anyMatch(u -> u.contains("Write the complete Java 17 implementation")),
                "developer asks for every class in one call: " + users);
        assertEquals(0, users.stream().filter(u -> u.contains("does not compile")).count(),
                "no compile-repair round trip");
        assertTrue(strict.notes().stream().anyMatch(n -> n.startsWith("Compile check skipped")), strict.notes().toString());
        assertTrue(strict.notes().stream().anyMatch(n -> n.contains("have no Javadoc")), strict.notes().toString());
    }

    @Test
    void offlineOptionSkipsTheModel() {
        StageResponse ba = service(new FakeModel()).run(StageType.BA,
                request("banking", "fund transfer", Map.of("offline", "true")));
        assertEquals("template", ba.mode());
    }

    // ------------------------------------------------------------------ training

    @Test
    void approvedExamplesBecomeFewShotPromptsAndOfflineRecall() throws Exception {
        FakeModel model = new FakeModel();
        StageService online = service(model);
        String corrected = "# Feature: Fund transfer\n\n## Story 1: Corrected by reviewer\nAs a customer, I want X.\n"
                + "Scenario: ok\n  Given A\n  When B\n  Then C\n";
        StageResponse first = online.run(StageType.BA, request("banking", "fund transfer between accounts", Map.of()));
        Map<String, Object> approved = online.approve(StageType.BA, "banking", "fund transfer between accounts",
                corrected, "Always include a corrected story", first.runId());

        assertEquals(1, approved.get("examples"));
        assertEquals(corrected.strip() + "\n", Files.readString(online.runDir(first.runId()).resolve("stories.md")),
                "approving an edited output rewrites the run file");
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Integer>> stages = (Map<String, Map<String, Integer>>) online.trainingStats().get("stages");
        assertEquals(1, stages.get("ba").get("banking"));

        online.run(StageType.BA, request("banking", "fund transfer to another bank", Map.of()));
        String system = model.systems.get(model.systems.size() - 1);
        assertTrue(system.contains("Approved examples"), "few-shot block added");
        assertTrue(system.contains("Corrected by reviewer"));
        assertTrue(system.contains("Always include a corrected story"));

        StageResponse other = online.run(StageType.BA, request("logistics", "fund transfer between accounts", Map.of()));
        assertFalse(model.systems.get(model.systems.size() - 1).contains("Corrected by reviewer"), "examples are per domain");
        assertEquals("llm", other.mode());

        StageResponse recalled = service(OFFLINE).run(StageType.BA,
                request("banking", "Fund transfer between accounts.", Map.of()));
        assertEquals("trained", recalled.mode());
        assertTrue(recalled.output().contains("Corrected by reviewer"));
        assertTrue(recalled.notes().get(0).startsWith("Recalled a reviewer-approved"), recalled.notes().toString());

        StageResponse unrelated = service(OFFLINE).run(StageType.BA, request("banking", "credit card dispute", Map.of()));
        assertEquals("template", unrelated.mode());
    }

    @Test
    void approvedDeveloperBundleIsRecalledAsFiles() throws Exception {
        StageService service = service(OFFLINE);
        StageResponse dev = service.run(StageType.DEVELOPER, request("banking", "fund transfer between accounts", Map.of()));
        String edited = dev.output().replace("Moves money between accounts", "Moves money between accounts (reviewed)");
        service.approve(StageType.DEVELOPER, "banking", "fund transfer between accounts", edited, "", dev.runId());

        StageResponse again = service.run(StageType.DEVELOPER, request("banking", "fund transfer between accounts", Map.of()));
        assertEquals("trained", again.mode());
        assertTrue(content(again, "FundTransferService.java").contains("(reviewed)"));
        assertEquals(paths(dev), paths(again));
        assertTrue(again.notes().stream().anyMatch(n -> n.startsWith("Compile check skipped")), again.notes().toString());

        StageResponse python = service.run(StageType.DEVELOPER, request("banking", "fund transfer between accounts",
                Map.of("language", "python")));
        assertEquals("template", python.mode(), "examples are kept per language for code stages");
    }

    @Test
    void trainerSavesARunAndManagesExamples() {
        StageService service = service(OFFLINE);
        List<StageResponse> run = service.runAll(request("Healthcare", "Patient appointment booking",
                Map.of("language", "python")));
        String runId = run.get(0).runId();

        StageResponse report = service.run(StageType.TRAINER, new StageRequest("Healthcare", runId,
                Map.of("save", "true", "feedback", "good run"), null));
        assertEquals(runId, report.runId());
        assertTrue(report.notes().get(0).startsWith("Saved 5 training example(s)"), report.notes().toString());
        assertTrue(report.output().contains("## Saved now"));
        assertEquals(5, service.trainingStats().get("total"));

        List<Map<String, String>> examples = service.trainingExamples("developer", "", 10);
        assertEquals(1, examples.size());
        assertEquals("healthcare:python", examples.get(0).get("domain"));
        assertEquals("good run", examples.get(0).get("feedback"));
        assertEquals(1, service.trainingExamples("ba", "healthcare", 10).size());

        assertTrue(service.deleteTrainingExample(examples.get(0).get("id")));
        assertFalse(service.deleteTrainingExample(examples.get(0).get("id")));
        assertEquals(4, service.trainingStats().get("total"));

        StageResponse alone = service.run(StageType.TRAINER, request("banking", "", Map.of()));
        assertTrue(alone.output().contains("What BOT has learned"));
        assertThrows(IllegalArgumentException.class, () -> service.run(StageType.TRAINER,
                request("banking", "", Map.of("save", "true"))));
        assertThrows(IllegalArgumentException.class, () -> service.approve(StageType.TRAINER, "banking", "x", "y", "", null));
    }

    @Test
    void parsesClassPlansFromLooseModelAnswers() {
        assertEquals(List.of("Account", "TransferService", "TransferException"), StageService.parsePlan("""
                Here are the classes:
                1. **Account** - holds the balance
                2. `TransferService.java`: moves money
                - TransferException \u2013 business rule failures
                """));
        assertTrue(StageService.similarity("Fund transfer between accounts", "fund transfer between accounts.") > 0.99);
    }

    @Test
    void domainsAndLanguagesParseFreeText() {
        assertEquals(StageDomain.BANKING, StageDomain.from(""));
        assertTrue(StageDomain.from("Banking") == StageDomain.BANKING);
        assertTrue(StageDomain.from("logistics & shipping") == StageDomain.LOGISTICS);
        StageDomain retail = StageDomain.from("Retail/E-commerce");
        assertEquals("retail-e-commerce", retail.id());
        assertEquals("shopper", retail.customerRole());
        assertEquals("com.retailecommerce", retail.basePackage());
        assertEquals(StageLanguage.CSHARP, StageLanguage.from("C#"));
        assertEquals(StageLanguage.PLSQL, StageLanguage.from("PL/SQL"));
        assertEquals(StageLanguage.OTHER, StageLanguage.from("Elixir"));
        assertEquals(StageAutomation.PLAYWRIGHT_TS, StageAutomation.choose("", StageLanguage.TYPESCRIPT));
        assertEquals(StageAutomation.SELENIUM_JAVA, StageAutomation.choose("", StageLanguage.KOTLIN));
        assertEquals(StageAutomation.SELENIUM_RUBY, StageAutomation.choose("ruby", StageLanguage.JAVA));
    }

    // ------------------------------------------------------------------ helpers

    private static boolean pytestAvailable() {
        try {
            Process p = new ProcessBuilder("python", "-c", "import pytest").redirectErrorStream(true).start();
            return p.waitFor(30, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> paths(StageResponse r) {
        return r.files().stream().map(StageFile::path).toList();
    }

    private static String content(StageResponse r, String fileName) {
        return r.files().stream().filter(f -> f.path().endsWith("/" + fileName) || f.path().equals(fileName)).findFirst()
                .orElseThrow(() -> new AssertionError(fileName + " not in " + paths(r))).content();
    }

    private static void assertCompiles(Path runDir, List<String> sourceRoots) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String rootName : sourceRoots) {
            Path root = runDir.resolve(rootName);
            try (var walk = Files.walk(root)) {
                for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                    sources.put(runDir.relativize(p).toString().replace('\\', '/'), Files.readString(p));
                }
            }
        }
        assertFalse(sources.isEmpty(), "no Java under " + sourceRoots);
        JavaCompileCheck.Result result = JavaCompileCheck.compile(sources, testClasspath());
        assertFalse(result.skipped(), "tests must run on a JDK");
        assertTrue(result.ok(), String.join("\n", result.errors()));
    }

    private static List<Path> testClasspath() throws Exception {
        Set<Path> cp = new LinkedHashSet<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.isBlank()) {
                cp.add(Path.of(entry));
            }
        }
        for (Class<?> c : List.of(Test.class, org.junit.jupiter.api.Assertions.class, WebDriver.class, WebDriverWait.class,
                ChromeDriver.class, ChromiumDriver.class, RemoteWebDriver.class)) {
            cp.add(Path.of(c.getProtectionDomain().getCodeSource().getLocation().toURI()));
        }
        return new ArrayList<>(cp);
    }

    private static Set<String> zipEntries(byte[] zip) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        return names;
    }

    /** Answers each stage prompt with small, valid output and records every prompt it was sent. */
    private static final class FakeModel implements LlmClient {
        final List<String> users = new ArrayList<>();
        final List<String> systems = new ArrayList<>();
        private static final Pattern FILE = Pattern.compile("complete file (\\w+)\\.java");

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String describe() {
            return "fake-model";
        }

        String prompt(String marker) {
            return users.stream().filter(u -> u.contains(marker)).findFirst()
                    .orElseThrow(() -> new AssertionError("no prompt containing: " + marker));
        }

        @Override
        public synchronized String complete(String systemPrompt, String userPrompt) {
            systems.add(systemPrompt);
            users.add(userPrompt);
            if (userPrompt.contains("Write the user stories now")) {
                String requirement = userPrompt.lines().skip(1).findFirst().orElse("");
                return "# Feature: Fund transfer\n\n## Story 1: Send money\nAs a customer, I want to " + requirement
                        + ", so that I can pay people.\n\n### Acceptance criteria\nScenario: Successful transfer\n"
                        + "  Given an active account\n  When I transfer 10.00\n  Then the money moves\n";
            }
            if (userPrompt.contains("Write the complete Java 17 implementation")
                    || userPrompt.contains("List the Java classes")) {
                if (userPrompt.contains("List the Java classes")) {
                    return "1. Account - holds the balance\n2. TransferService - moves money";
                }
                return "FILE: Account.java\n```java\npackage com.bank.transfer;\n\n/** Account. */\npublic class Account {\n"
                        + "    private long balance;\n    /** @return balance */\n    public long getBalance() { return balance; }\n"
                        + "    /** Business rule: no overdraft. */\n    public void debit(long cents) {\n"
                        + "        if (cents > balance) throw new IllegalArgumentException(\"Insufficient funds\");\n"
                        + "        balance -= cents;\n    }\n    public void credit(long cents) { balance += cents; }\n}\n```\n"
                        + "FILE: TransferService.java\n```java\npackage com.bank.transfer;\n\n/** Moves money. */\npublic class TransferService {\n"
                        + "    /** Transfers. */\n    public void transfer(Account from, Account to, long cents) {\n"
                        + "        from.debit(cents);\n        to.credit(cents);\n    }\n}\n```";
            }
            if (userPrompt.contains("Write the complete Python implementation")) {
                return "FILE: app/patient_booking.py\n```python\n\"\"\"Bookings.\"\"\"\n\n\ndef book(slot):\n"
                        + "    \"\"\"Books a slot.\"\"\"\n    # Business rule: a slot is required.\n"
                        + "    if not slot:\n        raise ValueError(\"Slot is required\")\n    return slot\n```";
            }
            if (userPrompt.contains("Write the complete Go implementation")) {
                return "```go\npackage main\n\n// Book books an appointment.\nfunc Book(slot string) string {\n"
                        + "    // Business rule: a slot is required.\n    return slot\n}\n```";
            }
            if (userPrompt.contains("Write the complete C# implementation")) {
                return "FILE: src/App/AppointmentService.cs\n```csharp\nnamespace App;\n\n/// <summary>Books appointments.</summary>\n"
                        + "public class AppointmentService\n{\n    public string Book(string slot) => slot;\n}\n```";
            }
            if (userPrompt.contains("Write the complete Scala implementation")) {
                return "```scala\n/** Books appointments. */\nobject Appointments {\n  def book(slot: String): String = slot\n}\n```";
            }
            if (userPrompt.contains("Write the complete")) {
                return "```\n# code\n```";
            }
            if (userPrompt.contains("Write pytest unit tests")) {
                return "FILE: tests/test_patient_booking.py\n```python\nfrom app.patient_booking import book\n\n\n"
                        + "def test_book():\n    assert book('9am') == '9am'\n```";
            }
            if (userPrompt.contains("Write UI automation tests using")) {
                return "FILE: tests/test_booking.py\n```python\ndef test_page(driver, base_url):\n    driver.get(base_url)\n```";
            }
            Matcher m = FILE.matcher(userPrompt);
            if (m.find()) {
                return m.group(1).equals("Account")
                        ? "```java\npackage com.bank.transfer;\n\n/** Account. */\npublic class Account {\n"
                        + "    private long balance;\n    /** @return balance */\n    public long getBalance() { return balance; }\n"
                        + "    /** Business rule: no overdraft. */\n    public void debit(long cents) {\n"
                        + "        if (cents > balance) throw new IllegalArgumentException(\"Insufficient funds\");\n"
                        + "        balance -= cents;\n    }\n    public void credit(long cents) { balance += cents; }\n}\n```"
                        : "```java\npackage com.bank.transfer;\n\n/** Moves money. */\npublic class TransferService {\n"
                        + "    /** Transfers. */\n    public void transfer(Account from, Account to, long cents) {\n"
                        + "        from.debit(cents);\n        to.credit(cents);\n    }\n}\n```";
            }
            if (userPrompt.contains("Write the JUnit 5 test class")) {
                Matcher p = Pattern.compile("Package: ([\\w.]+)").matcher(userPrompt);
                Matcher names = Pattern.compile("\\b([A-Z][A-Za-z0-9]+Test)\\b").matcher(userPrompt);
                assertTrue(p.find());
                StringBuilder out = new StringBuilder();
                while (names.find()) {
                    String test = names.group(1);
                    out.append("FILE: ").append(test).append(".java\n```java\npackage ").append(p.group(1))
                            .append(".;\n\nimport org.junit.jupiter.api.Test;\n")
                            .append("import static org.junit.jupiter.api.Assertions.assertEquals;\n\nclass ")
                            .append(test).append(" {\n    @Test\n    void works() {\n        assertEquals(1, 1);\n    }\n}\n```\n");
                }
                if (out.isEmpty()) {
                    out.append("```java\npackage ").append(p.group(1))
                            .append(".;\n\nimport org.junit.jupiter.api.Test;\n")
                            .append("class TransferServiceTest {\n    @Test\n    void works() {}\n}\n```");
                }
                return out.toString();
            }
            if (userPrompt.contains("Write Selenium WebDriver")) {
                return "FILE: FundTransferPage.java\n```java\npackage com.bank.transfer.ui;\n\nimport org.openqa.selenium.By;\n"
                        + "import org.openqa.selenium.WebDriver;\n\npublic class FundTransferPage {\n"
                        + "    private final WebDriver driver;\n    public FundTransferPage(WebDriver driver) { this.driver = driver; }\n"
                        + "    public void submit() { driver.findElement(By.id(\"go\")).click(); }\n}\n```\n"
                        + "FILE: FundTransferSeleniumTest.java\n```java\npackage com.bank.transfer.ui;\n\n"
                        + "import org.junit.jupiter.api.Test;\n\nclass FundTransferSeleniumTest {\n    @Test\n    void opens() { }\n}\n```";
            }
            if (userPrompt.contains("Write Ruby Selenium")) {
                return "FILE: spec/pages/fund_transfer_page.rb\n```ruby\nclass FundTransferPage\n  def initialize(driver)\n"
                        + "    @driver = driver\n  end\nend\n```\nFILE: spec/fund_transfer_spec.rb\n```ruby\n"
                        + "require_relative 'spec_helper'\nRSpec.describe 'Fund transfer' do\n  it 'works' do\n  end\nend\n```";
            }
            return "Fixed:\n```java\n" + userPrompt + "\n```";
        }
    }
}
