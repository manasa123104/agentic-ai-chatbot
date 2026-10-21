package com.agentic.chatbot.guardrail;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Honours a visitor's Guardrail On / Off choice for this request only.
 * Header {@code X-BOT-Guardrail} wins; otherwise the session value is used. Default is On.
 */
@Component
public class GuardrailOverrideFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-BOT-Guardrail";
    public static final String SESSION_KEY = "bot.guardrail.enabled";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path == null || !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Boolean fromHeader = parse(request.getHeader(HEADER));
        boolean on;
        if (fromHeader != null) {
            request.getSession(true).setAttribute(SESSION_KEY, fromHeader);
            on = fromHeader;
        } else {
            HttpSession session = request.getSession(false);
            Object stored = session == null ? null : session.getAttribute(SESSION_KEY);
            Boolean fromSession = stored instanceof Boolean b ? b : parse(stored == null ? null : String.valueOf(stored));
            on = fromSession == null || fromSession;
        }
        GuardrailOverride.set(on);
        try {
            chain.doFilter(request, response);
        } finally {
            GuardrailOverride.clear();
        }
    }

    public static Boolean parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase();
        if (s.isEmpty()) {
            return null;
        }
        if (s.equals("off") || s.equals("false") || s.equals("0") || s.equals("no")) {
            return false;
        }
        if (s.equals("on") || s.equals("true") || s.equals("1") || s.equals("yes")) {
            return true;
        }
        return null;
    }
}
