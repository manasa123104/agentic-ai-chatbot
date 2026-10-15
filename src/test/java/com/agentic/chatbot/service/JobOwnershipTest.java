package com.agentic.chatbot.service;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.ConversionJob;
import com.agentic.chatbot.web.RepositoryConversionController;
import com.agentic.chatbot.web.RequestOrigin;
import com.agentic.chatbot.web.VisitorCookie;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobOwnershipTest {

    @TempDir
    Path temp;

    private RepositoryConversionService service;
    private RepositoryConversionController controller;

    @BeforeEach
    void setUp() {
        LlmClient noModel = new LlmClient() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("no model");
            }
        };
        CodeConversionService converter = new CodeConversionService(new CodeSampleLibrary(), noModel,
                temp.resolve("generated").toString());
        service = new RepositoryConversionService(converter, new GitService("", "BOT", "bot@example.com"),
                temp.resolve("jobs").toString(), 300, 2048, false);
        controller = new RepositoryConversionController(service);
    }

    @AfterEach
    void waitForJobs() throws InterruptedException {
        for (int i = 0; i < 100 && service.list().stream().anyMatch(j ->
                j.getStatus() == ConversionJob.Status.QUEUED || j.getStatus() == ConversionJob.Status.RUNNING); i++) {
            Thread.sleep(50);
        }
    }

    /** A request through the tunnel (loopback address plus a CF header), optionally carrying a visitor cookie. */
    private static MockHttpServletRequest remote(String cookie) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("CF-Connecting-IP", "203.0.113.9");
        if (cookie != null) {
            request.setCookies(new Cookie(VisitorCookie.NAME, cookie));
        }
        return request;
    }

    private static MockHttpServletRequest host() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static String cookieValue(MockHttpServletResponse response) {
        String header = response.getHeader("Set-Cookie");
        assertTrue(header != null && header.startsWith(VisitorCookie.NAME + "="), "cookie was not set: " + header);
        return header.substring(VisitorCookie.NAME.length() + 1, header.indexOf(';'));
    }

    private ConversionJob startAs(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        return controller.start("text", "python", "auto", null, false, false, null, null, null, null,
                "Hello.java", "public class Hello { }", null, request, response);
    }

    @Test
    void visitorCookieIsRandomHttpOnlyAndSameSiteLax() {
        MockHttpServletResponse first = new MockHttpServletResponse();
        VisitorCookie.owner(remote(null), first);
        String header = first.getHeader("Set-Cookie");
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));

        MockHttpServletResponse second = new MockHttpServletResponse();
        VisitorCookie.owner(remote(null), second);
        assertNotEquals(cookieValue(first), cookieValue(second));

        // A browser that already has a valid cookie is not given a new one.
        MockHttpServletResponse again = new MockHttpServletResponse();
        VisitorCookie.owner(remote(cookieValue(first)), again);
        assertEquals(null, again.getHeader("Set-Cookie"));

        // A garbage cookie is replaced.
        MockHttpServletResponse bad = new MockHttpServletResponse();
        VisitorCookie.owner(remote("../../etc/passwd"), bad);
        assertTrue(bad.getHeader("Set-Cookie").startsWith(VisitorCookie.NAME + "="));
    }

    @Test
    void visitorsOnlySeeTheirOwnJobs() throws Exception {
        MockHttpServletResponse aResponse = new MockHttpServletResponse();
        ConversionJob job = startAs(remote(null), aResponse);
        String a = cookieValue(aResponse);
        String b = "11111111-2222-3333-4444-555555555555";
        String id = job.getId();

        assertEquals(1, controller.list(remote(a), new MockHttpServletResponse()).size());
        assertEquals(0, controller.list(remote(b), new MockHttpServletResponse()).size());
        assertEquals(0, controller.list(remote(null), new MockHttpServletResponse()).size());
        // The API never exposes the raw cookie value.
        assertFalse(job.getOwner().contains(a));

        assertEquals(id, controller.get(id, remote(a), new MockHttpServletResponse()).getId());
        assertFalse(controller.files(id, "output", remote(a), new MockHttpServletResponse()) == null);

        MockHttpServletRequest other = remote(b);
        MockHttpServletResponse r = new MockHttpServletResponse();
        assertThrows(RepositoryConversionController.JobNotFoundException.class, () -> controller.get(id, other, r));
        assertThrows(RepositoryConversionController.JobNotFoundException.class,
                () -> controller.files(id, "output", other, r));
        assertThrows(RepositoryConversionController.JobNotFoundException.class,
                () -> controller.read(id, "Hello.java", "source", other, r));
        assertThrows(RepositoryConversionController.JobNotFoundException.class,
                () -> controller.save(id, "x.py", java.util.Map.of("content", "x"), other, r));
        assertThrows(RepositoryConversionController.JobNotFoundException.class,
                () -> controller.download(id, "Hello.java", "source", other, r));
        assertThrows(RepositoryConversionController.JobNotFoundException.class, () -> controller.zip(id, other, r));
        assertThrows(RepositoryConversionController.JobNotFoundException.class,
                () -> controller.push(id, new RepositoryConversionController.PushRequest(
                        "https://github.com/a/b.git", "x", "", "", ""), other, r));
        assertEquals(404, controller.notFound(new RepositoryConversionController.JobNotFoundException())
                .getStatusCode().value());
    }

    @Test
    void ownerCannotPushThroughTheTunnelEither() throws Exception {
        MockHttpServletResponse aResponse = new MockHttpServletResponse();
        ConversionJob job = startAs(remote(null), aResponse);
        MockHttpServletRequest owner = remote(cookieValue(aResponse));
        assertThrows(RequestOrigin.HostOnlyException.class, () -> controller.push(job.getId(),
                new RepositoryConversionController.PushRequest("https://github.com/a/b.git", "x", "", "", ""),
                owner, new MockHttpServletResponse()));
    }

    @Test
    void hostSeesEverythingIncludingJobsWithoutOwner() throws Exception {
        // Legacy job: created without an owner (as before owners existed).
        ConversionJob legacy = service.startFromText("Old.java", "public class Old { }",
                new RepositoryConversionService.Options("auto", "python", null, false, false));
        assertEquals("", legacy.getOwner());
        ConversionJob visitorJob = startAs(remote(null), new MockHttpServletResponse());

        List<ConversionJob> forHost = controller.list(host(), new MockHttpServletResponse());
        assertEquals(2, forHost.size());
        assertEquals(visitorJob.getId(), controller.get(visitorJob.getId(), host(), new MockHttpServletResponse()).getId());

        // A tunnelled request from loopback is NOT the host, and a visitor with any cookie never sees legacy jobs.
        assertEquals(0, controller.list(remote(null), new MockHttpServletResponse()).size());
        MockHttpServletRequest visitor = remote("11111111-2222-3333-4444-555555555555");
        assertThrows(RepositoryConversionController.JobNotFoundException.class,
                () -> controller.get(legacy.getId(), visitor, new MockHttpServletResponse()));
    }
}
