package com.agentic.chatbot.web;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One rule for every "push to GitHub" form in the app (converter jobs and the stage bots). The host machine may use the
 * server's GITHUB_TOKEN or Git login; everyone else, including visitors of the public Render site, must paste their own
 * token. The token is used for that single push and is never stored or logged, and the server's credentials are never used
 * on behalf of a remote visitor.
 */
public final class GitPushPolicy {

    public static final String TOKEN_NEEDED = "Pushing to GitHub here needs your own GitHub token (the server's GitHub login is "
            + "only for the host machine). Create a personal access token with repo access in GitHub > Settings > Developer "
            + "settings, paste it in the Token box and push again. It is used for this push only and is never stored or logged.";

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_.\\-]{20,255}");

    private GitPushPolicy() {
    }

    /** The visitor's own token (or null when the host pushes with its own login); throws HostOnlyException when not allowed. */
    public static String authorize(HttpServletRequest request, String repoUrl, String rawToken) {
        boolean host = RequestOrigin.isHost(request);
        String token = validToken(rawToken);
        if (!host && token == null) {
            throw new RequestOrigin.HostOnlyException(TOKEN_NEEDED);
        }
        String url = repoUrl == null ? "" : repoUrl.trim();
        if (!host && !url.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new RequestOrigin.HostOnlyException(RequestOrigin.HOST_ONLY_REPO_URL);
        }
        return token;
    }

    /** The token when it is token-shaped, null when blank; the message never repeats the value. */
    public static String validToken(String token) {
        String t = token == null ? "" : token.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (!TOKEN.matcher(t).matches()) {
            throw new IllegalArgumentException("That does not look like a GitHub token (letters, digits, _ and - only).");
        }
        return t;
    }
}
