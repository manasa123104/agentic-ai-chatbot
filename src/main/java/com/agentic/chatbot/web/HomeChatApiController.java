package com.agentic.chatbot.web;

import com.agentic.chatbot.agent.PipelineListener;
import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.ChatHistoryEntry;
import com.agentic.chatbot.model.ConversionRequest;
import com.agentic.chatbot.model.ConversionResponse;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.service.ChatHistoryService;
import com.agentic.chatbot.service.ChatImageService;
import com.agentic.chatbot.service.CodeConversionService;
import com.agentic.chatbot.service.HomeChatService;
import com.agentic.chatbot.service.HomeChatService.Intent;
import com.agentic.chatbot.service.RunControl;
import com.agentic.chatbot.service.SiteEditService;
import com.agentic.chatbot.service.SitePatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The home chat ("/") works like ChatGPT: one conversation, answers that stream in as they are written.
 *
 * <p>{@code POST /api/chat/stream} takes the new message plus the earlier turns and answers with newline-delimited JSON
 * events: {@code meta} (what BOT understood), {@code status} (a short "thinking" line), {@code step} (the Business
 * Analyst / Developer / Tester / Trainer starting and finishing, live), {@code delta} (a piece of BOT's written answer),
 * {@code result} (the full result of a build, edit or conversion, for the cards and links), {@code ping} (keeps the
 * connection open through proxies) and finally {@code done}.
 *
 * <p>Follow-ups edit the site that was generated: "make the hero shorter" or "rename Plans to Pricing" is applied to the
 * pages on disk ({@link SitePatcher}), a colour or font change patches the theme, and only a structural request
 * ("add a chatbot agent") rebuilds the site from its own stored brief, keeping its look and the earlier edits. Plain
 * questions are answered by the connected model
 * with the conversation so far. Builds keep running on the server if the browser goes away, as before.
 * {@code /api/pipeline} (one JSON answer at the end) is unchanged.
 */
@RestController
public class HomeChatApiController {

    private static final Logger log = LoggerFactory.getLogger(HomeChatApiController.class);
    private static final ObjectMapper LINES = new ObjectMapper();
    private static final int MAX_TURNS = 14;
    private static final int MAX_TURN_CHARS = 2000;
    private static final int MAX_ANSWER_TOKENS = 700;
    private static final Duration ANSWER_TIMEOUT = Duration.ofSeconds(60);
    private static final Map<String, Object> END = Map.of("type", "end");

    private final PipelineOrchestrator orchestrator;
    private final ChatHistoryService historyService;
    private final SiteEditService siteEditService;
    private final CodeConversionService conversionService;
    private final HomeChatService chat;
    private final ChatImageService images;
    private final SitePatcher patcher;
    private final LlmClient llm;
    private final ExecutorService workers = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "home-chat");
        t.setDaemon(true);
        return t;
    });
    /** Pause between pieces of a written answer, so templated text still arrives like typing; tests set it to 0. */
    private final int pieceDelayMs;

    @Autowired
    public HomeChatApiController(
            PipelineOrchestrator orchestrator,
            ChatHistoryService historyService,
            SiteEditService siteEditService,
            CodeConversionService conversionService,
            HomeChatService chat,
            ChatImageService images,
            SitePatcher patcher,
            LlmClient llm) {
        this(orchestrator, historyService, siteEditService, conversionService, chat, images, patcher, llm, 25);
    }

    HomeChatApiController(
            PipelineOrchestrator orchestrator,
            ChatHistoryService historyService,
            SiteEditService siteEditService,
            CodeConversionService conversionService,
            HomeChatService chat,
            ChatImageService images,
            SitePatcher patcher,
            LlmClient llm,
            int pieceDelayMs) {
        this.patcher = patcher;
        this.orchestrator = orchestrator;
        this.historyService = historyService;
        this.siteEditService = siteEditService;
        this.conversionService = conversionService;
        this.chat = chat;
        this.images = images;
        this.llm = llm;
        this.pieceDelayMs = pieceDelayMs;
    }

    /** One request from the browser. */
    private record Turn(String message, String systemPrompt, String runId, boolean resume,
                        List<LlmClient.ChatTurn> earlier, List<?> pictures) {
    }

    /** What happened to the pictures of a message; {@code looked} is true when a vision model described them. */
    private record Pictures(boolean looked, String description, List<ChatImageService.Saved> saved, List<String> problems) {
        static final Pictures NONE = new Pictures(false, "", List.of(), List.of());

        boolean any() {
            return !saved.isEmpty() || !problems.isEmpty();
        }

        List<String> urls() {
            return saved.stream().map(ChatImageService.Saved::url).toList();
        }
    }

    @PostMapping(value = "/api/chat/stream", produces = "application/x-ndjson")
    public ResponseEntity<StreamingResponseBody> stream(@RequestBody Map<String, Object> body, HttpSession session) {
        Turn turn = parse(body);
        StreamingResponseBody out = os -> pump(os, session, turn);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-ndjson"))
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no") // proxies must pass each event on at once
                .body(out);
    }

    // ------------------------------------------------------------------ plumbing

    /** Events collected from the worker thread; {@code gone} turns true once the browser stopped listening. */
    private static final class Sink {
        private final BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        private final AtomicBoolean gone = new AtomicBoolean();

        void send(Map<String, Object> event) {
            queue.add(event);
        }

        /** For live model answers: stops generating when the browser has closed the chat. */
        void sendOrStop(Map<String, Object> event) {
            if (gone.get()) {
                throw new UncheckedIOException(new IOException("client closed the chat"));
            }
            queue.add(event);
        }
    }

    private void pump(OutputStream os, HttpSession session, Turn turn) throws IOException {
        Sink sink = new Sink();
        workers.submit(() -> {
            try {
                converse(turn, session, sink);
            } catch (UncheckedIOException ignored) {
                // the visitor closed the chat while a model answer was being written
            } catch (Throwable e) {
                log.warn("Home chat failed", e);
                sink.send(ev("type", "error", "text", "Something went wrong on my side. Please try again in a moment."));
            } finally {
                sink.send(ev("type", "done"));
                sink.queue.add(END);
            }
        });
        Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        try {
            while (true) {
                Map<String, Object> event = sink.queue.poll(10, TimeUnit.SECONDS);
                if (event == null) {
                    event = ev("type", "ping");
                }
                if (event == END) {
                    return;
                }
                w.write(LINES.writeValueAsString(event));
                w.write('\n');
                w.flush();
            }
        } catch (IOException e) {
            sink.gone.set(true);
            throw e;
        } catch (InterruptedException e) {
            sink.gone.set(true);
            Thread.currentThread().interrupt();
        }
    }

    private static Map<String, Object> ev(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
            }
        }
        return m;
    }

    private static Turn parse(Map<String, Object> body) {
        String message = body.get("message") == null ? "" : String.valueOf(body.get("message")).trim();
        String system = body.get("systemPrompt") == null ? "" : String.valueOf(body.get("systemPrompt")).trim();
        String runId = body.get("runId") == null ? null : String.valueOf(body.get("runId"));
        boolean resume = Boolean.TRUE.equals(body.get("resume"));
        List<LlmClient.ChatTurn> earlier = new ArrayList<>();
        if (body.get("history") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m && m.get("role") != null && m.get("content") != null) {
                    String role = "assistant".equals(String.valueOf(m.get("role"))) ? "assistant" : "user";
                    String content = String.valueOf(m.get("content")).strip();
                    if (!content.isEmpty()) {
                        earlier.add(new LlmClient.ChatTurn(role,
                                content.length() > MAX_TURN_CHARS ? content.substring(0, MAX_TURN_CHARS) : content));
                    }
                }
            }
        }
        if (earlier.size() > MAX_TURNS) {
            earlier = new ArrayList<>(earlier.subList(earlier.size() - MAX_TURNS, earlier.size()));
        }
        List<?> pictures = body.get("images") instanceof List<?> l ? l : List.of();
        return new Turn(message, system, runId, resume, earlier, pictures);
    }

    // ------------------------------------------------------------------ the conversation

    private void converse(Turn turn, HttpSession session, Sink sink) {
        long started = System.currentTimeMillis();
        String runId = RunControl.validId(turn.runId()) ? turn.runId() : PipelineOrchestrator.newRunId();
        PipelineRequest request = new PipelineRequest();
        request.setSystemPrompt(turn.systemPrompt());
        request.setRunId(runId);
        request.setRunUnitTests(true);
        request.setRunFunctionalTests(true);

        if (turn.resume() && orchestrator.hasCheckpoint(runId)) {
            String story = orchestrator.checkpoint(runId).map(c -> c.userStory).orElse(turn.message());
            request.setUserStory(story);
            request.setResume(true);
            sink.send(ev("type", "meta", "intent", "resume", "runId", runId));
            sink.send(ev("type", "status", "text", "Picking up where I left off\u2026"));
            build(request, turn.message(), story, false, Pictures.NONE, session, sink, started);
            return;
        }
        String message = turn.message();
        if (message.isBlank()) {
            sink.send(ev("type", "meta", "intent", "chat", "runId", runId));
            narrate("Tell me what you'd like to build \u2014 a website, an app, or a code conversion \u2014 and I'll get started.", sink);
            return;
        }

        Optional<ConversionRequest> conversion = conversionService.fromChatMessage(message);
        if (conversion.isPresent()) {
            sink.send(ev("type", "meta", "intent", "convert", "runId", runId));
            convert(conversion.get(), message, session, sink, started);
            return;
        }

        String base = resolveBaseStory(session);
        Intent intent = HomeChatService.classify(message, siteEditService.hasGeneratedSite(), !base.isBlank());
        sink.send(ev("type", "meta", "intent", intent.name().toLowerCase(java.util.Locale.ROOT), "runId", runId));
        ChatImageService.Result stored = turn.pictures().isEmpty() ? null : images.save(turn.pictures());
        Pictures pictures = stored == null ? Pictures.NONE : new Pictures(false, "", stored.saved(), stored.problems());
        switch (intent) {
            case CHAT -> talk(turn, pictures, sink);
            case EDIT -> {
                if (pictures.saved().isEmpty() && patch(message, false, base, session, sink, started)) {
                    return;
                }
                String instruction = pictures.saved().isEmpty() ? message
                        : message + " Use this image: " + String.join(" ", pictures.urls());
                edit(instruction, message, base, pictures, session, sink, started);
                patcher.replay(); // earlier edits survive a colour/image change that had to rebuild the pages
            }
            case REFINE -> {
                if (pictures.saved().isEmpty() && patch(message, true, base, session, sink, started)) {
                    return;
                }
                sink.send(ev("type", "status", "text",
                        "That needs a rebuild \u2014 I'll rebuild from your site's own brief and keep its look and your edits\u2026"));
                String brief = base.isBlank() ? message : HomeChatService.refinedBrief(base, List.of(message));
                pictures = lookAt(pictures, message, sink);
                brief = brief + pictureContext(pictures, message);
                request.setUserStory(brief);
                build(request, message, brief, true, pictures, session, sink, started);
            }
            default -> {
                sink.send(ev("type", "status", "text", "On it \u2014 I'll write the user stories first, then build the site."));
                pictures = lookAt(pictures, message, sink);
                String brief = message + pictureContext(pictures, message);
                request.setUserStory(brief);
                build(request, message, brief, false, pictures, session, sink, started);
            }
        }
    }

    // ------------------------------------------------------------------ pictures

    private List<LlmClient.ImageInput> inputs(Pictures p) {
        return p.saved().stream().map(s -> new LlmClient.ImageInput(s.mimeType(), s.base64())).toList();
    }

    /** Tries to have a vision model read the pictures; {@code looked} says whether that worked. */
    private Pictures lookAt(Pictures p, String message, Sink sink) {
        if (p.saved().isEmpty() || !llm.supportsVision()) {
            return p;
        }
        sink.send(ev("type", "status", "text", "Looking at your image" + (p.saved().size() > 1 ? "s" : "") + "\u2026"));
        try {
            String description = llm.describeImages(HomeChatService.VISION_SYSTEM, HomeChatService.visionPrompt(message),
                    inputs(p), Duration.ofSeconds(90), 450);
            if (description != null && description.strip().length() > 20) {
                return new Pictures(true, description, p.saved(), p.problems());
            }
        } catch (Exception e) {
            log.debug("Vision call failed, the images become site assets instead: {}", e.toString());
        }
        return p;
    }

    /** Text added to the brief: the vision model's description, or the stored pictures as assets. */
    private String pictureContext(Pictures p, String message) {
        if (p.saved().isEmpty()) {
            return "";
        }
        if (p.looked()) {
            return HomeChatService.visualReference(p.description());
        }
        return HomeChatService.attachedAssets(p.urls());
    }

    /** The picture-note paragraph for the end of the answer ("" when no pictures came with the message). */
    private static String pictureNote(Pictures p, boolean placed) {
        return p.any() ? HomeChatService.imageNote(p.looked(), placed, p.urls(), p.problems()) : "";
    }

    /** Plain conversation: the connected model answers with the whole conversation as context, streamed. */
    private void talk(Turn turn, Pictures pictures, Sink sink) {
        if (pictures.any()) {
            if (pictures.saved().isEmpty()) {
                narrate(pictureNote(pictures, false).strip(), sink);
                return;
            }
            if (llm.supportsVision()) {
                sink.send(ev("type", "status", "text", "Looking at your image\u2026"));
                try {
                    String answer = llm.describeImages(chat.chatSystemPrompt(turn.systemPrompt()), turn.message(),
                            inputs(pictures), ANSWER_TIMEOUT, MAX_ANSWER_TOKENS);
                    if (answer != null && answer.strip().length() > 1) {
                        narrate(answer.strip() + "\n\nSaved as site assets:"
                                + pictures.urls().stream().map(u -> "\n- [" + u + "](" + u + ")")
                                .reduce("", String::concat), sink);
                        return;
                    }
                } catch (Exception e) {
                    log.debug("Vision chat failed: {}", e.toString());
                }
            }
            narrate("I can't look at pictures with the model that's connected right now, but I've saved "
                    + (pictures.saved().size() == 1 ? "your image" : "your images") + " as site assets."
                    + " Tell me what to build and I'll use " + (pictures.saved().size() == 1 ? "it" : "them")
                    + " on the site."
                    + "\n" + pictures.urls().stream().map(u -> "\n- [" + u + "](" + u + ")")
                    .reduce("", String::concat), sink);
            return;
        }
        sink.send(ev("type", "status", "text", "Thinking\u2026"));
        List<LlmClient.ChatTurn> turns = new ArrayList<>(turn.earlier());
        turns.add(new LlmClient.ChatTurn("user", turn.message()));
        StringBuilder sent = new StringBuilder();
        if (llm.isAvailable()) {
            try {
                llm.chat(chat.chatSystemPrompt(turn.systemPrompt()), turns, ANSWER_TIMEOUT, MAX_ANSWER_TOKENS, piece -> {
                    sent.append(piece);
                    sink.sendOrStop(ev("type", "delta", "text", piece));
                });
            } catch (UncheckedIOException e) {
                throw e;
            } catch (Exception e) {
                log.debug("Home chat model call failed, answering from the built-in text: {}", e.toString());
            }
        }
        if (sent.toString().strip().length() < 2) {
            narrate(chat.offlineChatReply(turn.message()), sink);
        }
    }

    /**
     * Edits the generated pages in place ("make the hero shorter", "rename Plans to Pricing"). Returns false when the
     * request is not one the patcher can do, so the caller falls back to the theme editor or a rebuild.
     */
    private boolean patch(String message, boolean allowModel, String base, HttpSession session, Sink sink, long started) {
        if (!patcher.hasPages()) {
            return false;
        }
        sink.send(ev("type", "status", "text", "Looking at your current site\u2026"));
        SitePatcher.Outcome outcome = patcher.apply(message, allowModel);
        if (!outcome.handled()) {
            return false;
        }
        if (!outcome.changed()) {
            narrate(outcome.clarify(), sink); // a question back, nothing was changed
            return true;
        }
        sink.send(ev("type", "step", "state", "start", "label", HomeChatService.activeLabel("Site editor"), "index", 1, "total", 1));
        String preview = "/site/" + (outcome.page() == null ? "home" : outcome.page());
        BotStepLog step = new BotStepLog("Site editor \u2014 edit generated pages", true,
                "Edited " + String.join("; ", outcome.changes()), outcome.files(), System.currentTimeMillis() - started);
        sink.send(stepEvent(step, 1, 1));
        PipelineResponse response = new PipelineResponse();
        response.setSuccess(true);
        response.setPreviewUrl(preview);
        response.setCodeUrl("/code");
        response.setMessage("Applied your edit: " + String.join("; ", outcome.changes()));
        response.setSteps(new ArrayList<>(List.of(step)));
        response.setDurationMs(System.currentTimeMillis() - started);
        if (base != null && !base.isBlank()) {
            session.setAttribute("baseUserStory", base);
        }
        remember(response, message, session);
        narrate(HomeChatService.narratePatch(outcome.changes(), preview, patcher.brand(), outcome.lead()), sink);
        sink.send(ev("type", "result", "data", response));
        return true;
    }

    /** A colour / font / image change of the current site. */
    private void edit(String instruction, String message, String base, Pictures pictures, HttpSession session,
                      Sink sink, long started) {
        sink.send(ev("type", "status", "text", "Applying your change to the current site\u2026"));
        sink.send(ev("type", "step", "state", "start", "label", HomeChatService.activeLabel("Site editor"), "index", 1, "total", 1));
        PipelineResponse response = siteEditService.apply(instruction, base);
        for (BotStepLog step : response.getSteps()) {
            sink.send(stepEvent(step, 1, 1));
        }
        if (base != null && !base.isBlank()) {
            session.setAttribute("baseUserStory", base);
        }
        session.setAttribute("lastEditInstruction", message);
        response.setDurationMs(System.currentTimeMillis() - started);
        remember(response, message, session);
        String text = response.isSuccess() ? HomeChatService.narrateEdit(response) : HomeChatService.narrateFailure(response);
        narrate(text + pictureNote(pictures, response.isSuccess() && !pictures.saved().isEmpty()), sink);
        sink.send(ev("type", "result", "data", response));
    }

    /** Runs the pipeline and streams each bot's progress, then the written summary and the full result. */
    private void build(PipelineRequest request, String shownMessage, String briefForNext, boolean refined,
                       Pictures pictures, HttpSession session, Sink sink, long started) {
        PipelineListener live = new PipelineListener() {
            @Override
            public void stepStarted(String botName, int index, int total) {
                sink.send(ev("type", "step", "state", "start", "bot", botName,
                        "label", HomeChatService.activeLabel(botName), "index", index, "total", total));
            }

            @Override
            public void stepFinished(BotStepLog step, int index, int total) {
                sink.send(stepEvent(step, index, total));
            }
        };
        // a rebuild of the existing site keeps its look and the edits made on it; a new site starts clean
        SitePatcher.Look look = refined ? patcher.snapshotLook() : null;
        PipelineResponse response = orchestrator.run(request, live);
        boolean kept = false;
        if (response.isSuccess() && !response.isStopped()) {
            if (refined) {
                patcher.restoreLook(look);
                kept = patcher.replay() > 0 || look != null && !look.pages().isEmpty();
            } else {
                patcher.resetEdits();
            }
        }
        // pictures nobody could look at become the site's own images: hero + cards
        boolean placed = false;
        if (response.isSuccess() && !response.isStopped() && !pictures.looked() && !pictures.saved().isEmpty()) {
            int at = response.getSteps().size() + 1;
            sink.send(ev("type", "step", "state", "start", "label", "Placing your images on the site", "index", at, "total", at));
            PipelineResponse placing = siteEditService.apply(
                    "Use this image: " + String.join(" ", pictures.urls()) + " as the hero and card images", briefForNext);
            placed = placing.isSuccess();
            sink.send(ev("type", "step", "state", placed ? "done" : "failed", "label", "Placed your images on the site",
                    "durationMs", placing.getSteps().isEmpty() ? null : placing.getSteps().get(0).getDurationMs(),
                    "index", at, "total", at));
        }
        response.setDurationMs(System.currentTimeMillis() - started);
        if (!response.isStopped()) {
            session.setAttribute("baseUserStory", briefForNext);
            remember(response, shownMessage, session);
        }
        String text;
        if (response.isStopped()) {
            text = HomeChatService.narrateStopped(response);
        } else if (response.isSuccess()) {
            text = HomeChatService.narrateBuild(response, chat.siteBrand(), refined, chat.siteHasAgent());
            if (kept) {
                text = text.replace("Here's what I did:", "I kept your colours, fonts, pictures and earlier edits.\n\nHere's what I did:");
            }
        } else {
            text = HomeChatService.narrateFailure(response);
        }
        if (!response.isStopped() && response.isSuccess()) {
            text += pictureNote(pictures, placed);
        }
        narrate(text, sink);
        sink.send(ev("type", "result", "data", response));
    }

    private void convert(ConversionRequest request, String message, HttpSession session, Sink sink, long started) {
        sink.send(ev("type", "status", "text", "Converting your code\u2026"));
        sink.send(ev("type", "step", "state", "start", "label", "Converting your code", "index", 1, "total", 1));
        ConversionResponse result = conversionService.convert(request);
        PipelineResponse response = new PipelineResponse();
        response.setSuccess(result.success());
        response.setMessage(result.message());
        response.setCodeUrl("/convert");
        response.setConversion(result);
        List<String> files = result.outputFile() == null ? List.of() : List.of(result.outputFile());
        BotStepLog step = new BotStepLog("Code Converter", result.success(), result.message(), files,
                System.currentTimeMillis() - started);
        response.setSteps(new ArrayList<>(List.of(step)));
        response.setDurationMs(System.currentTimeMillis() - started);
        response.setHistoryId(historyService.save(message, response).getId());
        sink.send(ev("type", "step", "state", result.success() ? "done" : "failed", "label", "Converted your code",
                "durationMs", step.getDurationMs(), "index", 1, "total", 1));
        narrate(HomeChatService.narrateConversion(result), sink);
        sink.send(ev("type", "result", "data", response));
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> stepEvent(BotStepLog step, int index, int total) {
        return ev("type", "step", "state", step.isSuccess() ? "done" : "failed", "bot", step.getBotName(),
                "label", HomeChatService.doneLabel(step.getBotName()), "summary", step.getSummary(),
                "durationMs", step.getDurationMs(), "index", index, "total", total);
    }

    /** Streams a written answer piece by piece, like typing. */
    private void narrate(String text, Sink sink) {
        List<String> pieces = HomeChatService.pieces(text);
        int delay = pieces.isEmpty() ? 0 : Math.min(pieceDelayMs, 1500 / pieces.size());
        for (String piece : pieces) {
            sink.send(ev("type", "delta", "text", piece));
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Saves a finished run to the history and remembers it for the next follow-up. */
    private void remember(PipelineResponse response, String message, HttpSession session) {
        ChatHistoryEntry saved = historyService.save(message, response);
        response.setHistoryId(saved.getId());
        session.setAttribute("lastPipelineResponse", response);
        session.setAttribute("lastUserStory", message);
        session.setAttribute("lastHistoryId", saved.getId());
    }

    /** The brief of the site built so far (what a follow-up is added to). */
    private String resolveBaseStory(HttpSession session) {
        Object base = session.getAttribute("baseUserStory");
        if (base instanceof String s && !s.isBlank()) {
            return s;
        }
        // the site on disk is the truth: its own stored brief beats whatever an older session remembers
        String stored = patcher.storedBrief();
        if (!stored.isBlank()) {
            return stored;
        }
        Object last = session.getAttribute("lastUserStory");
        if (last instanceof String s && !s.isBlank() && !SiteEditService.isEditMessage(s)) {
            return s;
        }
        for (ChatHistoryEntry entry : historyService.list()) {
            if (entry.getConversion() == null && entry.getUserStory() != null
                    && !SiteEditService.isEditMessage(entry.getUserStory())) {
                return entry.getUserStory();
            }
        }
        return "";
    }
}
