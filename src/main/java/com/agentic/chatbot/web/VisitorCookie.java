package com.agentic.chatbot.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Anonymous per-browser identity: a random HttpOnly cookie. Jobs store only a hash of it, so the job list never
 * reveals the cookie value itself.
 */
public final class VisitorCookie {

    public static final String NAME = "bot_visitor";
    private static final Pattern VALID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private VisitorCookie() {
    }

    /** The visitor's owner key; creates and sets the cookie when the browser has none (or an invalid one). */
    public static String owner(HttpServletRequest request, HttpServletResponse response) {
        String id = null;
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (NAME.equals(c.getName()) && c.getValue() != null && VALID.matcher(c.getValue()).matches()) {
                    id = c.getValue();
                    break;
                }
            }
        }
        if (id == null) {
            id = UUID.randomUUID().toString();
            boolean https = "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto")) || request.isSecure();
            ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(NAME, id)
                    .httpOnly(true).path("/").maxAge(Duration.ofDays(365));
            if (RequestOrigin.isPublicMode()) {
                // A Hugging Face Space page embeds the app in a cross-site iframe, where a Lax cookie is never sent.
                builder.secure(true).sameSite("None").partitioned(true);
            } else {
                builder.secure(https).sameSite("Lax");
            }
            ResponseCookie cookie = builder.build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        return hash(id);
    }

    static String hash(String id) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(id.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
