package com.agentic.chatbot.stage;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.service.BotModelTrainingService;
import com.agentic.chatbot.stage.StageResponse.StageFile;
import com.agentic.chatbot.stage.template.AutomationTemplates;
import com.agentic.chatbot.stage.template.CodeTemplates;
import com.agentic.chatbot.stage.template.DeployTemplates;
import com.agentic.chatbot.stage.template.ManualTestTemplates;
import com.agentic.chatbot.stage.template.PolyglotTemplates;
import com.agentic.chatbot.stage.template.PythonTemplates;
import com.agentic.chatbot.stage.template.SeleniumTemplates;
import com.agentic.chatbot.stage.template.StageSample;
import com.agentic.chatbot.stage.template.StoryTemplates;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Runs the five stage bots: Business Analyst, Developer, Tester (unit + UI automation), Deploy and Trainer. Each
 * stage works on its own from any input; stages that share a run id write into the same folder,
 * generated-app/stages/{runId}/, so a chained run builds one project: stories.md, the code in the chosen
 * language, its tests, automation tests, deployment files and a training report.
 */
@Service
public class StageService {

    private static final Logger log = LoggerFactory.getLogger(StageService.class);
    private static final Pattern RUN_ID = Pattern.compile("\\d{8}-\\d{6}-[0-9a-f]{6}");
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final Pattern PLAN_LINE = Pattern.compile(
            "^\\W*(?:\\d+[.)]\\s*)?[*`]*([A-Z][A-Za-z0-9]+)(?:\\.java)?[*`]*\\s*[-:\u2013\u2014]\\s*(.+)$");
    private static final Pattern BASE_URL = Pattern.compile("^https?://[\\w.\\-:]+(?:/[\\w.\\-/]*)?$");
    private static final Pattern CODE_LIKE = Pattern.compile(
            "(?m)^\\s*(?:package\\s+[\\w.]+;|import\\s|from\\s+\\w[\\w.]*\\s+import|(?:public|private|protected)\\s|class\\s+\\w|"
                    + "def\\s+\\w+\\s*\\(|function\\s|func\\s|fn\\s+\\w|#include|using\\s+System|namespace\\s|const\\s+\\w+\\s*=|"
                    + "CREATE\\s+(?:OR\\s+REPLACE\\s+)?(?:PACKAGE|PROCEDURE|FUNCTION|TABLE)|IDENTIFICATION\\s+DIVISION|<\\?php|module\\s+\\w)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVA_MAIN = Pattern.compile("public\\s+static\\s+void\\s+main\\s*\\(");
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    static final int MAX_INPUT_CHARS = 60_000;
    private static final int MAX_DEVELOPER_CLASSES = 4;
    private static final int MAX_TESTED_CLASSES = 3;
    private static final int MAX_REPAIRS = 2;
    /** Per model call in the Tester stage; a small local model on low RAM can otherwise run for many minutes. */
    static final Duration TESTER_CALL_TIMEOUT = Duration.ofSeconds(90);
    /** Wall-clock limit for all Tester model calls together, so a slow model cannot keep the stage running forever. */
    static final Duration TESTER_BUDGET = Duration.ofSeconds(240);
    private static final Duration TESTER_MIN_CALL = Duration.ofSeconds(15);
    private static final int TESTER_MAX_TOKENS = 1200;
    /** Longer Tester code is sent as signatures plus stories instead of the full source. */
    private static final int TESTER_FULL_CODE_CHARS = 3000;
    private static final ExecutorService MODEL_CALLS = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "stage-model-call");
        t.setDaemon(true);
        return t;
    });
    private static final Set<String> JAVA_LANG_TYPES = Set.of("Exception", "Error", "Object", "String", "Integer", "Long",
            "Double", "Boolean", "Number", "Record", "Enum", "Class", "System", "Thread", "Math", "Override", "Void");
    private static final int FEW_SHOT_EXAMPLES = 2;
    private static final double RECALL_SIMILARITY = 0.8;
    private static final List<StageType> WORK_STAGES = List.of(StageType.BA, StageType.DEVELOPER, StageType.TESTER,
            StageType.DEPLOY);
    private static final String OFFLINE_LANGUAGES = "Java, Python, " + String.join(", ",
            PolyglotTemplates.LANGUAGES.stream().map(StageLanguage::label).toList());

    private final StageLlm llm;
    private final BotModelTrainingService training;
    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** What a stage produced before it is written to disk. {@code extras} are scaffold files not shown as output. */
    private record Generated(Map<String, String> files, Map<String, String> extras, List<String> notes,
                             String mode, String model) {
    }

    /** Domain, language and automation choices of one request. */
    record Ctx(StageDomain domain, LanguageProfile lang, StageAutomation automation, StageRequest request) {

        /** The built-in language; OTHER for catalog languages without offline templates and for free text. */
        StageLanguage language() {
            return lang.kind();
        }

        String languageName() {
            return lang.label();
        }

        boolean java() {
            return language() == StageLanguage.JAVA;
        }

        /** Training examples are kept per domain, and per language for the code stages (Java keeps the plain key). */
        String key(StageType stage) {
            return stage == StageType.BA || java() ? domain.id() : domain.id() + ":" + lang.id();
        }
    }

    public StageService(StageLlm llm, BotModelTrainingService training,
                        @Value("${agentic.output-dir:generated-app}") String outputDir) {
        this.llm = llm;
        this.training = training;
        this.root = Path.of(outputDir).toAbsolutePath().normalize().resolve("stages");
    }

    public Path root() {
        return root;
    }

    public Optional<String> modelName() {
        return llm.active().map(LlmClient::describe);
    }

    static Ctx context(StageRequest request) {
        StageDomain domain = StageDomain.from(request.domain());
        LanguageProfile lang = LanguageProfile.resolve(request.option("language", "java"),
                request.option("languageName", ""));
        return new Ctx(domain, lang, StageAutomation.choose(request.option("automation", ""), lang.kind()), request);
    }

    // ---------------------------------------------------------------- running stages

    public StageResponse run(StageType stage, StageRequest request) {
        long start = System.currentTimeMillis();
        if (stage == StageType.TRAINER) {
            return trainer(request, start);
        }
        String input = request.input() == null ? "" : request.input().strip();
        if (input.isBlank()) {
            throw new IllegalArgumentException(switch (stage) {
                case BA -> "Enter a requirement, e.g. \"fund transfer between accounts\".";
                case DEVELOPER -> "Paste user stories, or run the Business Analyst stage first.";
                case TESTER -> "Paste code (and/or user stories for the automation tests), or run the Developer stage first.";
                case DEPLOY -> "Paste the code to deploy, or run the Developer stage first.";
                case TRAINER -> "";
            });
        }
        if (input.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("Input is too long (" + input.length() + " characters; limit "
                    + MAX_INPUT_CHARS + "). Split it and run the stage per part.");
        }
        Ctx ctx = context(request);
        String runId = resolveRunId(request.runId());
        Path dir = runDir(runId);
        Optional<LlmClient> model = request.flag("offline") ? Optional.empty() : llm.active();

        Generated g = generate(stage, ctx, input, model, dir);
        if (stage == StageType.TESTER || stage == StageType.DEPLOY) {
            addMissingSources(dir, ctx, testerInput(input, dir).code(), g.extras());
        }
        Map<String, String> all = new LinkedHashMap<>(g.files());
        g.extras().forEach(all::putIfAbsent);
        writeFiles(dir, all);
        if (stage == StageType.DEPLOY && request.flag("buildLocally")) {
            g.notes().add(dockerBuild(dir, appName(ctx, input, dir)));
        }
        recordRun(runId, stage, ctx, input, g, all.keySet(), System.currentTimeMillis() - start);

        List<StageFile> files = all.entrySet().stream().map(e -> new StageFile(e.getKey(), e.getValue())).toList();
        String output = output(stage, g.files());
        String nextStage = stage.next().map(StageType::id).orElse("");
        String nextInput = switch (stage) {
            case BA, DEVELOPER -> output;
            case TESTER -> Optional.of(developerCode(runId)).filter(s -> !s.isBlank())
                    .orElse(testerInput(input, dir).code());
            case DEPLOY -> runId;
            case TRAINER -> "";
        };
        long elapsed = System.currentTimeMillis() - start;
        log.info("Stage {} ({}, {}) run {} finished in {} ms via {}", stage.id(), ctx.domain().id(), ctx.lang().id(),
                runId, elapsed, g.mode());
        return new StageResponse(true, stage.id(), stage.title(), ctx.domain().id(), ctx.lang().id(), runId, g.mode(),
                g.model(), output, files, g.notes(), nextStage, nextInput, elapsed);
    }

    /** Runs all five stages, each fed from the previous one, into a single run folder. The Trainer only reports. */
    public List<StageResponse> runAll(StageRequest request) {
        List<StageResponse> results = new ArrayList<>();
        StageResponse ba = run(StageType.BA, request);
        results.add(ba);
        String runId = ba.runId();
        StageResponse dev = run(StageType.DEVELOPER, request.withInput(ba.nextInput(), runId));
        results.add(dev);
        StageResponse tester = run(StageType.TESTER, request.withInput(dev.nextInput(), runId).withOption("testerPart", null));
        results.add(tester);
        results.add(run(StageType.DEPLOY, request.withInput(tester.nextInput(), runId)));
        results.add(run(StageType.TRAINER, request.withInput("", runId).withOption("save", null)));
        return results;
    }

    private Generated generate(StageType stage, Ctx ctx, String input, Optional<LlmClient> model, Path dir) {
        if (stage == StageType.DEPLOY) {
            return deploy(ctx, input, model, dir);
        }
        if (stage == StageType.BA && !ctx.request().option("baMode", "").isBlank()) {
            return importedStories(ctx, input, model, dir);
        }
        List<String> notes = new ArrayList<>();
        if (model.isPresent()) {
            try {
                Generated g = viaModel(stage, ctx, input, model.get(), dir);
                if (g != null) {
                    return g;
                }
                notes.add("The AI model's answer could not be used, so the offline template was used instead.");
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (RuntimeException e) {
                log.warn("Stage {} model call failed: {}", stage.id(), e.getMessage());
                notes.add("The AI model failed (" + e.getMessage() + "), so the offline template was used instead.");
            }
        } else {
            Optional<Generated> trained = recall(stage, ctx, input);
            if (trained.isPresent()) {
                return trained.get();
            }
        }
        Generated t = template(stage, ctx, input, dir);
        t.notes().addAll(0, notes);
        return t;
    }

    /**
     * Business Analyst on uploaded or GitHub stories (option baMode): "passthrough" keeps them exactly as given so they go
     * straight to the Developer; "refine" lets the AI model organise them without dropping any (and falls back to
     * passthrough without a model).
     */
    private Generated importedStories(Ctx ctx, String input, Optional<LlmClient> model, Path dir) {
        List<String> notes = new ArrayList<>();
        boolean refine = ctx.request().option("baMode", "").equalsIgnoreCase("refine");
        if (refine && model.isPresent()) {
            try {
                Generated g = viaModel(StageType.BA, ctx, input, model.get(), dir);
                if (g != null) {
                    g.notes().add("Refined the uploaded/GitHub stories with the AI model; edit them below before sending them to the Developer.");
                    return g;
                }
                notes.add("The AI model's answer could not be used, so your stories were kept as they are.");
            } catch (RuntimeException e) {
                log.warn("Business Analyst refine failed: {}", e.getMessage());
                notes.add("The AI model failed (" + e.getMessage() + "), so your stories were kept as they are.");
            }
        } else if (refine) {
            notes.add("No AI model is connected, so your stories were kept as they are (use as-is).");
        }
        Map<String, String> files = new LinkedHashMap<>();
        files.put("stories.md", input.strip() + "\n");
        notes.add(0, "Used your uploaded/GitHub stories as-is (" + countStories(input) + " story heading(s) found); the Developer receives them unchanged.");
        if (input.length() > 2500) {
            notes.add("The Developer reads the first part of long story sets; for 100+ stories run it per feature or sub-folder.");
        }
        return new Generated(files, Map.of(), notes, "passthrough", "your stories");
    }

    private static int countStories(String text) {
        Matcher m = Pattern.compile("(?im)^\\s*(?:#{1,4}\\s*story\\b|as an?\\s)").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static String needModel(Ctx ctx, String what) {
        return "No AI model is available, and offline mode has built-in templates only for " + OFFLINE_LANGUAGES
                + ". Start Ollama (ollama serve, model qwen2.5-coder) or set OPENAI_API_KEY, then run again to generate "
                + ctx.languageName() + " " + what + ".";
    }

    // ---------------------------------------------------------------- offline templates

    private Generated template(StageType stage, Ctx ctx, String input, Path dir) {
        StageDomain domain = ctx.domain();
        List<String> notes = new ArrayList<>();
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, String> extras = new LinkedHashMap<>();
        StageSample sample = StageSample.detect(input, domain);
        String which = switch (sample) {
            case FUND_TRANSFER -> "banking fund-transfer template";
            case SHIPMENT_TRACKING -> "logistics shipment-tracking template";
            case GENERIC -> "generic " + domain.label() + " request-workflow template (" + sample.featureName(input) + ")";
        };
        switch (stage) {
            case BA -> files.put("stories.md", StoryTemplates.stories(domain, input));
            case DEVELOPER -> {
                if (ctx.java()) {
                    files.putAll(CodeTemplates.developer(domain, input, ctx.request().option("package", "")));
                    extras.put("pom.xml", pomFor(files));
                } else if (ctx.language() == StageLanguage.PYTHON) {
                    files.putAll(PythonTemplates.code(domain, input));
                    extras.putAll(PythonTemplates.scaffold());
                    which = "Python " + domain.label() + " request-workflow template (" + sample.featureName(input) + ")";
                } else if (PolyglotTemplates.supports(ctx.language())) {
                    files.putAll(PolyglotTemplates.code(ctx.language(), domain, input));
                    extras.putAll(PolyglotTemplates.scaffold(ctx.language(), appName(ctx, input, dir), false));
                    which = ctx.languageName() + " " + (PolyglotTemplates.fundTransfer(domain, input)
                            ? "banking fund-transfer template"
                            : domain.label() + " request-workflow template (" + sample.featureName(input) + ")");
                } else {
                    throw new IllegalArgumentException(needModel(ctx, "code"));
                }
                notes.add(checkNote(ctx.lang(), files));
            }
            case TESTER -> which = testerTemplate(ctx, testerInput(input, dir), files, extras, notes);
            case DEPLOY, TRAINER -> throw new IllegalStateException("not a template stage: " + stage);
        }
        notes.add(0, "Offline mode: used the built-in " + which + ".");
        return new Generated(files, extras, notes, "template", "offline templates");
    }

    /** Code and stories found in a Tester input; stories fall back to the run's stories.md. */
    record TesterInput(String code, String stories) {
    }

    TesterInput testerInput(String input, Path dir) {
        StringBuilder code = new StringBuilder();
        StringBuilder stories = new StringBuilder();
        List<Map.Entry<String, String>> parts = StageFiles.split(input);
        boolean marked = parts.stream().anyMatch(p -> !p.getKey().isBlank());
        for (Map.Entry<String, String> part : parts) {
            String path = part.getKey();
            String content = part.getValue();
            if (path.startsWith("manual-tests/")) {
                continue; // earlier manual test cases are tester output, not stories
            }
            if (path.endsWith(".md") || (path.isBlank() && isStories(content))) {
                stories.append(content).append('\n');
            } else if (marked && !path.isBlank()) {
                code.append("=== FILE: ").append(path).append(" ===\n").append(content);
            } else {
                code.append(content);
            }
        }
        if (!marked && parts.size() <= 1 && stories.isEmpty()) {
            code.setLength(0);
            code.append(input.strip()).append('\n');
        }
        String s = stories.toString().strip();
        if (s.isEmpty() && dir != null) {
            s = readQuietly(dir.resolve("stories.md")).strip();
        }
        return new TesterInput(code.toString().strip(), s);
    }

    private static boolean isStories(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        boolean storyWords = t.contains("scenario:") || (t.contains("as a") && t.contains("i want"))
                || t.contains("feature:");
        return storyWords && !CODE_LIKE.matcher(text).find();
    }

    private static String testerPart(Ctx ctx) {
        String part = ctx.request().option("testerPart", "all").toLowerCase(Locale.ROOT);
        return part.equals("unit") || part.equals("automation") || part.equals("manual") || part.equals("both") ? part : "all";
    }

    /** Unit tests are written for unit, both (legacy: unit + automation) and all. */
    private static boolean wantsUnit(String part) {
        return part.equals("unit") || part.equals("both") || part.equals("all");
    }

    private static boolean wantsAutomation(String part) {
        return part.equals("automation") || part.equals("both") || part.equals("all");
    }

    /** Manual (functional) test cases a person runs by hand: only for manual and all. */
    private static boolean wantsManual(String part) {
        return part.equals("manual") || part.equals("all");
    }

    private String testerTemplate(Ctx ctx, TesterInput in, Map<String, String> files, Map<String, String> extras,
                                  List<String> notes) {
        String part = testerPart(ctx);
        List<String> which = new ArrayList<>();
        if (wantsUnit(part)) {
            if (in.code().isBlank()) {
                requireAutomationOnly(part, notes);
            } else if (ctx.java()) {
                Map<String, String> sources = StageFiles.javaSources(in.code(), "src/main/java", null);
                if (sources.isEmpty()) {
                    if (part.equals("unit")) {
                        throw new IllegalArgumentException("No Java class found in the input. Paste Java code or run the Developer stage first.");
                    }
                    notes.add("No Java class found in the input, so no unit tests were written.");
                } else {
                    Map<String, String> tests = CodeTemplates.unitTests(ctx.domain(), sources);
                    files.putAll(tests);
                    which.add(describeTests(sources, tests));
                }
            } else if (ctx.language() == StageLanguage.PYTHON) {
                Map<String, String> sources = sourceFiles(ctx, in.code());
                Map<String, String> tests = PythonTemplates.tests(ctx.domain(), sources);
                files.putAll(tests);
                extras.putAll(PythonTemplates.scaffold());
                boolean full = tests.values().stream().noneMatch(t -> t.contains("generated offline by BOT"));
                which.add(full ? "pytest suite matching the generated code" : "pytest skeleton generator");
                Map<String, String> all = new LinkedHashMap<>(sources);
                all.putAll(tests);
                notes.add(checkNote(ctx.lang(), all));
            } else if (PolyglotTemplates.supports(ctx.language())) {
                Map<String, String> sources = sourceFiles(ctx, in.code());
                Map<String, String> tests = PolyglotTemplates.tests(ctx.language(), ctx.domain(), sources);
                if (tests.isEmpty()) {
                    if (part.equals("unit")) {
                        throw new IllegalArgumentException("No " + ctx.languageName() + " code found in the input. Paste "
                                + ctx.languageName() + " code or run the Developer stage first.");
                    }
                    notes.add("No " + ctx.languageName() + " code found in the input, so no unit tests were written.");
                } else {
                    files.putAll(tests);
                    extras.putAll(PolyglotTemplates.scaffold(ctx.language(), appName(ctx, in.code(), null), true));
                    boolean full = tests.values().stream().noneMatch(t -> t.contains("generated offline by BOT"));
                    which.add(ctx.lang().unitFramework() + (full ? " suite matching the generated code" : " skeleton generator"));
                    notes.add(checkNote(ctx.lang(), tests));
                }
            } else if (part.equals("unit")) {
                throw new IllegalArgumentException(needModel(ctx, "unit tests (" + ctx.lang().unitFramework() + ")"));
            } else {
                notes.add(needModel(ctx, "unit tests (" + ctx.lang().unitFramework() + ")"));
            }
        }
        if (wantsAutomation(part) && ctx.automation() != StageAutomation.NONE) {
            String stories = in.stories().isBlank() ? in.code() : in.stories();
            if (in.stories().isBlank()) {
                notes.add("No user stories found (paste them or run the Business Analyst stage), so the automation tests "
                        + "cover a page-load scenario only.");
            }
            which.add(automationTemplates(ctx, stories, files, extras, notes) + " automation template");
        }
        if (wantsManual(part) && manualTemplate(ctx, in, files, notes)) {
            which.add("manual (functional) test-case template");
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Nothing to test: paste code for unit tests or user stories for automation and manual tests.");
        }
        return String.join(" and ", which);
    }

    private static void requireAutomationOnly(String part, List<String> notes) {
        if (part.equals("unit")) {
            throw new IllegalArgumentException("Paste code to unit-test, or run the Developer stage first.");
        }
        notes.add("No code in the input, so no unit tests were written.");
    }

    /** Manual (functional) test cases from the stories (or the code when there are none); false when there is nothing to write from. */
    private boolean manualTemplate(Ctx ctx, TesterInput in, Map<String, String> files, List<String> notes) {
        if (in.stories().isBlank() && in.code().isBlank()) {
            if (testerPart(ctx).equals("manual")) {
                throw new IllegalArgumentException("Paste user stories (Given/When/Then) or code to write manual test cases from, "
                        + "or run the Business Analyst stage first.");
            }
            return false;
        }
        if (!ManualTestTemplates.hasCases(in.stories(), in.code())) {
            if (testerPart(ctx).equals("manual")) {
                throw new IllegalArgumentException("No Given/When/Then scenarios or methods found to write manual test cases from. "
                        + "Paste user stories with scenarios, or run the Business Analyst stage first.");
            }
            notes.add("No scenarios or methods found, so no manual test cases were written.");
            return false;
        }
        if (in.stories().isBlank()) {
            notes.add("No user stories found, so the manual test cases were derived from the code. Run the Business Analyst "
                    + "stage (or paste stories) for cases that follow the acceptance criteria.");
        }
        files.putAll(ManualTestTemplates.files(ctx.domain(), in.stories(), in.code(), baseUrl(ctx.request())));
        return true;
    }

    private static String describeTests(Map<String, String> sources, Map<String, String> tests) {
        boolean skeleton = tests.values().stream().anyMatch(t -> t.contains("generated offline by BOT"));
        return skeleton
                ? "unit-test skeleton generator (" + tests.size() + " test class(es) for " + sources.size()
                + " source file(s); connect an AI model for behavioural tests)"
                : "unit-test suite matching the generated code";
    }

    /** Offline automation tests; returns a label for the notes. */
    private String automationTemplates(Ctx ctx, String stories, Map<String, String> files, Map<String, String> extras,
                                       List<String> notes) {
        StageDomain domain = ctx.domain();
        String baseUrl = baseUrl(ctx.request());
        StageAutomation a = ctx.automation();
        if (AutomationTemplates.supports(a)) {
            files.putAll(AutomationTemplates.tests(a, domain, stories, baseUrl));
            extras.putAll(AutomationTemplates.scaffold(a, baseUrl));
            return a.label();
        }
        if (a != StageAutomation.BOTH && a != StageAutomation.SELENIUM_JAVA && a != StageAutomation.SELENIUM_RUBY) {
            notes.add("No offline template for " + a.label() + "; wrote Selenium Java instead. Connect an AI model for "
                    + a.label() + ".");
            a = StageAutomation.SELENIUM_JAVA;
        }
        if (a != StageAutomation.SELENIUM_RUBY) {
            files.putAll(SeleniumTemplates.java(domain, stories, baseUrl));
            extras.putAll(SeleniumTemplates.javaScaffold(CodeTemplates.packageFor(domain, stories)));
        }
        if (a != StageAutomation.SELENIUM_JAVA) {
            files.putAll(SeleniumTemplates.ruby(domain, stories, baseUrl));
            extras.putAll(SeleniumTemplates.rubyScaffold(baseUrl));
        }
        return a.label();
    }

    // ---------------------------------------------------------------- AI model

    private String system(StageType stage, Ctx ctx) {
        return StagePrompts.system(stage, ctx.domain(), ctx.lang(), ctx.automation())
                + StagePrompts.fewShot(training.stageExamples(stage.id(), ctx.key(stage), FEW_SHOT_EXAMPLES));
    }

    private Generated viaModel(StageType stage, Ctx ctx, String input, LlmClient model, Path dir) {
        String system = system(stage, ctx);
        int examples = training.stageExamples(stage.id(), ctx.key(stage), FEW_SHOT_EXAMPLES).size();
        List<String> notes = new ArrayList<>();
        notes.add("Generated by " + model.describe() + " as the " + ctx.domain().label() + " " + stage.title()
                + (stage == StageType.BA ? "" : " (" + ctx.languageName() + ")")
                + (examples == 0 ? "." : ", guided by " + examples + " approved training example(s)."));
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, String> extras = new LinkedHashMap<>();
        switch (stage) {
            case BA -> {
                String user = ctx.request().option("baMode", "").equalsIgnoreCase("refine")
                        ? StagePrompts.baRefine(input) : StagePrompts.baUser(input);
                String answer = StageFiles.collapseRepeats(StageFiles.stripFences(model.complete(system, user))).strip();
                if (!looksLikeStories(answer)) {
                    return null;
                }
                if (!answer.toLowerCase(Locale.ROOT).contains("feature:")) {
                    answer = "# Feature: " + StageSample.featureTitle(input) + "\nDomain: " + ctx.domain().label() + "\n\n" + answer;
                }
                files.put("stories.md", answer + "\n");
            }
            case DEVELOPER -> {
                if (ctx.java()) {
                    if (!developerViaModel(ctx, input, model, system, files, notes)) {
                        return null;
                    }
                    extras.put("pom.xml", pomFor(files));
                } else {
                    if (!developerAnyViaModel(ctx, input, model, system, files, notes)) {
                        return null;
                    }
                    if (ctx.language() == StageLanguage.PYTHON) {
                        extras.putAll(PythonTemplates.scaffold());
                        if (files.keySet().stream().anyMatch(p -> p.startsWith("app/")) && !files.containsKey("app/__init__.py")) {
                            extras.put("app/__init__.py", "");
                        }
                    }
                }
            }
            case TESTER -> testerViaModel(ctx, testerInput(input, dir), model, system, files, extras, notes);
            case DEPLOY, TRAINER -> throw new IllegalStateException("not a model stage: " + stage);
        }
        return new Generated(files, extras, notes, "llm", model.describe());
    }

    private boolean developerViaModel(Ctx ctx, String stories, LlmClient model, String system,
                                      Map<String, String> files, List<String> notes) {
        StageDomain domain = ctx.domain();
        String pkg = ctx.request().option("package", CodeTemplates.packageFor(domain, stories));
        String feature = StageSample.detect(stories, domain).featureName(stories);
        List<String> plan = parsePlan(model.complete(system, StagePrompts.developerPlan(stories, pkg))).stream()
                .map(t -> JAVA_LANG_TYPES.contains(t) ? feature + t : t)
                .distinct()
                .toList();
        if (plan.isEmpty()) {
            plan = List.of(feature, feature + "Service");
            notes.add("The model did not list classes; generating " + String.join(" and ", plan) + ".");
        } else {
            notes.add("Planned classes: " + String.join(", ", plan) + ".");
        }
        StringBuilder signatures = new StringBuilder();
        for (String type : plan) {
            String answer = model.complete(system, StagePrompts.developerClass(stories, pkg, plan, type, signatures.toString()));
            Map<String, String> parsed = modelJava(answer, "src/main/java", pkg);
            if (parsed.isEmpty()) {
                notes.add("No Java code came back for " + type + "; skipped.");
                continue;
            }
            parsed.forEach((path, code) -> {
                files.put(path, code);
                signatures.append(StageFiles.signatures(code));
            });
        }
        if (files.isEmpty()) {
            return false;
        }
        JavaCompileCheck.Result check = JavaCompileCheck.compile(files, List.of());
        int repairs = 0;
        while (!check.ok() && !check.skipped() && repairs < MAX_REPAIRS) {
            String failing = check.errors().get(0);
            String path = failing.substring(0, Math.max(0, failing.indexOf(".java:") + 5));
            if (!files.containsKey(path)) {
                break;
            }
            List<String> errors = check.errors().stream().filter(e -> e.startsWith(path)).toList();
            String type = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            Map<String, String> fixed = StageFiles.javaSources(
                    model.complete(system, StagePrompts.repair(type, files.get(path), errors)), "src/main/java", pkg);
            repairs++;
            if (fixed.containsKey(path)) {
                files.put(path, fixed.get(path));
            } else {
                break;
            }
            check = JavaCompileCheck.compile(files, List.of());
        }
        if (repairs > 0) {
            notes.add("Asked the model to fix compile errors " + repairs + " time(s).");
        }
        if (!check.ok() && !check.skipped() && !ctx.request().flag("keepModelCode")) {
            throw new IllegalStateException("its Java still had " + check.errors().size()
                    + " compile error(s) after " + repairs + " repair attempt(s), first: " + check.errors().get(0)
                    + ". Pass option keepModelCode=true to keep it anyway");
        }
        long undocumented = files.values().stream().filter(c -> !c.contains("/**")).count();
        if (undocumented > 0) {
            notes.add(undocumented + " of " + files.size() + " file(s) from the model have no Javadoc; add comments or approve a "
                    + "commented version so future runs follow it.");
        }
        notes.add(check.summary(files.size()));
        return true;
    }

    private boolean developerAnyViaModel(Ctx ctx, String stories, LlmClient model, String system,
                                         Map<String, String> files, List<String> notes) {
        LanguageProfile lang = ctx.lang();
        String feature = StageSample.detect(stories, ctx.domain()).featureName(stories);
        String answer = model.complete(system, StagePrompts.developerAny(stories, ctx.languageName(), lang.fence(),
                lang.srcDir(), feature));
        files.putAll(codeFiles(answer, lang.srcDir(), lang.ext(), StagePrompts.snake(feature)));
        if (files.isEmpty()) {
            return false;
        }
        boolean commented = files.values().stream().anyMatch(c -> c.contains("Business rule") || c.contains("\"\"\"")
                || c.contains("/**") || c.contains("///"));
        if (!commented) {
            notes.add("The model's code has few comments; add them or approve a commented version so future runs follow it.");
        }
        notes.add(checkNote(lang, files));
        return true;
    }

    private void testerViaModel(Ctx ctx, TesterInput in, LlmClient model, String system, Map<String, String> files,
                                Map<String, String> extras, List<String> notes) {
        String part = testerPart(ctx);
        TesterBudget budget = new TesterBudget(model, system, notes);
        if (wantsUnit(part)) {
            if (in.code().isBlank()) {
                requireAutomationOnly(part, notes);
            } else {
                boolean ok = ctx.java()
                        ? unitTestsViaModel(in.code(), in.stories(), budget, files, notes)
                        : unitAnyViaModel(ctx, in.code(), budget, files, extras, notes);
                if (!ok) {
                    notes.add("No usable unit tests from the model; used the offline unit-test template.");
                    Map<String, String> fallback = new LinkedHashMap<>();
                    testerTemplate(withPart(ctx, "unit"), in, fallback, extras, notes);
                    files.putAll(fallback);
                }
            }
        }
        if (wantsAutomation(part) && ctx.automation() != StageAutomation.NONE) {
            String stories = in.stories().isBlank() ? in.code() : in.stories();
            functionalViaModel(ctx, stories, budget, files, extras, notes);
        }
        if (wantsManual(part)) {
            manualViaModel(ctx, in, budget, files, notes);
        }
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Nothing to test: paste code for unit tests or user stories for automation and manual tests.");
        }
    }

    private void manualViaModel(Ctx ctx, TesterInput in, TesterBudget budget, Map<String, String> files, List<String> notes) {
        if (in.stories().isBlank() && in.code().isBlank()) {
            manualTemplate(ctx, in, files, notes);
            return;
        }
        String answer = budget.call(StagePrompts.manualTests(trimmedCode(in.stories()), trimmedCode(in.code()),
                baseUrl(ctx.request())), "the manual test cases").map(a -> StageFiles.stripFences(a).strip()).orElse("");
        String lower = answer.toLowerCase(Locale.ROOT);
        if (answer.length() > 120 && lower.contains("step") && lower.contains("expected")) {
            files.put(ManualTestTemplates.PATH, "# Manual test cases (functional testing)\n\n"
                    + answer.replaceFirst("(?m)^# .*\\R+", "") + "\n");
        } else {
            notes.add("No usable manual test cases from the model; used the offline template.");
            manualTemplate(ctx, in, files, notes);
        }
    }

    private static Ctx withPart(Ctx ctx, String part) {
        return new Ctx(ctx.domain(), ctx.lang(), ctx.automation(), ctx.request().withOption("testerPart", part));
    }

    /**
     * Runs the Tester's model calls one after another (a low-RAM Ollama serves one request at a time), each bounded by
     * {@link #TESTER_CALL_TIMEOUT} and all by {@link #TESTER_BUDGET}. After a timeout or failure the remaining calls
     * are skipped so their parts use the offline templates.
     */
    private static final class TesterBudget {
        private final LlmClient model;
        private final String system;
        private final List<String> notes;
        private final Instant end = Instant.now().plus(TESTER_BUDGET);
        private boolean stopped;

        TesterBudget(LlmClient model, String system, List<String> notes) {
            this.model = model;
            this.system = system;
            this.notes = notes;
        }

        boolean stopped() {
            return stopped;
        }

        /** The model's answer, or empty (with a note) when the call was skipped, timed out or failed. */
        Optional<String> call(String prompt, String what) {
            if (stopped) {
                notes.add("Skipped the model for " + what + " after an earlier timeout or failure; used the offline template.");
                return Optional.empty();
            }
            Duration left = Duration.between(Instant.now(), end);
            if (left.compareTo(TESTER_MIN_CALL) < 0) {
                stopped = true;
                notes.add("Tester time limit of " + TESTER_BUDGET.toSeconds() + "s reached; " + what
                        + " uses the offline template.");
                return Optional.empty();
            }
            Duration limit = left.compareTo(TESTER_CALL_TIMEOUT) < 0 ? left : TESTER_CALL_TIMEOUT;
            Future<String> answer = MODEL_CALLS.submit(() -> model.complete(system, prompt, limit, TESTER_MAX_TOKENS));
            try {
                return Optional.of(answer.get(limit.toMillis() + 2000, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                notes.add("The model did not answer " + what + " within " + limit.toSeconds()
                        + "s; used the offline template.");
            } catch (ExecutionException e) {
                String why = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
                notes.add("The model failed on " + what + " (" + why + "); used the offline template.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                notes.add("The model call for " + what + " was interrupted; used the offline template.");
            }
            answer.cancel(true);
            stopped = true;
            return Optional.empty();
        }
    }

    /** Java code longer than {@link #TESTER_FULL_CODE_CHARS} reduced to its signatures for small-model prompts. */
    private static String trimmedCode(String code) {
        if (code.length() <= TESTER_FULL_CODE_CHARS || !StageFiles.looksLikeJava(code)) {
            return code;
        }
        String signatures = StageFiles.signatures(code);
        return signatures.isBlank() ? code : signatures;
    }

    private boolean unitTestsViaModel(String code, String stories, TesterBudget budget,
                                      Map<String, String> files, List<String> notes) {
        Map<String, String> sources = StageFiles.javaSources(code, "src/main/java", null);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("No Java class found in the input. Paste Java code or run the Developer stage first.");
        }
        List<Map.Entry<String, String>> targets = sources.entrySet().stream()
                .filter(e -> isTestable(e.getValue()))
                .sorted((a, b) -> Boolean.compare(!a.getKey().endsWith("Service.java"), !b.getKey().endsWith("Service.java")))
                .limit(MAX_TESTED_CLASSES)
                .toList();
        if (targets.isEmpty()) {
            targets = sources.entrySet().stream().limit(1).toList();
        }
        Map<String, String> tests = new LinkedHashMap<>();
        for (Map.Entry<String, String> target : targets) {
            String classCode = target.getValue();
            String pkg = StageFiles.packageOf(classCode).orElse("");
            String type = StageFiles.primaryType(classCode).orElse("Unit");
            StringBuilder others = new StringBuilder();
            sources.forEach((p, c) -> {
                if (!p.equals(target.getKey())) {
                    others.append(StageFiles.signatures(c));
                }
            });
            boolean trimmed = classCode.length() > TESTER_FULL_CODE_CHARS;
            String prompt = StagePrompts.unitTest(trimmed ? trimmedCode(classCode) : classCode, others.toString(),
                    trimmed ? stories : "", pkg, type + "Test");
            Optional<String> answer = budget.call(prompt, "the " + type + " unit tests");
            if (answer.isEmpty()) {
                if (budget.stopped()) {
                    break;
                }
                continue;
            }
            Map<String, String> parsed = modelJava(answer.get(), "src/test/java", pkg);
            parsed.entrySet().stream().filter(e -> e.getValue().contains("@Test")).findFirst()
                    .ifPresentOrElse(e -> tests.put(e.getKey(), e.getValue()),
                            () -> notes.add("No usable JUnit test came back for " + type + "."));
        }
        if (tests.isEmpty()) {
            return false;
        }
        files.putAll(tests);
        Optional<Path> junit = junitJar();
        if (junit.isPresent()) {
            Map<String, String> all = new LinkedHashMap<>(sources);
            all.putAll(tests);
            notes.add(JavaCompileCheck.compile(all, List.of(junit.get())).summary(all.size()));
        } else {
            notes.add("Compile check of the tests skipped: JUnit is not on BOT's runtime classpath. Run mvn test in the run folder.");
        }
        return true;
    }

    private boolean unitAnyViaModel(Ctx ctx, String code, TesterBudget budget, Map<String, String> files,
                                    Map<String, String> extras, List<String> notes) {
        LanguageProfile lang = ctx.lang();
        Optional<String> answer = budget.call(StagePrompts.unitTestAny(code, ctx.languageName(), lang.unitFramework(),
                lang.fence(), lang.testDir()), "the " + ctx.languageName() + " unit tests");
        if (answer.isEmpty()) {
            return false;
        }
        String base = "test_" + StagePrompts.snake(StageSample.detect(code, ctx.domain()).featureName(code));
        Map<String, String> tests = codeFiles(answer.get(), lang.testDir(), lang.ext(), base);
        if (tests.isEmpty()) {
            return false;
        }
        files.putAll(tests);
        if (lang.kind() == StageLanguage.PYTHON) {
            extras.putAll(PythonTemplates.scaffold());
            Map<String, String> all = new LinkedHashMap<>(sourceFiles(ctx, code));
            all.putAll(tests);
            notes.add(checkNote(lang, all));
        } else {
            notes.add(checkNote(lang, tests));
        }
        return true;
    }

    private void functionalViaModel(Ctx ctx, String stories, TesterBudget budget, Map<String, String> files,
                                    Map<String, String> extras, List<String> notes) {
        StageDomain domain = ctx.domain();
        String baseUrl = baseUrl(ctx.request());
        StageAutomation a = ctx.automation();
        String feature = SeleniumTemplates.feature(domain, stories);
        String promptStories = trimmedCode(stories);
        if (a != StageAutomation.BOTH && a != StageAutomation.SELENIUM_JAVA && a != StageAutomation.SELENIUM_RUBY) {
            Map<String, String> parsed = budget.call(StagePrompts.automationAny(promptStories, a, feature, baseUrl),
                    "the " + a.label() + " tests").map(answer -> automationFiles(answer, a, feature)).orElse(Map.of());
            if (!parsed.isEmpty()) {
                files.putAll(parsed);
                extras.putAll(AutomationTemplates.scaffold(a, baseUrl));
            } else {
                notes.add("No usable " + a.label() + " answer from the model; used the offline template.");
                automationTemplates(ctx, stories, files, extras, notes);
            }
            return;
        }
        String pkg = SeleniumTemplates.uiPackage(domain, stories);
        if (a != StageAutomation.SELENIUM_RUBY) {
            Map<String, String> java = budget.call(StagePrompts.seleniumJava(promptStories, pkg, feature, baseUrl),
                            "the Java Selenium tests")
                    .map(answer -> modelJava(answer, SeleniumTemplates.JAVA_ROOT + "src/test/java", pkg))
                    .orElse(Map.of());
            if (java.values().stream().anyMatch(c -> c.contains("WebDriver") || c.contains("By."))) {
                files.putAll(java);
            } else {
                notes.add("No usable Java Selenium answer from the model; used the offline Java template.");
                files.putAll(SeleniumTemplates.java(domain, stories, baseUrl));
            }
            extras.putAll(SeleniumTemplates.javaScaffold(CodeTemplates.packageFor(domain, stories)));
        }
        if (a != StageAutomation.SELENIUM_JAVA) {
            Map<String, String> ruby = budget.call(StagePrompts.seleniumRuby(promptStories, feature, baseUrl),
                            "the Ruby Selenium tests")
                    .map(answer -> rubyFiles(answer, feature))
                    .orElse(Map.of());
            if (ruby.values().stream().anyMatch(c -> c.contains("describe"))) {
                files.putAll(ruby);
            } else {
                notes.add("No usable Ruby Selenium answer from the model; used the offline Ruby template.");
                files.putAll(SeleniumTemplates.ruby(domain, stories, baseUrl));
            }
            extras.putAll(SeleniumTemplates.rubyScaffold(baseUrl));
        }
    }

    private static Map<String, String> automationFiles(String answer, StageAutomation a, String feature) {
        Map<String, String> out = new LinkedHashMap<>();
        String ext = switch (a.fence()) {
            case "python" -> "py";
            case "typescript" -> "ts";
            case "csharp" -> "cs";
            default -> "js";
        };
        int n = 0;
        for (Map.Entry<String, String> part : StageFiles.split(answer)) {
            String path = StageFiles.safeRelative(part.getKey());
            if (path.startsWith(a.root())) {
                path = path.substring(a.root().length());
            }
            if (path.isBlank() || !path.contains(".")) {
                path = "tests/" + StagePrompts.snake(feature) + (n == 0 ? "" : "_" + n) + "." + ext;
            }
            n++;
            out.put(a.root() + path, part.getValue());
        }
        return out;
    }

    private static Map<String, String> modelJava(String answer, String root, String pkg) {
        Map<String, String> out = new LinkedHashMap<>();
        StageFiles.javaSources(answer, root, pkg).forEach((path, code) -> out.put(path, StageFiles.addMissingJdkImports(code)));
        return out;
    }

    private static Map<String, String> rubyFiles(String answer, String feature) {
        Map<String, String> out = new LinkedHashMap<>();
        String snake = StagePrompts.snake(feature);
        for (Map.Entry<String, String> part : StageFiles.split(answer)) {
            String content = part.getValue();
            String path = StageFiles.safeRelative(part.getKey());
            if (StageFiles.looksLikeJava(content) && !content.contains(" do") && !content.contains("def ")) {
                continue;
            }
            if (path.isBlank() || !path.endsWith(".rb")) {
                path = content.contains("describe") ? "spec/" + snake + "_spec.rb" : "spec/pages/" + snake + "_page.rb";
            }
            if (path.startsWith(SeleniumTemplates.RUBY_ROOT)) {
                path = path.substring(SeleniumTemplates.RUBY_ROOT.length());
            }
            if (!path.startsWith("spec/")) {
                path = "spec/" + path;
            }
            out.put(SeleniumTemplates.RUBY_ROOT + path, content);
        }
        return out;
    }

    /**
     * Files of a non-Java answer or paste: FILE paths are kept (made safe); unnamed parts get
     * {@code dir/base.ext}, and bare file names are put under {@code dir}.
     */
    static Map<String, String> codeFiles(String text, String dir, String ext, String base) {
        Map<String, String> out = new LinkedHashMap<>();
        int n = 0;
        for (Map.Entry<String, String> part : StageFiles.split(text)) {
            String content = part.getValue();
            if (content.isBlank()) {
                continue;
            }
            String path = StageFiles.safeRelative(part.getKey());
            if (path.isBlank() || !path.contains(".")) {
                path = dir + "/" + base + (n == 0 ? "" : "_" + n) + "." + ext;
            } else if (!path.contains("/") && !dir.equals(".")) {
                path = dir + "/" + path;
            }
            n++;
            out.put(path, content);
        }
        return out;
    }

    /** The code under test or to deploy, as files keyed by path, using the language's conventions. */
    Map<String, String> sourceFiles(Ctx ctx, String code) {
        if (code == null || code.isBlank()) {
            return Map.of();
        }
        if (ctx.java()) {
            return StageFiles.javaSources(code, "src/main/java", null);
        }
        String base = StagePrompts.snake(StageSample.detect(code, ctx.domain()).featureName(code));
        Map<String, String> files = codeFiles(code, ctx.lang().srcDir(), ctx.lang().ext(), base);
        files.keySet().removeIf(p -> p.startsWith(ctx.lang().testDir() + "/test_") || StageAutomation.isAutomationPath(p));
        return files;
    }

    static List<String> parsePlan(String answer) {
        Set<String> types = new LinkedHashSet<>();
        for (String line : (answer == null ? "" : answer).split("\\R")) {
            Matcher m = PLAN_LINE.matcher(line.strip());
            if (m.matches() && !m.group(1).equals("ClassName")) {
                types.add(m.group(1));
            }
            if (types.size() >= MAX_DEVELOPER_CLASSES) {
                break;
            }
        }
        return List.copyOf(types);
    }

    private static boolean isTestable(String code) {
        String type = StageFiles.primaryType(code).orElse("");
        boolean isClass = Pattern.compile("\\bclass\\s+" + type + "\\b").matcher(code).find();
        boolean exception = code.matches("(?s).*class\\s+" + type + "\\s+extends\\s+\\w*(Exception|Error)\\b.*");
        return isClass && !exception && code.contains("(");
    }

    private static boolean looksLikeStories(String text) {
        String t = text.toLowerCase(Locale.ROOT);
        return t.length() > 80 && (t.contains("as a") || t.contains("given")) && !t.contains("public class");
    }

    private static Optional<Path> junitJar() {
        try {
            Class<?> test = Class.forName("org.junit.jupiter.api.Test");
            return Optional.of(Path.of(test.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (Exception | LinkageError e) {
            return Optional.empty();
        }
    }

    /** Java: javac; Python: py_compile; other languages: say how to check. */
    private static String checkNote(LanguageProfile lang, Map<String, String> files) {
        if (lang.kind() == StageLanguage.JAVA) {
            return compileNote(files, List.of());
        }
        if (lang.kind() == StageLanguage.PYTHON) {
            Map<String, String> py = new LinkedHashMap<>();
            files.forEach((p, c) -> {
                if (p.endsWith(".py")) {
                    py.put(p, c);
                }
            });
            return PythonCompileCheck.summary(PythonCompileCheck.compile(py), py.size());
        }
        return "No compile check for " + lang.label() + " inside BOT; verify with " + lang.checkCommand()
                + " or the CI workflow from the Deploy stage.";
    }

    // ---------------------------------------------------------------- deploy

    private Generated deploy(Ctx ctx, String input, Optional<LlmClient> model, Path dir) {
        TesterInput in = testerInput(input, null);
        String code = in.code().isBlank() ? input : in.code();
        String app = appName(ctx, code, dir);
        Map<String, String> sources = sourceFiles(ctx, code);
        String entry = entryPoint(ctx.language(), sources, app);
        int port = port(ctx.request());
        boolean compose = !"false".equalsIgnoreCase(ctx.request().option("compose", "true"));
        boolean k8s = !"false".equalsIgnoreCase(ctx.request().option("kubernetes", "true"));
        Map<String, String> files = new LinkedHashMap<>(DeployTemplates.files(new DeployTemplates.Project(
                ctx.lang(), app, entry, port, compose, k8s)));
        List<String> notes = new ArrayList<>();
        String mode = "template";
        String modelName = "deploy templates";
        notes.add("Deployment files come from BOT's built-in " + ctx.languageName() + " deploy template (deterministic, "
                + "no AI needed): Dockerfile, build scripts, GitHub Actions CI/CD" + (compose ? ", docker-compose" : "")
                + (k8s ? ", Kubernetes YAML" : "") + " and DEPLOY.md.");
        notes.add(entry.isBlank() && ctx.language() != StageLanguage.PLSQL
                ? "No entry point (main program) found: the image builds and tests the code; add a main program and update CMD."
                : ctx.language() == StageLanguage.PLSQL ? "The image is Oracle Database Free; the PL/SQL is installed at first start-up."
                : "Entry point: " + entry + ".");
        if (!DeployTemplates.hasRecipe(ctx.lang())) {
            if (model.isPresent()) {
                try {
                    String answer = StageFiles.stripFences(model.get().complete(system(StageType.DEPLOY, ctx),
                            StagePrompts.deployAny(code, ctx.languageName(), port))).strip();
                    if (answer.contains("FROM ")) {
                        files.put("Dockerfile", answer + "\n");
                        mode = "llm";
                        modelName = model.get().describe();
                        notes.add("Dockerfile written by " + model.get().describe() + " for " + ctx.languageName() + ".");
                    }
                } catch (RuntimeException e) {
                    notes.add("The AI model failed (" + e.getMessage() + "); the Dockerfile is a generic template with TODOs.");
                }
            } else {
                notes.add("The Dockerfile is a generic template with TODOs; connect an AI model for one tailored to "
                        + ctx.languageName() + ".");
            }
        }
        Map<String, String> extras = new LinkedHashMap<>(DeployTemplates.scaffold(ctx.lang(), app));
        if (ctx.java() && !sources.isEmpty()) {
            extras.put("pom.xml", pomFor(sources));
        }
        return new Generated(files, extras, notes, mode, modelName);
    }

    /** Image / app name from the run's stories when there are any, else from the code. */
    static String appName(Ctx ctx, String code, Path dir) {
        String stories = dir == null ? "" : readQuietly(dir.resolve("stories.md"));
        String basis = stories.isBlank() ? code : stories;
        String feature = StageSample.detect(basis, ctx.domain()).featureName(basis);
        String kebab = StagePrompts.snake(feature).replace('_', '-').replaceAll("[^a-z0-9-]", "");
        return kebab.isBlank() ? "app" : kebab;
    }

    private static String entryPoint(StageLanguage lang, Map<String, String> sources, String app) {
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String path = e.getKey();
            String code = e.getValue();
            switch (lang) {
                case JAVA -> {
                    if (JAVA_MAIN.matcher(code).find()) {
                        String pkg = StageFiles.packageOf(code).orElse("");
                        String type = StageFiles.primaryType(code).orElse("Main");
                        return pkg.isBlank() ? type : pkg + "." + type;
                    }
                }
                case PYTHON -> {
                    if (code.contains("__name__") && code.contains("__main__") && path.endsWith(".py")) {
                        return path.substring(0, path.length() - 3).replace('/', '.');
                    }
                }
                case JAVASCRIPT -> {
                    if (code.contains("require.main === module") || code.contains(".listen(") || path.endsWith("/index.js")) {
                        return path;
                    }
                }
                case TYPESCRIPT -> {
                    if (code.contains("require.main === module") || code.contains(".listen(") || path.endsWith("/index.ts")) {
                        return "dist/" + path.substring(path.lastIndexOf('/') + 1).replaceAll("\\.ts$", ".js");
                    }
                }
                case GO -> {
                    if (code.contains("package main")) {
                        return app;
                    }
                }
                case RUST, CPP, C -> {
                    if (code.contains("fn main(") || code.matches("(?s).*\\bint\\s+main\\s*\\(.*")) {
                        return app.replace('-', '_');
                    }
                }
                case PHP, RUBY -> {
                    return path;
                }
                default -> {
                }
            }
        }
        return "";
    }

    private static int port(StageRequest request) {
        try {
            int p = Integer.parseInt(request.option("port", "8080"));
            return p > 0 && p < 65536 ? p : 8080;
        } catch (NumberFormatException e) {
            return 8080;
        }
    }

    /** Runs docker build in the run folder when Docker is available; returns a note either way. */
    private static String dockerBuild(Path dir, String app) {
        try {
            Process check = new ProcessBuilder("docker", "version", "--format", "{{.Server.Version}}")
                    .redirectErrorStream(true).start();
            if (!check.waitFor(15, TimeUnit.SECONDS) || check.exitValue() != 0) {
                check.destroyForcibly();
                return "Build locally skipped: Docker is installed but its engine is not running.";
            }
        } catch (IOException e) {
            return "Build locally skipped: Docker is not installed on this machine.";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Build locally skipped: interrupted.";
        }
        try {
            Process build = new ProcessBuilder("docker", "build", "-t", app + ":latest", ".")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            byte[] out = build.getInputStream().readAllBytes();
            if (!build.waitFor(10, TimeUnit.MINUTES)) {
                build.destroyForcibly();
                return "Local docker build timed out after 10 minutes.";
            }
            List<String> lines = new String(out, StandardCharsets.UTF_8).lines().toList();
            String tail = String.join(" | ", lines.subList(Math.max(0, lines.size() - 3), lines.size()));
            return build.exitValue() == 0
                    ? "Local docker build succeeded: image " + app + ":latest."
                    : "Local docker build failed (exit " + build.exitValue() + "): " + tail;
        } catch (IOException e) {
            return "Local docker build could not start: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Local docker build interrupted.";
        }
    }

    // ---------------------------------------------------------------- trainer

    private StageResponse trainer(StageRequest request, long start) {
        String input = request.input() == null ? "" : request.input().strip();
        String requested = request.runId() != null && RUN_ID.matcher(request.runId().trim()).matches()
                ? request.runId().trim() : RUN_ID.matcher(input).matches() ? input : null;
        Map<String, Object> run = requested == null ? Map.of() : readRun(requested);
        Ctx ctx = context(request);
        StageDomain domain = run.get("domain") instanceof String d ? StageDomain.from(d) : ctx.domain();
        if (run.get("domainLabel") instanceof String label) {
            domain = StageDomain.from(label);
        }
        String languageId = run.get("language") instanceof String l ? l : ctx.lang().id();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> stages = run.get("stages") instanceof Map<?, ?> m
                ? (Map<String, Map<String, Object>>) m : Map.of();

        List<String> notes = new ArrayList<>();
        List<String> saved = new ArrayList<>();
        if (request.flag("save")) {
            if (requested == null || stages.isEmpty()) {
                throw new IllegalArgumentException("Nothing to save: run the stages first, or give the run id to save.");
            }
            Set<String> wanted = new HashSet<>(Arrays.asList(request.option("stages", "ba,developer,tester,deploy")
                    .toLowerCase(Locale.ROOT).split("\\s*,\\s*")));
            for (StageType stage : WORK_STAGES) {
                Map<String, Object> entry = stages.get(stage.id());
                if (entry == null || !wanted.contains(stage.id())) {
                    continue;
                }
                String output = stageOutput(requested, stage, entry);
                if (output.isBlank()) {
                    continue;
                }
                String key = entry.get("trainingKey") instanceof String k ? k : domain.id();
                training.recordStageExample(stage.id(), key, String.valueOf(entry.getOrDefault("input", "")), output,
                        request.option("feedback", ""));
                saved.add(stage.title());
            }
            notes.add(saved.isEmpty() ? "No stage outputs found in run " + requested + " to save."
                    : "Saved " + saved.size() + " training example(s): " + String.join(", ", saved) + ".");
        } else {
            notes.add(requested == null
                    ? "No run given: showing what BOT has learned so far. Run the stages, or paste a run id, to review a run."
                    : "Review the outputs, correct them in their stage cards, then press \"Save this run as training\". "
                    + "Nothing is saved automatically.");
        }
        String report = trainingReport(requested, stages, domain, languageId, saved);
        String runId = resolveRunId(requested);
        Path dir = runDir(runId);
        Map<String, String> files = new LinkedHashMap<>();
        files.put("training/TRAINING_REPORT.md", report);
        writeFiles(dir, files);
        Generated g = new Generated(files, Map.of(), notes, "trainer", "training store");
        recordRun(runId, StageType.TRAINER, new Ctx(domain, LanguageProfile.resolve(languageId, languageId),
                StageAutomation.BOTH, request), input, g, files.keySet(), System.currentTimeMillis() - start);
        long elapsed = System.currentTimeMillis() - start;
        return new StageResponse(true, StageType.TRAINER.id(), StageType.TRAINER.title(), domain.id(), languageId, runId,
                "trainer", "training store", report, files.entrySet().stream()
                .map(e -> new StageFile(e.getKey(), e.getValue())).toList(), notes, "", "", elapsed);
    }

    private String trainingReport(String runId, Map<String, Map<String, Object>> stages, StageDomain domain,
                                  String languageId, List<String> saved) {
        StringBuilder sb = new StringBuilder("# Training report\n\n");
        if (runId != null && !stages.isEmpty()) {
            sb.append("Run `").append(runId).append("` Â· domain **").append(domain.label()).append("** Â· language **")
                    .append(languageId).append("**\n\n## Stages in this run\n\n| Stage | Mode | Files | Notes |\n|---|---|---|---|\n");
            for (StageType stage : WORK_STAGES) {
                Map<String, Object> e = stages.get(stage.id());
                if (e == null) {
                    sb.append("| ").append(stage.title()).append(" | not run | 0 | |\n");
                    continue;
                }
                Object files = e.get("files");
                Object notes = e.get("notes");
                sb.append("| ").append(stage.title()).append(" | ").append(e.getOrDefault("mode", "?")).append(" | ")
                        .append(files instanceof List<?> fl ? fl.size() : 0).append(" | ")
                        .append(notes instanceof List<?> nl && !nl.isEmpty()
                                ? String.valueOf(nl.get(nl.size() - 1)).replace("|", "/").replace("\n", " ") : "")
                        .append(" |\n");
            }
            sb.append('\n');
        }
        Map<String, Object> stats = training.stageStats();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Integer>> byStage = (Map<String, Map<String, Integer>>) stats.get("stages");
        sb.append("## What BOT has learned\n\n").append(stats.get("total")).append(" approved example(s) in ")
                .append("data/bot-training.json.\n\n| Stage | Approved examples (by domain / language) |\n|---|---|\n");
        for (StageType stage : WORK_STAGES) {
            Map<String, Integer> counts = byStage.getOrDefault(stage.id(), Map.of());
            if (stage == StageType.TESTER) {
                counts = merge(counts, byStage.get("unit-test"), byStage.get("functional-test"));
            }
            sb.append("| ").append(stage.title()).append(" | ").append(counts.isEmpty() ? "none yet" : counts.entrySet()
                    .stream().map(c -> c.getKey() + ": " + c.getValue()).reduce((a, b) -> a + ", " + b).orElse(""))
                    .append(" |\n");
        }
        if (!saved.isEmpty()) {
            sb.append("\n## Saved now\n\n");
            saved.forEach(s -> sb.append("- ").append(s).append('\n'));
        }
        sb.append("\n## How the examples are used\n\n")
                .append("- With an AI model, each stage gets its 2 latest approved examples for the same domain (and language ")
                .append("for code stages) as few-shot examples, including your feedback.\n")
                .append("- Offline, an approved output is reused when the same (or a very similar) input comes again.\n")
                .append("- Delete bad examples in the Trainer card, or with DELETE /api/stages/training/examples/{id}.\n");
        return sb.toString();
    }

    @SafeVarargs
    private static Map<String, Integer> merge(Map<String, Integer> base, Map<String, Integer>... more) {
        Map<String, Integer> out = new LinkedHashMap<>(base);
        for (Map<String, Integer> m : more) {
            if (m != null) {
                m.forEach((k, v) -> out.merge(k, v, Integer::sum));
            }
        }
        return out;
    }

    /** A stage's output as shown in its card, rebuilt from the run folder. */
    private String stageOutput(String runId, StageType stage, Map<String, Object> entry) {
        Path dir = runDir(runId);
        if (stage == StageType.BA) {
            return readQuietly(dir.resolve("stories.md"));
        }
        List<StageFile> files = new ArrayList<>();
        if (entry.get("files") instanceof List<?> list) {
            for (Object p : list) {
                String content = readQuietly(dir.resolve(String.valueOf(p)));
                if (!content.isBlank()) {
                    files.add(new StageFile(String.valueOf(p), content));
                }
            }
        }
        return files.isEmpty() ? "" : StageFiles.bundle(files);
    }

    /** Developer files of a run, as a bundle; empty when the Developer has not run. */
    String developerCode(String runId) {
        Map<String, Object> run = readRun(runId);
        if (run.get("stages") instanceof Map<?, ?> stages && stages.get("developer") instanceof Map<?, ?> dev) {
            @SuppressWarnings("unchecked")
            String out = stageOutput(runId, StageType.DEVELOPER, (Map<String, Object>) dev);
            return out;
        }
        return "";
    }

    // ---------------------------------------------------------------- training

    /** Offline only: reuse a reviewer-approved output when the same (or nearly the same) input comes again. */
    private Optional<Generated> recall(StageType stage, Ctx ctx, String input) {
        Map<String, String> best = null;
        double bestScore = 0;
        for (Map<String, String> ex : training.stageExamples(stage.id(), ctx.key(stage), 50)) {
            double score = similarity(input, ex.getOrDefault("input", ""));
            if (score > bestScore) {
                bestScore = score;
                best = ex;
            }
        }
        if (best == null || bestScore < RECALL_SIMILARITY) {
            return Optional.empty();
        }
        Map<String, String> files = filesFromOutput(stage, ctx, best.getOrDefault("input", input), best.get("output"));
        if (files.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> extras = new LinkedHashMap<>();
        if (stage == StageType.DEVELOPER) {
            if (ctx.java()) {
                extras.put("pom.xml", pomFor(files));
            } else if (ctx.language() == StageLanguage.PYTHON) {
                extras.putAll(PythonTemplates.scaffold());
            } else {
                extras.putAll(DeployTemplates.scaffold(ctx.lang(), appName(ctx, input, null)));
            }
        } else if (stage == StageType.TESTER) {
            addAutomationScaffolds(files, ctx, input, extras);
        }
        List<String> notes = new ArrayList<>();
        notes.add("Recalled a reviewer-approved training example (" + Math.round(bestScore * 100)
                + "% similar input, approved " + best.getOrDefault("at", "earlier") + ").");
        String feedback = best.getOrDefault("feedback", "");
        if (!feedback.isBlank()) {
            notes.add("Reviewer feedback on that example: " + feedback);
        }
        if (stage == StageType.DEVELOPER) {
            notes.add(checkNote(ctx.lang(), files));
        }
        return Optional.of(new Generated(files, extras, notes, "trained", "approved training example"));
    }

    private static void addAutomationScaffolds(Map<String, String> files, Ctx ctx, String input, Map<String, String> extras) {
        String baseUrl = baseUrl(ctx.request());
        for (StageAutomation a : StageAutomation.values()) {
            if (a.root().isEmpty() || files.keySet().stream().noneMatch(p -> p.startsWith(a.root()))) {
                continue;
            }
            switch (a) {
                case SELENIUM_JAVA -> extras.putAll(SeleniumTemplates.javaScaffold(CodeTemplates.packageFor(ctx.domain(), input)));
                case SELENIUM_RUBY -> extras.putAll(SeleniumTemplates.rubyScaffold(baseUrl));
                default -> extras.putAll(AutomationTemplates.scaffold(a, baseUrl));
            }
        }
    }

    static double similarity(String a, String b) {
        Set<String> x = tokens(a);
        Set<String> y = tokens(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(x);
        inter.retainAll(y);
        Set<String> union = new HashSet<>(x);
        union.addAll(y);
        return (double) inter.size() / union.size();
    }

    private static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        for (String w : (text == null ? "" : text).toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (w.length() > 2) {
                out.add(w);
            }
        }
        return out;
    }

    /** Stores an approved (possibly corrected) output, and rewrites the run's files when it was edited. */
    public Map<String, Object> approve(StageType stage, String domainId, String language, String input, String output,
                                       String feedback, String runId) {
        if (stage == StageType.TRAINER) {
            throw new IllegalArgumentException("The Trainer report is not a training example; approve the other stages, "
                    + "or use \"Save this run as training\".");
        }
        if (output == null || output.isBlank()) {
            throw new IllegalArgumentException("Nothing to approve: run the stage first or paste the corrected output.");
        }
        Ctx ctx = context(new StageRequest(domainId, input, language == null ? Map.of() : Map.of("language", language), runId));
        String key = ctx.key(stage);
        Map<String, String> entry = training.recordStageExample(stage.id(), key,
                input == null ? "" : input, output, feedback == null ? "" : feedback);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.get("id"));
        result.put("stage", stage.id());
        result.put("domain", key);
        if (runId != null && RUN_ID.matcher(runId).matches() && Files.isDirectory(runDir(runId))) {
            Map<String, String> files = filesFromOutput(stage, ctx, input == null ? "" : input, output);
            writeFiles(runDir(runId), files);
            result.put("savedFiles", files.size());
        }
        result.put("examples", training.stageExamples(stage.id(), key, 1000).size());
        result.put("stats", training.stageStats());
        return result;
    }

    /** Kept for callers that only know the domain (Java). */
    public Map<String, Object> approve(StageType stage, String domainId, String input, String output, String feedback,
                                       String runId) {
        return approve(stage, domainId, null, input, output, feedback, runId);
    }

    public Map<String, Object> trainingStats() {
        return training.stageStats();
    }

    /** Approved examples (previews only) for the Trainer card. */
    public List<Map<String, String>> trainingExamples(String stage, String domain, int limit) {
        String stageId = stage == null || stage.isBlank() ? "" : StageType.fromId(stage).map(StageType::id).orElse(stage);
        return training.listStageExamples(stageId, domain, Math.max(1, Math.min(limit, 500))).stream().map(e -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("id", e.get("id"));
            m.put("at", e.get("at"));
            m.put("stage", e.get("stage"));
            m.put("domain", e.get("domain"));
            m.put("input", StagePrompts.clip(e.getOrDefault("input", ""), 300));
            m.put("output", StagePrompts.clip(e.getOrDefault("output", ""), 600));
            m.put("feedback", e.getOrDefault("feedback", ""));
            return m;
        }).toList();
    }

    public boolean deleteTrainingExample(String id) {
        return id != null && !id.isBlank() && training.deleteStageExample(id.trim());
    }

    /** Splits a stage's text output (as shown and edited in the UI) back into files. */
    Map<String, String> filesFromOutput(StageType stage, Ctx ctx, String input, String output) {
        Map<String, String> files = new LinkedHashMap<>();
        if (output == null || output.isBlank()) {
            return files;
        }
        StageDomain domain = ctx.domain();
        switch (stage) {
            case BA -> files.put("stories.md", output.strip() + "\n");
            case DEVELOPER -> {
                if (ctx.java()) {
                    files.putAll(StageFiles.javaSources(output, "src/main/java", CodeTemplates.packageFor(domain, input)));
                } else {
                    String base = StagePrompts.snake(StageSample.detect(input, domain).featureName(input));
                    files.putAll(codeFiles(output, ctx.lang().srcDir(), ctx.lang().ext(), base));
                }
            }
            case TESTER -> {
                for (Map.Entry<String, String> part : StageFiles.split(output)) {
                    String path = StageFiles.safeRelative(part.getKey());
                    String content = part.getValue();
                    boolean ruby = path.startsWith(SeleniumTemplates.RUBY_ROOT) || path.endsWith(".rb")
                            || (path.isBlank() && (content.contains("RSpec") || (content.contains("def ") && !content.contains(";"))
                            && content.contains(" do")));
                    if (path.startsWith(SeleniumTemplates.JAVA_ROOT) && path.endsWith(".java")) {
                        files.putAll(StageFiles.javaSources(content, SeleniumTemplates.JAVA_ROOT + "src/test/java",
                                SeleniumTemplates.uiPackage(domain, input)));
                    } else if (!path.isBlank() && StageAutomation.isAutomationPath(path) && !ruby) {
                        files.put(path, content);
                    } else if (ruby) {
                        files.putAll(rubyFiles("FILE: " + path + "\n" + content, SeleniumTemplates.feature(domain, input)));
                    } else if (path.endsWith(".java") || (path.isBlank() && StageFiles.looksLikeJava(content))) {
                        boolean ui = content.contains("WebDriver");
                        files.putAll(StageFiles.javaSources(content, ui ? SeleniumTemplates.JAVA_ROOT + "src/test/java"
                                : "src/test/java", ui ? SeleniumTemplates.uiPackage(domain, input) : null));
                    } else if (!path.isBlank()) {
                        files.put(path, content);
                    } else {
                        files.putAll(codeFiles(content, ctx.lang().testDir(), ctx.lang().ext(), "test_generated"));
                    }
                }
            }
            case DEPLOY -> {
                for (Map.Entry<String, String> part : StageFiles.split(output)) {
                    String path = StageFiles.safeRelative(part.getKey());
                    if (!path.isBlank()) {
                        files.put(path, part.getValue());
                    } else if (part.getValue().contains("FROM ")) {
                        files.put("Dockerfile", part.getValue());
                    }
                }
            }
            case TRAINER -> {
            }
        }
        return files;
    }

    // ---------------------------------------------------------------- files and runs

    private String output(StageType stage, Map<String, String> files) {
        List<StageFile> list = files.entrySet().stream().map(e -> new StageFile(e.getKey(), e.getValue())).toList();
        return stage == StageType.BA ? files.getOrDefault("stories.md", "") : StageFiles.bundle(list);
    }

    private static String compileNote(Map<String, String> files, List<Path> classpath) {
        Map<String, String> java = new LinkedHashMap<>();
        files.forEach((p, c) -> {
            if (p.endsWith(".java")) {
                java.put(p, c);
            }
        });
        return JavaCompileCheck.compile(java, classpath).summary(java.size());
    }

    private static String pomFor(Map<String, String> files) {
        String pkg = files.values().stream().map(StageFiles::packageOf).flatMap(Optional::stream).findFirst().orElse("com.example");
        int dot = pkg.lastIndexOf('.');
        String group = dot > 0 ? pkg.substring(0, dot) : pkg;
        String artifact = (dot > 0 ? pkg.substring(dot + 1) : pkg) + "-service";
        return CodeTemplates.pom(group, artifact);
    }

    /** When tests or deployment files are generated on their own, copy the code into the run so it builds by itself. */
    private void addMissingSources(Path dir, Ctx ctx, String code, Map<String, String> extras) {
        Map<String, String> sources = sourceFiles(ctx, code);
        sources.forEach((path, content) -> {
            if (!Files.exists(dir.resolve(path))) {
                extras.putIfAbsent(path, content);
            }
        });
        if (ctx.java() && !sources.isEmpty() && !Files.exists(dir.resolve("pom.xml"))) {
            extras.putIfAbsent("pom.xml", pomFor(sources));
        }
        if (ctx.language() == StageLanguage.PYTHON && sources.keySet().stream().anyMatch(p -> p.startsWith("app/"))
                && !Files.exists(dir.resolve("app/__init__.py"))) {
            extras.putIfAbsent("app/__init__.py", "");
        }
    }

    /** The requested run id when valid, else a new one (also used by the stories importer). */
    public String resolveRunId(String requested) {
        if (requested != null && RUN_ID.matcher(requested.trim()).matches()) {
            return requested.trim();
        }
        return LocalDateTime.now().format(RUN_TIME) + "-"
                + String.format("%06x", ThreadLocalRandom.current().nextInt(0x1000000));
    }

    public Path runDir(String runId) {
        if (runId == null || !RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("Unknown run id.");
        }
        return root.resolve(runId);
    }

    private void writeFiles(Path dir, Map<String, String> files) {
        try {
            for (Map.Entry<String, String> e : files.entrySet()) {
                Path target = resolveInside(dir, e.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, e.getValue(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save generated files: " + e.getMessage(), e);
        }
    }

    private static Path resolveInside(Path dir, String relative) {
        String safe = StageFiles.safeRelative(relative);
        if (safe.isBlank()) {
            throw new IllegalArgumentException("Invalid file path.");
        }
        Path target = dir.resolve(safe).normalize();
        if (!target.startsWith(dir)) {
            throw new IllegalArgumentException("Invalid file path.");
        }
        return target;
    }

    private Map<String, Object> readRun(String runId) {
        Path file = runDir(runId).resolve("run.json");
        try {
            return Files.exists(file)
                    ? mapper.readValue(Files.readString(file), new TypeReference<LinkedHashMap<String, Object>>() {})
                    : new LinkedHashMap<>();
        } catch (IOException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private synchronized void recordRun(String runId, StageType stage, Ctx ctx, String input, Generated g,
                                        Set<String> written, long durationMs) {
        Path file = runDir(runId).resolve("run.json");
        try {
            Map<String, Object> run = readRun(runId);
            run.put("runId", runId);
            if (stage != StageType.TRAINER || !run.containsKey("domain")) {
                run.put("domain", ctx.domain().id());
                run.put("domainLabel", ctx.domain().label());
                run.put("language", ctx.lang().id());
            }
            run.put("updatedAt", Instant.now().toString());
            @SuppressWarnings("unchecked")
            Map<String, Object> stages = (Map<String, Object>) run.computeIfAbsent("stages", k -> new LinkedHashMap<>());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("at", Instant.now().toString());
            entry.put("mode", g.mode());
            entry.put("durationMs", durationMs);
            entry.put("model", g.model());
            entry.put("trainingKey", ctx.key(stage));
            entry.put("input", StagePrompts.clip(input, 4000));
            entry.put("files", new ArrayList<>(g.files().keySet()));
            entry.put("written", new ArrayList<>(written));
            entry.put("notes", g.notes());
            stages.put(stage.id(), entry);
            Files.createDirectories(file.getParent());
            Files.writeString(file, mapper.writeValueAsString(run), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not update {}: {}", file, e.getMessage());
        }
    }

    public Optional<Path> file(String runId, String relative) {
        Path dir = runDir(runId);
        Path target = resolveInside(dir, relative);
        return Files.isRegularFile(target) ? Optional.of(target) : Optional.empty();
    }

    /**
     * ZIP of the whole run, or of one stage's files when {@code stage} is given. The Tester and Deploy ZIPs also
     * contain the Developer's code so they build on their own.
     */
    public byte[] zip(String runId, StageType stage) {
        Path dir = runDir(runId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Run " + runId + " not found.");
        }
        Set<String> only = stage == null ? null : stageFiles(runId, stage);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes); Stream<Path> walk = Files.walk(dir)) {
            Set<String> names = new LinkedHashSet<>();
            Map<String, String> code = new LinkedHashMap<>();
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if (only != null && !only.contains(rel)) {
                    continue;
                }
                names.add(rel);
                if (StageBusinessLogic.isCode(rel) && !rel.startsWith("training/")) {
                    code.put(rel, readQuietly(p));
                }
                zip.putNextEntry(new ZipEntry(runId + "/" + rel));
                Files.copy(p, zip);
                zip.closeEntry();
            }
            // business logic goes with every code download: one file per code file plus a stage-level summary
            for (Map.Entry<String, String> e : code.entrySet()) {
                String name = StageBusinessLogic.companionName(e.getKey());
                if (names.add(name)) {
                    addText(zip, runId + "/" + name, StageBusinessLogic.fileMarkdown(e.getKey(), e.getValue()));
                }
            }
            if (names.add(StageBusinessLogic.PROJECT_FILE)) {
                addText(zip, runId + "/" + StageBusinessLogic.PROJECT_FILE, projectLogic(runId, stage, code));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the ZIP: " + e.getMessage(), e);
        }
        return bytes.toByteArray();
    }

    private static void addText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private String projectLogic(String runId, StageType stage, Map<String, String> code) {
        Path dir = runDir(runId);
        String title = (stage == null ? "run " : stage.title() + " output, run ") + runId;
        return StageBusinessLogic.projectMarkdown(title, readQuietly(dir.resolve("stories.md")), code);
    }

    /**
     * Business logic as Markdown: of one code file ({@code path}) or, when blank, of the whole stage (or run when
     * {@code stage} is null). Derived from the {@code Business rule:} comments in the generated code, so it needs no model.
     */
    public String businessLogic(String runId, StageType stage, String path) {
        Path dir = runDir(runId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Run " + runId + " not found.");
        }
        if (path != null && !path.isBlank()) {
            Path file = resolveInside(dir, path);
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("File not found: " + path);
            }
            return StageBusinessLogic.fileMarkdown(path.replace('\\', '/'), readQuietly(file));
        }
        Set<String> only = stage == null ? null : stageFiles(runId, stage);
        Map<String, String> code = new LinkedHashMap<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                if ((only == null || only.contains(rel)) && StageBusinessLogic.isCode(rel) && !rel.startsWith("training/")) {
                    code.put(rel, readQuietly(p));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the run: " + e.getMessage(), e);
        }
        return projectLogic(runId, stage, code);
    }

    /**
     * Copies the files of one stage (Developer; Tester and Deploy include the Developer's code) or, when {@code stage} is
     * null, of the whole run (without run.json, training reports and imported story files) into {@code target}, ready to
     * push to Git. Returns the number of files copied.
     */
    public int exportFiles(String runId, StageType stage, Path target) {
        Path dir = runDir(runId);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Run " + runId + " not found.");
        }
        Set<String> only = stage == null ? null : stageFiles(runId, stage);
        int copied = 0;
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.filter(Files::isRegularFile).sorted().toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                boolean skip = only != null ? !only.contains(rel)
                        : rel.equals("run.json") || rel.startsWith("training/") || rel.startsWith("stories-input/");
                if (skip) {
                    continue;
                }
                Path dest = target.resolve(rel).normalize();
                Files.createDirectories(dest.getParent());
                Files.copy(p, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not prepare the files: " + e.getMessage(), e);
        }
        return copied;
    }

    private Set<String> stageFiles(String runId, StageType stage) {
        Map<String, Object> run = readRun(runId);
        Set<String> out = new LinkedHashSet<>();
        if (!(run.get("stages") instanceof Map<?, ?> stages)) {
            return out;
        }
        List<StageType> include = switch (stage) {
            case TESTER, DEPLOY -> List.of(StageType.DEVELOPER, stage);
            default -> List.of(stage);
        };
        for (StageType s : include) {
            if (stages.get(s.id()) instanceof Map<?, ?> entry) {
                Object list = entry.get("written") != null ? entry.get("written") : entry.get("files");
                if (list instanceof List<?> l) {
                    l.forEach(p -> out.add(String.valueOf(p)));
                }
            }
        }
        if (stage == StageType.TRAINER) {
            out.add("run.json");
        }
        return out;
    }

    private static String readQuietly(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readString(file) : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static String baseUrl(StageRequest request) {
        String url = request.option("baseUrl", DEFAULT_BASE_URL);
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return BASE_URL.matcher(url).matches() ? url : DEFAULT_BASE_URL;
    }
}
