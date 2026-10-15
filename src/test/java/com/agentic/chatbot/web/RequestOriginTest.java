package com.agentic.chatbot.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestOriginTest {

    private static MockHttpServletRequest from(String address, String... headers) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(address);
        for (int i = 0; i < headers.length; i += 2) {
            request.addHeader(headers[i], headers[i + 1]);
        }
        return request;
    }

    @Test
    void directLoopbackRequestsAreTheHost() {
        assertTrue(RequestOrigin.isHost(from("127.0.0.1")));
        assertTrue(RequestOrigin.isHost(from("::1")));
        assertTrue(RequestOrigin.isHost(from("0:0:0:0:0:0:0:1")));
    }

    @Test
    void otherAddressesAreRemote() {
        assertFalse(RequestOrigin.isHost(from("192.168.1.20")));
        assertFalse(RequestOrigin.isHost(from("203.0.113.9")));
        assertFalse(RequestOrigin.isHost(from("localhost.evil.com")));
        assertFalse(RequestOrigin.isHost(from("")));
        assertFalse(RequestOrigin.isHost(null));
    }

    @Test
    void tunnelledRequestsAreRemoteEvenFromLoopback() {
        assertFalse(RequestOrigin.isHost(from("127.0.0.1", "CF-Connecting-IP", "203.0.113.9")));
        assertFalse(RequestOrigin.isHost(from("::1", "X-Forwarded-For", "203.0.113.9")));
        assertFalse(RequestOrigin.isHost(from("127.0.0.1", "X-Forwarded-For", "127.0.0.1")));
    }

    @Test
    void remoteUsersCannotUseLocalFoldersOrPush() {
        RepositoryConversionController controller = new RepositoryConversionController(null);
        MockHttpServletRequest tunnel = from("127.0.0.1", "CF-Connecting-IP", "203.0.113.9");

        RequestOrigin.HostOnlyException local = assertThrows(RequestOrigin.HostOnlyException.class,
                () -> controller.start("local", "python", "auto", null, false, false, null, null,
                        "C:\\Users\\me", null, null, null, null, tunnel, new MockHttpServletResponse()));
        assertEquals(RequestOrigin.HOST_ONLY_LOCAL_FOLDER, local.getMessage());
        assertTrue(local.getMessage().startsWith("Folder on this computer is only available on the host machine"));

        assertThrows(RequestOrigin.HostOnlyException.class,
                () -> controller.start("git", "python", "auto", null, false, false, "file:///C:/secret", null,
                        null, null, null, null, null, tunnel, new MockHttpServletResponse()));
        assertThrows(RequestOrigin.HostOnlyException.class,
                () -> controller.start("git", "python", "auto", null, false, false, "git@github.com:a/b.git", null,
                        null, null, null, null, null, tunnel, new MockHttpServletResponse()));

    }

    @Test
    void controllerAnswersForbiddenForHostOnlyErrors() {
        var response = new RepositoryConversionController(null)
                .hostOnly(new RequestOrigin.HostOnlyException(RequestOrigin.HOST_ONLY_LOCAL_FOLDER));
        assertEquals(403, response.getStatusCode().value());
        assertEquals(RequestOrigin.HOST_ONLY_LOCAL_FOLDER, response.getBody().get("error"));
    }
}
