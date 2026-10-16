package com.agentic.chatbot.web;

import com.agentic.chatbot.agent.PipelineListener;
import com.agentic.chatbot.agent.PipelineOrchestrator;
import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.BotStepLog;
import com.agentic.chatbot.model.PipelineRequest;
import com.agentic.chatbot.model.PipelineResponse;
import com.agentic.chatbot.service.ChatHistoryService;
import com.agentic.chatbot.service.ChatImageService;
import com.agentic.chatbot.service.CodeConversionService;
import com.agentic.chatbot.service.HomeChatService;
import com.agentic.chatbot.service.HomeChatService.Intent;
import com.agentic.chatbot.service.SiteEditService;
import com.agentic.chatbot.service.SitePatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpSession;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The home chat behaves like ChatGPT: follow-ups use the conversation, questions are answered, builds stream steps. */
class HomeChatTest {

    // ------------------------------------------------------------------ what a message means

    @Test
    void newPromptsBuildFollowUpsRefineAndQuestionsChat() {
        // no site yet: everything that is not a question builds
        assertEquals(Intent.BUILD, HomeChatService.classify("Build a bakery website called Crumb Hearth", false, false));
        assertEquals(Intent.BUILD, HomeChatService.classify("add a chatbot agent", false, false));
        // with a site: follow-ups continue the same brief
        assertEquals(Intent.REFINE, HomeChatService.classify("add a chatbot agent", true, true));
        assertEquals(Intent.REFINE, HomeChatService.classify("make the hero shorter", true, true));
        assertEquals(Intent.REFINE, HomeChatService.classify("Can you add a pricing page?", true, true));
        // colours and fonts patch the current site
        assertEquals(Intent.EDIT, HomeChatService.classify("Change the website color to blue and use Poppins font", true, true));
        // a brand-new site request is never a follow-up
        assertEquals(Intent.BUILD, HomeChatService.classify(
                "Build a travel website called SkyTrail with destinations and contact pages", true, true));
        // plain conversation
        assertEquals(Intent.CHAT, HomeChatService.classify("hi", true, true));
        assertEquals(Intent.CHAT, HomeChatService.classify("What did you build?", true, true));
        assertEquals(Intent.CHAT, HomeChatService.classify("how do I run the code?", false, false));
        assertEquals(Intent.CHAT, HomeChatService.classify("thanks!", true, true));
        // an uploaded file of stories is always a build
        assertEquals(Intent.BUILD, HomeChatService.classify(
                "Build a website from the user stories and requirements below.\n\n=== User stories and requirements "
                        + "(from the uploaded file) ===\nHow do I log in?", true, true));
    }

    @Test
    void followUpsAreAddedToTheEarlierBriefWithoutDuplicatingTheHeader() {
        String once = HomeChatService.refinedBrief("Bakery website called Crumb Hearth.", List.of("add a chatbot agent"));
        String twice = HomeChatService.refinedBrief(once, List.of("make the hero shorter"));
        assertTrue(twice.startsWith("Bakery website called Crumb Hearth."));
        assertTrue(twice.contains("- add a chatbot agent") && twice.contains("- make the hero shorter"), twice);
        assertEquals(1, twice.split("Follow-up changes requested", -1).length - 1, twice);
    }

    @Test
    void thePipelineLogIsRetoldInPlainWords() {
        PipelineResponse r = new PipelineResponse();
        r.setSuccess(true);
        r.setPreviewUrl("/site/home");
        r.setSteps(new ArrayList<>(List.of(
                new BotStepLog("Bot 1 - Business Analyst", true, "internal", List.of("USER_STORY.md"), 10),
                new BotStepLog("Bot 2 - Java Developer", true, "internal", List.of("a.html", "b.html", "c.java"), 10))));
        String text = HomeChatService.narrateBuild(r, "Crumb Hearth", false, true);
        assertTrue(text.contains("**Crumb Hearth** website is ready"), text);
        assertTrue(text.contains("Wrote the user stories") && text.contains("(3 files)"), text);
        assertTrue(text.contains("chatbot agent"), text);
        assertTrue(text.contains("[Open the website](/site/home)") && text.contains("(/code)"), text);
        assertFalse(text.contains("Bot 1"), "no internal bot names: " + text);
    }

    // ------------------------------------------------------------------ the stream

    private static class Model implements LlmClient {
        boolean available = true;
        String system;
        List<ChatTurn> turns = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            throw new IllegalStateException("chat() should be used");
        }

        @Override
        public String chat(String systemPrompt, List<ChatTurn> t, Duration timeout, int maxTokens, Consumer<String> onChunk) {
            system = systemPrompt;
            turns = t;
            onChunk.accept("The site is at ");
            onChunk.accept("/site/home.");
            return "The site is at /site/home.";
        }
    }

    private static List<String> lines(HomeChatApiController c, Map<String, Object> body, MockHttpSession session) throws Exception {
        var out = new ByteArrayOutputStream();
        c.stream(body, session).getBody().writeTo(out);
        return List.of(out.toString(StandardCharsets.UTF_8).split("\n"));
    }

    private static String joinedText(List<String> events) {
        StringBuilder sb = new StringBuilder();
        for (String e : events) {
            if (e.contains("\"type\":\"delta\"")) {
                sb.append(e.replaceAll(".*\"text\":\"", "").replaceAll("\"}$", "").replace("\\n", "\n").replace("\\\"", "\""));
            }
        }
        return sb.toString();
    }

    @Test
    void aBuildStreamsEachStepThenAFriendlyAnswerThenTheResultAndFollowUpsKeepTheBrief(@TempDir Path dir) throws Exception {
        var orchestrator = mock(PipelineOrchestrator.class);
        var site = mock(SiteEditService.class);
        var conversion = mock(CodeConversionService.class);
        var history = new ChatHistoryService(dir.resolve("history.json").toString());
        var model = new Model();
        var controller = new HomeChatApiController(orchestrator, history, site, conversion,
                new HomeChatService(dir.toString()), new ChatImageService(dir.toString()), new SitePatcher(dir.toString(), model), model, 0);
        var session = new MockHttpSession();

        when(orchestrator.run(any(PipelineRequest.class), any(PipelineListener.class))).thenAnswer(inv -> {
            PipelineListener listener = inv.getArgument(1);
            var ba = new BotStepLog("Bot 1 - Business Analyst", true, "stories written", List.of("USER_STORY.md"), 12);
            var dev = new BotStepLog("Bot 2 - Java Developer", true, "code written", List.of("home.html", "App.java"), 30);
            listener.stepStarted(ba.getBotName(), 1, 2);
            listener.stepFinished(ba, 1, 2);
            listener.stepStarted(dev.getBotName(), 2, 2);
            listener.stepFinished(dev, 2, 2);
            PipelineResponse r = new PipelineResponse();
            r.setSuccess(true);
            r.setPreviewUrl("/site/home");
            r.setSteps(new ArrayList<>(List.of(ba, dev)));
            return r;
        });

        List<String> events = lines(controller, Map.of("message", "Build a bakery website called Crumb Hearth",
                "runId", "run-abc12345"), session);

        assertTrue(events.get(0).contains("\"type\":\"meta\"") && events.get(0).contains("\"intent\":\"build\""), events.toString());
        int firstStep = indexOf(events, "\"type\":\"step\"");
        int lastStep = lastIndexOf(events, "\"type\":\"step\"");
        int firstDelta = indexOf(events, "\"type\":\"delta\"");
        int result = indexOf(events, "\"type\":\"result\"");
        assertTrue(firstStep > 0 && lastStep > firstStep, events.toString());
        assertEquals(4, events.stream().filter(l -> l.contains("\"type\":\"step\"")).count(), "start + done for two bots");
        assertTrue(events.get(firstStep).contains("\"state\":\"start\"") && events.get(firstStep).contains("Writing the user stories"));
        assertTrue(firstDelta > lastStep && result > firstDelta, "steps, then the written answer, then the result: " + events);
        assertTrue(joinedText(events).contains("Open the website"), joinedText(events));
        assertTrue(events.get(events.size() - 1).contains("\"type\":\"done\""));
        assertEquals("Build a bakery website called Crumb Hearth", session.getAttribute("baseUserStory"));
        assertEquals(1, history.list().size());

        // the next message is a follow-up: the earlier brief is rebuilt with the change added
        when(site.hasGeneratedSite()).thenReturn(true);
        List<String> second = lines(controller, Map.of("message", "add a chatbot agent"), session);
        assertTrue(second.get(0).contains("\"intent\":\"refine\""), second.toString());
        var captor = ArgumentCaptor.forClass(PipelineRequest.class);
        verify(orchestrator, org.mockito.Mockito.times(2)).run(captor.capture(), any(PipelineListener.class));
        String brief = captor.getAllValues().get(1).getUserStory();
        assertTrue(brief.startsWith("Build a bakery website called Crumb Hearth"), brief);
        assertTrue(brief.contains("- add a chatbot agent"), brief);
    }

    @Test
    void aQuestionIsAnsweredByTheModelWithTheConversationSoFarAndNeverRunsThePipeline(@TempDir Path dir) throws Exception {
        var orchestrator = mock(PipelineOrchestrator.class);
        var model = new Model();
        var controller = new HomeChatApiController(orchestrator, new ChatHistoryService(dir.resolve("h.json").toString()),
                mock(SiteEditService.class), mock(CodeConversionService.class), new HomeChatService(dir.toString()), new ChatImageService(dir.toString()), new SitePatcher(dir.toString(), model), model, 0);

        List<String> events = lines(controller, Map.of(
                "message", "Where is it?",
                "history", List.of(
                        Map.of("role", "user", "content", "Build a bakery website"),
                        Map.of("role", "assistant", "content", "All done - your website is ready."))), new MockHttpSession());

        assertEquals(2, events.stream().filter(l -> l.contains("\"type\":\"delta\"")).count(), events.toString());
        assertEquals(3, model.turns.size());
        assertEquals("user", model.turns.get(0).role());
        assertEquals("assistant", model.turns.get(1).role());
        assertEquals("Where is it?", model.turns.get(2).content());
        assertTrue(model.system.contains("BOT"));
        verify(orchestrator, never()).run(any(PipelineRequest.class), any(PipelineListener.class));
    }

    @Test
    void withoutAModelBotStillAnswersGreetingsInPlainWords(@TempDir Path dir) throws Exception {
        var model = new Model();
        model.available = false;
        var controller = new HomeChatApiController(mock(PipelineOrchestrator.class),
                new ChatHistoryService(dir.resolve("h.json").toString()), mock(SiteEditService.class),
                mock(CodeConversionService.class), new HomeChatService(dir.toString()), new ChatImageService(dir.toString()), new SitePatcher(dir.toString(), model), model, 0);

        List<String> events = lines(controller, Map.of("message", "hello"), new MockHttpSession());

        assertTrue(joinedText(events).contains("BOT"), joinedText(events));
        assertTrue(events.get(events.size() - 1).contains("\"type\":\"done\""));
    }

    // ------------------------------------------------------------------ pasted images

    private static final String PNG_1X1 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static Map<String, Object> png(String name) {
        return Map.of("name", name, "type", "image/png", "data", "data:image/png;base64," + PNG_1X1);
    }

    @Test
    void imagesAreCheckedAndStoredUnderUploads(@TempDir Path dir) throws Exception {
        var service = new ChatImageService(dir.toString());
        var bad = Map.<String, Object>of("name", "evil.png", "type", "image/png",
                "data", java.util.Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes()));
        var result = service.save(List.of(png("shot.png"), bad, png("b.png"), png("c.png"), png("d.png"), png("e.png")));

        assertEquals(4, result.saved().size());
        assertTrue(result.saved().get(0).url().startsWith("/uploads/chat/chat-") && result.saved().get(0).url().endsWith(".png"));
        assertEquals("image/png", result.saved().get(0).mimeType());
        assertTrue(java.nio.file.Files.exists(dir.resolve("uploads/chat").resolve(
                result.saved().get(0).url().substring("/uploads/chat/".length()))));
        assertEquals(2, result.problems().size(), result.problems().toString()); // the fake png, the fifth image
        assertTrue(result.problems().get(0).contains("evil.png"), result.problems().toString());
    }

    /** A model that can look at pictures and records what it was sent. */
    private static final class Seeing extends Model {
        List<ImageInput> seen = List.of();
        String prompt;

        @Override
        public boolean supportsVision() {
            return true;
        }

        @Override
        public String describeImages(String system, String p, List<ImageInput> images, Duration timeout, int maxTokens) {
            seen = images;
            prompt = p;
            return "A warm cream layout with terracotta headings, rounded cards and a large bread photo in the hero.";
        }
    }

    private static PipelineResponse okRun() {
        PipelineResponse r = new PipelineResponse();
        r.setSuccess(true);
        r.setPreviewUrl("/site/home");
        r.setSteps(new ArrayList<>(List.of(new BotStepLog("Bot 2 - Java Developer", true, "x", List.of("a.html"), 5))));
        return r;
    }

    @Test
    void aVisionModelDescribesThePastedImageAndTheBriefFollowsIt(@TempDir Path dir) throws Exception {
        var orchestrator = mock(PipelineOrchestrator.class);
        var site = mock(SiteEditService.class);
        var model = new Seeing();
        var controller = new HomeChatApiController(orchestrator, new ChatHistoryService(dir.resolve("h.json").toString()),
                site, mock(CodeConversionService.class), new HomeChatService(dir.toString()),
                new ChatImageService(dir.toString()), new SitePatcher(dir.toString(), model), model, 0);
        when(orchestrator.run(any(PipelineRequest.class), any(PipelineListener.class))).thenReturn(okRun());

        List<String> events = lines(controller, Map.of(
                "message", "Build a bakery website called Crumb Hearth that matches this look",
                "images", List.of(png("look.png"))), new MockHttpSession());

        assertEquals(1, model.seen.size());
        assertEquals("image/png", model.seen.get(0).mimeType());
        assertEquals(PNG_1X1, model.seen.get(0).base64());
        var captor = ArgumentCaptor.forClass(PipelineRequest.class);
        verify(orchestrator).run(captor.capture(), any(PipelineListener.class));
        String brief = captor.getValue().getUserStory();
        assertTrue(brief.startsWith("Build a bakery website called Crumb Hearth"), brief);
        assertTrue(brief.contains("terracotta headings"), "the image description is part of the brief: " + brief);
        verify(site, never()).apply(any(), any()); // looked at, so no asset placement needed
        String text = joinedText(events);
        assertTrue(text.contains("I looked at it"), text);
        assertTrue(text.contains("/uploads/chat/"), text);
    }

    @Test
    void withoutVisionTheImagesBecomeSiteAssetsAndTheUserIsTold(@TempDir Path dir) throws Exception {
        var orchestrator = mock(PipelineOrchestrator.class);
        var site = mock(SiteEditService.class);
        var placed = new PipelineResponse();
        placed.setSuccess(true);
        when(site.apply(any(), any())).thenReturn(placed);
        var controller = new HomeChatApiController(orchestrator, new ChatHistoryService(dir.resolve("h.json").toString()),
                site, mock(CodeConversionService.class), new HomeChatService(dir.toString()),
                new ChatImageService(dir.toString()), new SitePatcher(dir.toString(), null), new Model(), 0);
        when(orchestrator.run(any(PipelineRequest.class), any(PipelineListener.class))).thenReturn(okRun());

        List<String> events = lines(controller, Map.of(
                "message", "Build a bakery website called Crumb Hearth",
                "images", List.of(png("hero.png"))), new MockHttpSession());

        var captor = ArgumentCaptor.forClass(String.class);
        verify(site).apply(captor.capture(), any());
        assertTrue(captor.getValue().contains("Use this image: /uploads/chat/chat-"), captor.getValue());
        String text = joinedText(events);
        assertTrue(text.contains("can't look at pictures") && text.contains("hero and card images"), text);
        assertTrue(events.stream().anyMatch(l -> l.contains("Placing your images on the site")), events.toString());
    }

    // ------------------------------------------------------------------ editing the generated site

    @Test
    void followUpsEditTheGeneratedSiteInPlaceAndStack(@TempDir Path dir) throws Exception {
        var templates = java.nio.file.Files.createDirectories(dir.resolve("src/main/resources/templates"));
        java.nio.file.Files.writeString(templates.resolve("home.html"), com.agentic.chatbot.service.SitePatcherTest.HOME);
        java.nio.file.Files.writeString(dir.resolve("SITE_META.json"), "{\"brand\":\"Crumb Hearth\",\"language\":\"java\"}");
        java.nio.file.Files.writeString(dir.resolve("USER_STORY.md"), "# Big site generated\n\nBuild a bakery website called Crumb Hearth\n");
        var orchestrator = mock(PipelineOrchestrator.class);
        var site = mock(SiteEditService.class);
        when(site.hasGeneratedSite()).thenReturn(true);
        var controller = new HomeChatApiController(orchestrator, new ChatHistoryService(dir.resolve("h.json").toString()),
                site, mock(CodeConversionService.class), new HomeChatService(dir.toString()),
                new ChatImageService(dir.toString()), new SitePatcher(dir.toString(), null), new Model(), 0);
        var session = new MockHttpSession();

        // a fresh session (the server restarted): the brief comes from the site on disk
        List<String> first = lines(controller, Map.of("message", "make the hero shorter"), session);
        assertTrue(first.get(0).contains("\"intent\":\"refine\""), first.toString());
        String text = joinedText(first);
        assertTrue(text.contains("updated the hero") && text.contains("Crumb Hearth"), text);
        assertTrue(text.contains("(/site/home)") && text.contains("(/download/site.zip)"), text);
        assertTrue(first.stream().anyMatch(l -> l.contains("\"type\":\"result\"")), first.toString());
        String afterFirst = java.nio.file.Files.readString(templates.resolve("home.html"));
        assertTrue(afterFirst.contains("min-height:170px"));

        // the next edit builds on the latest state of the site
        List<String> second = lines(controller, Map.of("message", "rename Plans to Pricing"), session);
        assertTrue(joinedText(second).contains("renamed \u201cPlans\u201d to \u201cPricing\u201d"), joinedText(second));
        String afterSecond = java.nio.file.Files.readString(templates.resolve("home.html"));
        assertTrue(afterSecond.contains("min-height:170px") && afterSecond.contains(">Pricing<"), "both edits are on the site");

        // a question back instead of a blind rebuild
        List<String> third = lines(controller, Map.of("message", "rename the menu"), session);
        assertTrue(joinedText(third).contains("Which menu item"), joinedText(third));

        verify(orchestrator, never()).run(any(PipelineRequest.class), any(PipelineListener.class));
    }

    private static int indexOf(List<String> events, String needle) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(List<String> events, String needle) {
        for (int i = events.size() - 1; i >= 0; i--) {
            if (events.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }
}
