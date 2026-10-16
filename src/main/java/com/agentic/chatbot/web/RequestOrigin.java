package com.agentic.chatbot.web;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Tells whether a request comes from the machine running BOT itself. Features that touch this machine (reading a
 * local folder, pushing with the server's Git login or GITHUB_TOKEN) are only offered to the host.
 *
 * <p>A tunnel such as cloudflared connects from localhost, so a loopback address alone is not enough: any request
 * carrying a proxy header (CF-Connecting-IP, X-Forwarded-For, ...) is treated as remote.
 */
public final class RequestOrigin {

    public static final String HOST_ONLY_LOCAL_FOLDER =
            "Folder on this computer is only available on the host machine \u2013 use GitHub URL or upload";
    public static final String HOST_ONLY_PUSH =
            "Pushing to GitHub is only available on the host machine \u2013 download the converted files as a ZIP instead";
    public static final String HOST_ONLY_REPO_URL =
            "Only https:// repository URLs are available here (file:// and git@ links are only for the host machine) "
                    + "\u2013 use a GitHub https URL or upload";

    private static final String[] PROXY_HEADERS = {
            "CF-Connecting-IP", "True-Client-IP", "X-Forwarded-For", "X-Real-IP", "Forwarded", "X-Forwarded-Host"
    };

    public static final String PUBLIC_MODE_NOTICE =
            "This is the public BOT Space: converting a folder on a computer is disabled here, and pushing to GitHub needs your "
                    + "own GitHub token. Use a GitHub https URL, upload files or a ZIP, or paste code, and download the result as a ZIP.";

    /**
     * Set from {@code agentic.public-mode} at startup. Behind a hosting proxy (Hugging Face, Render, ...) every request
     * arrives from the proxy, so no request may ever be treated as the host machine.
     */
    private static volatile boolean publicMode;

    private RequestOrigin() {
    }

    public static void setPublicMode(boolean enabled) {
        publicMode = enabled;
    }

    public static boolean isPublicMode() {
        return publicMode;
    }

    /**
     * True only for a direct request from 127.0.0.1 / ::1 that did not pass through a proxy or tunnel, and never
     * in public mode.
     */
    public static boolean isHost(HttpServletRequest request) {
        if (publicMode || request == null) {
            return false;
        }
        for (String header : PROXY_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return isLoopback(request.getRemoteAddr());
    }

    static boolean isLoopback(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        try {
            // Literal IPs only (no DNS lookup): "127.0.0.1", "0:0:0:0:0:0:0:1", "::1"
            if (!address.matches("[0-9a-fA-F:.]+")) {
                return false;
            }
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /** Thrown when a remote user asks for something only the host machine may do; mapped to HTTP 403. */
    public static class HostOnlyException extends RuntimeException {
        public HostOnlyException(String message) {
            super(message);
        }
    }
}
