package com.agentic.chatbot.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The preflight against a fake GitHub API: each situation gets its own, specific message. */
class GitHubPreflightTest {

    private static final String URL = "https://github.com/manasa123104/T1";
    private static final String TOKEN = "ghp_0123456789abcdefghijABCDEFGHIJ012345";
    private static final String FINE = "github_pat_0123456789abcdefghijABCDEFGHIJ012345";

    private HttpServer server;
    private GitHubPreflight preflight;
    /** path + (auth|anon) -> status; body; scopes header */
    private final Map<String, Object[]> routes = new HashMap<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            String key = ex.getRequestURI().getPath() + (ex.getRequestHeaders().containsKey("Authorization") ? "|auth" : "|anon");
            Object[] r = routes.getOrDefault(key, new Object[]{404, "{\"message\":\"Not Found\"}", null});
            if (r[2] != null) {
                ex.getResponseHeaders().add("X-OAuth-Scopes", (String) r[2]);
            }
            byte[] body = ((String) r[1]).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders((Integer) r[0], body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        preflight = new GitHubPreflight("http://127.0.0.1:" + server.getAddress().getPort());
        routes.put("/user|auth", new Object[]{200, "{\"login\":\"manasa123104\"}", null});
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void repo(String key, int status, String body, String scopes) {
        routes.put("/repos/manasa123104/T1|" + key, new Object[]{status, body, scopes});
    }

    @Test
    void classicTokenWithPushPermissionIsWritable() {
        repo("auth", 200, "{\"private\":false,\"permissions\":{\"admin\":false,\"push\":true,\"pull\":true}}", "repo, workflow");
        GitHubPreflight.Result r = preflight.check(URL, TOKEN);
        assertEquals(GitHubPreflight.Code.WRITABLE, r.code());
        assertTrue(r.ok() && r.canPush());
        assertTrue(r.message().contains("manasa123104"), r.message());
    }

    @Test
    void readOnlyTokenIsExplained() {
        repo("auth", 200, "{\"private\":false,\"permissions\":{\"admin\":false,\"push\":false,\"pull\":true}}", null);
        GitHubPreflight.Result r = preflight.check(URL, FINE);
        assertEquals(GitHubPreflight.Code.READ_ONLY, r.code());
        assertFalse(r.ok());
        assertTrue(r.message().contains("can read") && r.message().contains("not write")
                && r.message().contains("Contents: Read and write"), r.message());
    }

    @Test
    void classicTokenWithoutRepoScopeIsExplained() {
        repo("auth", 200, "{\"private\":true,\"permissions\":{\"push\":true}}", "gist, read:org");
        GitHubPreflight.Result r = preflight.check(URL, TOKEN);
        assertEquals(GitHubPreflight.Code.MISSING_SCOPE, r.code());
        assertTrue(r.message().contains("repo") && r.message().contains("gist, read:org"), r.message());
    }

    @Test
    void fineGrainedTokenThatCanSeeTheRepoIsUnverifiedNotClaimedWritable() {
        repo("auth", 200, "{\"private\":false,\"permissions\":{\"push\":true}}", null);
        GitHubPreflight.Result r = preflight.check(URL, FINE);
        assertEquals(GitHubPreflight.Code.UNVERIFIED, r.code());
        assertTrue(r.ok());
        assertTrue(r.message().contains("Contents"), r.message());
    }

    @Test
    void publicRepoInvisibleToTheTokenIsNoAccess() {
        repo("auth", 404, "{\"message\":\"Not Found\"}", null);
        repo("anon", 200, "{\"private\":false}", null);
        GitHubPreflight.Result r = preflight.check(URL, FINE);
        assertEquals(GitHubPreflight.Code.NO_ACCESS, r.code());
        assertTrue(r.message().startsWith("Token has no access to this repository"), r.message());
    }

    @Test
    void missingRepoIsReportedAsNotFound() {
        repo("auth", 404, "{\"message\":\"Not Found\"}", null);
        repo("anon", 404, "{\"message\":\"Not Found\"}", null);
        GitHubPreflight.Result r = preflight.check(URL, TOKEN);
        assertEquals(GitHubPreflight.Code.REPO_NOT_FOUND, r.code());
        assertTrue(r.message().startsWith("Repository not found - create " + URL + " first"), r.message());
    }

    @Test
    void rejectedTokenAndForbiddenAreDistinct() {
        repo("auth", 401, "{\"message\":\"Bad credentials\"}", null);
        assertEquals(GitHubPreflight.Code.BAD_TOKEN, preflight.check(URL, TOKEN).code());
        repo("auth", 403, "{\"message\":\"API rate limit exceeded\"}", null);
        GitHubPreflight.Result r = preflight.check(URL, TOKEN);
        assertEquals(GitHubPreflight.Code.FORBIDDEN, r.code());
        assertTrue(r.message().contains("rate limit"), r.message());
    }

    @Test
    void tokenIsNeverEchoedAndOtherHostsAreSkipped() {
        repo("auth", 401, "{\"message\":\"Bad credentials\"}", null);
        assertFalse(preflight.check(URL, TOKEN).message().contains(TOKEN));
        GitHubPreflight.Result other = preflight.check("https://gitlab.com/a/b", TOKEN);
        assertEquals(GitHubPreflight.Code.SKIPPED, other.code());
        assertTrue(other.ok());
        assertEquals(GitHubPreflight.Code.SKIPPED, preflight.check(URL, "").code());
    }

    @Test
    void unreachableApiDoesNotBlockThePush() {
        GitHubPreflight down = new GitHubPreflight("http://127.0.0.1:1");
        GitHubPreflight.Result r = down.check(URL, TOKEN);
        assertEquals(GitHubPreflight.Code.SKIPPED, r.code());
        assertTrue(r.ok());
    }
}
