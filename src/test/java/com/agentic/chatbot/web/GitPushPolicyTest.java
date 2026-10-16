package com.agentic.chatbot.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitPushPolicyTest {

    private static final String TOKEN = "ghp_abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String REPO = "https://github.com/owner/repo.git";

    @AfterEach
    void reset() {
        RequestOrigin.setPublicMode(false);
    }

    private static MockHttpServletRequest from(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        return request;
    }

    @Test
    void hostMayPushWithItsOwnLoginOrAPastedToken() {
        assertNull(GitPushPolicy.authorize(from("127.0.0.1"), REPO, ""));
        assertEquals(TOKEN, GitPushPolicy.authorize(from("127.0.0.1"), REPO, " " + TOKEN + " "));
    }

    @Test
    void remoteVisitorsMustPasteTheirOwnTokenAndUseHttps() {
        assertThrows(RequestOrigin.HostOnlyException.class, () -> GitPushPolicy.authorize(from("203.0.113.9"), REPO, ""));
        assertThrows(RequestOrigin.HostOnlyException.class,
                () -> GitPushPolicy.authorize(from("203.0.113.9"), "git@github.com:owner/repo.git", TOKEN));
        assertEquals(TOKEN, GitPushPolicy.authorize(from("203.0.113.9"), REPO, TOKEN));
    }

    @Test
    void publicModeTreatsEveryoneAsARemoteVisitor() {
        RequestOrigin.setPublicMode(true);
        assertThrows(RequestOrigin.HostOnlyException.class, () -> GitPushPolicy.authorize(from("127.0.0.1"), REPO, ""));
        assertEquals(TOKEN, GitPushPolicy.authorize(from("127.0.0.1"), REPO, TOKEN));
    }

    @Test
    void rejectsMalformedTokensWithoutEchoingThem() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GitPushPolicy.validToken("not a token; rm -rf /"));
        assertEquals(false, e.getMessage().contains("rm -rf"));
    }
}