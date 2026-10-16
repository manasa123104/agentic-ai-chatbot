package com.agentic.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Asks the GitHub API what a visitor's token can do with a repository, before (or instead of) a push, so the page can say
 * exactly why a push would fail: wrong account, token not granted to the repository, read-only token, missing repo scope or a
 * repository that does not exist. The token is only sent to the GitHub API as a bearer header; it is never logged or returned.
 */
@Service
public class GitHubPreflight {

    /** The outcome classes; {@link Result#ok()} is true for WRITABLE and UNVERIFIED only. */
    public enum Code {
        /** The token can push (push/maintain/admin, or a classic token with the repo scope). */
        WRITABLE,
        /** Authenticated and the repository is visible, but GitHub does not reveal a fine-grained token's Contents permission. */
        UNVERIFIED,
        /** The repository does not exist (and is not a private one the token merely cannot see). */
        REPO_NOT_FOUND,
        /** The repository exists publicly, but this token cannot see it (wrong account or fine-grained token not granted). */
        NO_ACCESS,
        /** The token can read the repository but not push to it. */
        READ_ONLY,
        /** Classic token without the repo / public_repo scope. */
        MISSING_SCOPE,
        /** GitHub rejected the token (401). */
        BAD_TOKEN,
        /** Rate limit, SAML SSO or another 403 from the API. */
        FORBIDDEN,
        /** Not a github.com URL, or the API could not be reached: nothing was checked. */
        SKIPPED
    }

    /** Result of a check; {@code message} is safe to show to the user. */
    public record Result(Code code, String message, String login, String scopes, String tokenType, boolean canPush) {
        public boolean ok() {
            return code == Code.WRITABLE || code == Code.UNVERIFIED || code == Code.SKIPPED;
        }
    }

    private static final Pattern GITHUB = Pattern.compile(
            "^(?:https://(?:www\\.)?github\\.com/|git@github\\.com:)([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String apiUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public GitHubPreflight(@Value("${agentic.github.api-url:https://api.github.com}") String apiUrl) {
        this.apiUrl = apiUrl == null || apiUrl.isBlank() ? "https://api.github.com" : apiUrl.replaceAll("/+$", "");
    }

    /** {owner, repo} of a github.com URL, empty for any other host. */
    public static Optional<String[]> ownerAndRepo(String repoUrl) {
        Matcher m = GITHUB.matcher(repoUrl == null ? "" : repoUrl.trim());
        return m.matches() ? Optional.of(new String[]{m.group(1), m.group(2)}) : Optional.empty();
    }

    /** "classic", "fine-grained" or "unknown", from the token prefix. */
    static String tokenType(String token) {
        String t = token == null ? "" : token;
        if (t.startsWith("github_pat_")) {
            return "fine-grained";
        }
        if (t.startsWith("ghp_") || t.startsWith("gho_") || t.startsWith("ghu_") || t.startsWith("ghs_")) {
            return "classic";
        }
        return "unknown";
    }

    public Result check(String repoUrl, String token) {
        Optional<String[]> parts = ownerAndRepo(repoUrl);
        if (parts.isEmpty() || token == null || token.isBlank()) {
            return new Result(Code.SKIPPED, "Not checked: only github.com repositories with a token can be checked ahead of a push.",
                    null, null, tokenType(token), true);
        }
        String owner = parts.get()[0];
        String repo = parts.get()[1];
        String url = "https://github.com/" + owner + "/" + repo;
        String type = tokenType(token);
        try {
            HttpResponse<String> r = get("/repos/" + owner + "/" + repo, token);
            String scopes = r.headers().firstValue("X-OAuth-Scopes").map(String::trim).orElse(null);
            String login = login(token);
            String who = login == null ? "" : " (token belongs to GitHub user " + login + ")";
            switch (r.statusCode()) {
                case 200 -> {
                    return fromRepository(r.body(), url, owner, scopes, login, who, type);
                }
                case 401 -> {
                    return new Result(Code.BAD_TOKEN, "GitHub rejected the token (bad, expired or revoked). Create a new token "
                            + "with repo access (classic: repo scope; fine-grained: Contents Read and write on " + owner + "/"
                            + repo + ") and paste it again.", null, scopes, type, false);
                }
                case 403 -> {
                    String why = message(r.body());
                    return new Result(Code.FORBIDDEN, "GitHub refused the request" + (why.isEmpty() ? "" : " (" + why + ")")
                            + ". If your organisation uses SAML SSO, authorize the token for it under GitHub > Settings > "
                            + "Developer settings > Tokens > Configure SSO; if it is a rate limit, wait a few minutes.",
                            login, scopes, type, false);
                }
                case 404 -> {
                    HttpResponse<String> anon = getAnonymous("/repos/" + owner + "/" + repo);
                    if (anon != null && anon.statusCode() == 200) {
                        return new Result(Code.NO_ACCESS, "Token has no access to this repository (wrong account, or a "
                                + "fine-grained token not granted to " + repo + ")" + who + ". " + url + " exists, but this "
                                + "token cannot see it. Use a token created by the account that owns or collaborates on it; for "
                                + "a fine-grained token choose 'Only select repositories' and add " + repo + " with Contents: "
                                + "Read and write.", login, scopes, type, false);
                    }
                    return new Result(Code.REPO_NOT_FOUND, "Repository not found - create " + url + " first on GitHub "
                            + "(New repository, name " + repo + "). If it already exists and is private, this token's account"
                            + who + " cannot see it.", login, scopes, type, false);
                }
                default -> {
                    return new Result(Code.SKIPPED, "GitHub answered HTTP " + r.statusCode() + " while checking the token; "
                            + "trying the push anyway.", login, scopes, type, true);
                }
            }
        } catch (IOException e) {
            return new Result(Code.SKIPPED, "Could not reach the GitHub API to check the token; trying the push anyway.",
                    null, null, type, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(Code.SKIPPED, "Token check interrupted.", null, null, type, true);
        }
    }

    private Result fromRepository(String body, String url, String owner, String scopes, String login, String who, String type)
            throws IOException {
        JsonNode permissions = JSON.readTree(body).path("permissions");
        boolean push = permissions.path("push").asBoolean(false) || permissions.path("maintain").asBoolean(false)
                || permissions.path("admin").asBoolean(false);
        boolean priv = JSON.readTree(body).path("private").asBoolean(false);
        if (scopes != null && !scopes.isBlank()) {
            // only classic tokens send X-OAuth-Scopes
            List<String> have = Arrays.stream(scopes.toLowerCase(Locale.ROOT).split("\\s*,\\s*")).toList();
            boolean enough = have.contains("repo") || !priv && have.contains("public_repo");
            if (!enough) {
                return new Result(Code.MISSING_SCOPE, "The classic token has scopes [" + scopes + "] but pushing to "
                        + (priv ? "a private" : "a public") + " repository needs the " + (priv ? "repo" : "repo (or public_repo)")
                        + " scope. Tick 'repo' when creating the token.", login, scopes, type, false);
            }
        }
        if (!push) {
            return new Result(Code.READ_ONLY, "Token can read " + url + " but not write" + who + ": set Contents: Read and "
                    + "write on the fine-grained token (and make sure the account has write access), or use a classic token "
                    + "with the repo scope.", login, scopes, type, false);
        }
        if ("fine-grained".equals(type)) {
            return new Result(Code.UNVERIFIED, "The token can see " + url + " and the account may push" + who + ". GitHub does "
                    + "not show a fine-grained token's Contents permission, so if the push still fails, edit the token and set "
                    + "Contents: Read and write for this repository.", login, scopes, type, true);
        }
        return new Result(Code.WRITABLE, "Token can write to " + url + who + ".", login, scopes, type, true);
    }

    private String login(String token) {
        try {
            HttpResponse<String> r = get("/user", token);
            return r.statusCode() == 200 ? JSON.readTree(r.body()).path("login").asText(null) : null;
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String message(String body) {
        try {
            return JSON.readTree(body).path("message").asText("");
        } catch (IOException e) {
            return "";
        }
    }

    private HttpResponse<String> get(String path, String token) throws IOException, InterruptedException {
        return http.send(request(path).header("Authorization", "Bearer " + token).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> getAnonymous(String path) {
        try {
            return http.send(request(path).build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(apiUrl + path)).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json").header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "agentic-ai-chatbot").GET();
    }
}
