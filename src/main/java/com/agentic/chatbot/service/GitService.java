package com.agentic.chatbot.service;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs the {@code git} command line to clone repositories used as conversion input and to push converted output to a
 * branch. Credentials come from the machine's Git credential manager, or from {@code agentic.github.token}
 * (GITHUB_TOKEN) for HTTPS remotes. Git config files are never modified.
 */
@Service
public class GitService {

    private static final Pattern REPO_URL = Pattern.compile(
            "^(https://[\\w.-]+(:\\d+)?/[\\w./~-]+|git@[\\w.-]+:[\\w./~-]+|file:///?[\\w:./\\\\ ~-]+)$");
    private static final Pattern GITHUB = Pattern.compile(
            "^(?:https://github\\.com/|git@github\\.com:)([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$");

    private final String token;
    private final String authorName;
    private final String authorEmail;

    public GitService(@Value("${agentic.github.token:}") String token,
                      @Value("${agentic.git.author-name:BOT Code Converter}") String authorName,
                      @Value("${agentic.git.author-email:bot-converter@users.noreply.github.com}") String authorEmail) {
        this.token = token == null ? "" : token.trim();
        this.authorName = authorName;
        this.authorEmail = authorEmail;
    }

    public record PushResult(String branch, String baseBranch, String commit, String branchUrl, String compareUrl) {
    }

    /**
     * Cleans a pasted repository URL: surrounding/inner whitespace and trailing "." and "/" characters (a stray full stop
     * after the name is the commonest typo) are removed; a trailing ".git" is kept. Never returns null.
     */
    public static String normalizeRepoUrl(String url) {
        if (url == null) {
            return "";
        }
        String u = url.strip().replaceAll("\\s+", "");
        return u.replaceAll("[./]+$", "");
    }

    public static void validateRepoUrl(String url) {
        if (url != null && url.strip().endsWith(".") && !url.strip().startsWith("-")) {
            throw new IllegalArgumentException("The repository URL ends with a full stop (\"" + url.strip()
                    + "\"). Remove it, e.g. https://github.com/owner/repo");
        }
        if (url == null || url.isBlank() || url.startsWith("-") || !REPO_URL.matcher(url.trim()).matches()) {
            throw new IllegalArgumentException("Enter a repository URL such as https://github.com/owner/repo.git");
        }
        String message = githubUrlProblem(url.trim());
        if (message != null) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Explains why a github.com URL is not a repository URL (an organisation/user page, or a page inside a
     * repository), or returns null when it looks fine or is not a github.com URL.
     */
    static String githubUrlProblem(String url) {
        java.util.regex.Matcher m = Pattern.compile("^https://(?:www\\.)?github\\.com(?:/([^?#]*))?(?:[?#].*)?$").matcher(url);
        if (!m.matches()) {
            return null;
        }
        String path = m.group(1) == null ? "" : m.group(1).replaceAll("^/+|/+$", "");
        String[] parts = path.isEmpty() ? new String[0] : path.split("/");
        if (parts.length == 0) {
            return "This is the GitHub home page - use a repository URL such as https://github.com/spring-guides/gs-gradle";
        }
        if (parts.length == 1) {
            return "This is a GitHub organisation or user page (" + parts[0] + ") - use a repository URL such as https://github.com/"
                    + parts[0] + "/gs-gradle (owner/repository).";
        }
        if (parts.length > 2) {
            String base = "https://github.com/" + parts[0] + "/" + parts[1].replaceAll("\\.git$", "");
            return "This looks like a page inside a repository - use the repository URL " + base
                    + " and put the branch and sub-folder in their own boxes.";
        }
        return null;
    }

    public void cloneRepo(String url, String branch, Path destination) {
        cloneRepo(url, branch, destination, null);
    }

    /** True when the server has its own GITHUB_TOKEN configured. */
    public boolean hasServerToken() {
        return !token.isEmpty();
    }

    /** Clones with {@code userToken} (a visitor's own PAT) instead of the server token when it is given. */
    public void cloneRepo(String url, String branch, Path destination, String userToken) {
        validateRepoUrl(url);
        List<String> args = new ArrayList<>(List.of("clone", "--depth", "1"));
        if (branch != null && !branch.isBlank()) {
            validateBranch(branch);
            args.addAll(List.of("--branch", branch.trim()));
        }
        args.addAll(List.of("--", url.trim(), destination.toString()));
        run(destination.getParent(), 600, authArgs(url, userToken), args, userToken);
    }

    /**
     * Clones {@code repoUrl}, creates {@code branch} from {@code baseBranch} (or the default branch), copies
     * {@code content} into {@code folder}, commits and pushes. Returns the branch and compare links.
     */
    public PushResult pushDirectory(Path content, String repoUrl, String branch, String baseBranch, String folder,
                                    String commitMessage, Path workDir) throws IOException {
        return pushDirectory(content, repoUrl, branch, baseBranch, folder, commitMessage, workDir, null, false);
    }

    /**
     * Like the method above, with two additions for the stage bots: {@code userToken} (the visitor's own PAT, used
     * instead of the server token and never logged) and {@code pullFirst} (start from the latest remote copy of
     * {@code branch} when it already exists, so the generated files are laid over what is there, then push).
     */
    public PushResult pushDirectory(Path content, String repoUrl, String branch, String baseBranch, String folder,
                                    String commitMessage, Path workDir, String userToken, boolean pullFirst)
            throws IOException {
        validateRepoUrl(repoUrl);
        validateBranch(branch);
        String target = folder == null ? "" : folder.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
        if (target.contains("..") || target.startsWith("-") || target.startsWith(".git")) {
            throw new IllegalArgumentException("Folder must be a relative path inside the repository.");
        }
        Path repo = workDir.resolve("repo");
        Files.createDirectories(workDir);
        List<String> auth = authArgs(repoUrl, userToken);
        boolean pulled = false;
        if (pullFirst) {
            try {
                run(workDir, 600, auth, List.of("clone", "--depth", "1", "--branch", branch.trim(), "--", repoUrl.trim(),
                        repo.toString()), userToken);
                pulled = true;
            } catch (IllegalStateException e) {
                LoggerFactory.getLogger(GitService.class).info("Branch {} not found on the remote; starting from the base branch", branch.trim());
                deleteQuietly(repo);
            }
        }
        if (!pulled) {
            List<String> clone = new ArrayList<>(List.of("clone", "--depth", "1"));
            if (baseBranch != null && !baseBranch.isBlank()) {
                validateBranch(baseBranch);
                clone.addAll(List.of("--branch", baseBranch.trim()));
            }
            clone.addAll(List.of("--", repoUrl.trim(), repo.toString()));
            run(workDir, 600, auth, clone, userToken);
        }

        String base = pulled ? branch.trim()
                : baseBranch == null || baseBranch.isBlank()
                ? run(repo, 30, List.of(), List.of("rev-parse", "--abbrev-ref", "HEAD")).strip()
                : baseBranch.trim();
        run(repo, 30, List.of(), List.of("checkout", "-B", branch.trim()));

        Path destination = target.isEmpty() ? repo : repo.resolve(target).normalize();
        if (!destination.startsWith(repo)) {
            throw new IllegalArgumentException("Folder must be a relative path inside the repository.");
        }
        copyTree(content, destination);
        run(repo, 60, List.of(), List.of("add", "-A", "--", target.isEmpty() ? "." : target));
        String status = run(repo, 30, List.of(), List.of("status", "--porcelain"));
        if (status.isBlank()) {
            throw new IllegalStateException("Nothing to push: the branch already contains this code.");
        }
        String message = commitMessage == null || commitMessage.isBlank()
                ? "Add converted code from BOT" : commitMessage.strip();
        run(repo, 60, List.of(), List.of("-c", "user.name=" + authorName, "-c", "user.email=" + authorEmail,
                "-c", "commit.gpgsign=false", "commit", "-q", "-m", message));
        String commit = run(repo, 30, List.of(), List.of("rev-parse", "HEAD")).strip();
        run(repo, 600, auth, List.of("push", "-u", "origin", "HEAD:refs/heads/" + branch.trim()), userToken);

        Optional<String[]> github = githubRepo(repoUrl);
        String branchUrl = github.map(g -> "https://github.com/" + g[0] + "/" + g[1] + "/tree/" + branch.trim()
                + (target.isEmpty() ? "" : "/" + target)).orElse(null);
        String compareUrl = pulled ? null : github.map(g -> "https://github.com/" + g[0] + "/" + g[1] + "/compare/" + base
                + "..." + branch.trim() + "?expand=1").orElse(null);
        return new PushResult(branch.trim(), base, commit, branchUrl, compareUrl);
    }

    static Optional<String[]> githubRepo(String url) {
        Matcher m = GITHUB.matcher(url.trim());
        return m.matches() ? Optional.of(new String[] {m.group(1), m.group(2)}) : Optional.empty();
    }

    void validateBranch(String branch) {
        if (branch == null || branch.isBlank() || branch.startsWith("-")) {
            throw new IllegalArgumentException("Enter a branch name, for example converted/python.");
        }
        try {
            run(null, 15, List.of(), List.of("check-ref-format", "--branch", branch.trim()));
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("\"" + branch + "\" is not a valid Git branch name.");
        }
    }

    private List<String> authArgs(String url) {
        return authArgs(url, null);
    }

    /** A visitor's own token wins over the server token; the server token is never used when one is given. */
    private List<String> authArgs(String url, String userToken) {
        String use = userToken != null && !userToken.isBlank() ? userToken.trim() : token;
        if (use.isEmpty() || !url.trim().startsWith("https://")) {
            return List.of();
        }
        String basic = Base64.getEncoder().encodeToString(("x-access-token:" + use).getBytes(StandardCharsets.UTF_8));
        return List.of("-c", "http.extraHeader=Authorization: Basic " + basic);
    }

    private String run(Path dir, int timeoutSeconds, List<String> configArgs, List<String> args) {
        return run(dir, timeoutSeconds, configArgs, args, null);
    }

    private String run(Path dir, int timeoutSeconds, List<String> configArgs, List<String> args, String userToken) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(configArgs);
        command.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        if (dir != null) {
            pb.directory(dir.toFile());
        }
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        try {
            Process process = pb.start();
            var processOutput = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException e) {
                    return "";
                }
            });
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("git " + args.get(0) + " timed out");
            }
            String output = processOutput.join();
            if (process.exitValue() != 0) {
                throw new IllegalStateException("git " + args.get(0) + " failed: " + scrub(output, userToken).strip());
            }
            return output;
        } catch (IOException e) {
            throw new IllegalStateException("Git is not installed or not on PATH: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git " + args.get(0) + " was interrupted");
        }
    }

    private String scrub(String text, String userToken) {
        String out = token.isEmpty() ? text : text.replace(token, "***");
        if (userToken != null && !userToken.isBlank()) {
            String t = userToken.trim();
            out = out.replace(t, "***").replace(Base64.getEncoder().encodeToString(
                    ("x-access-token:" + t).getBytes(StandardCharsets.UTF_8)), "***");
        }
        return out;
    }

    /** Recursively deletes a folder, ignoring errors (read-only Git objects are made writable first). */
    public static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                path.toFile().setWritable(true);
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            // best effort
        }
    }

    static void copyTree(Path from, Path to) throws IOException {
        try (var paths = Files.walk(from)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path dest = to.resolve(from.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
