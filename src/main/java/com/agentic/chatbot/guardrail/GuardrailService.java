package com.agentic.chatbot.guardrail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * MCP-aware guardrails for BOT: inspect tool names and arguments before stage runs, GitHub pushes and imports.
 * In {@link GuardrailMode#OBSERVE} denials are logged but not thrown; in {@link GuardrailMode#ENFORCE} they block.
 */
@Service
public class GuardrailService {

    private static final Logger log = LoggerFactory.getLogger(GuardrailService.class);

    /** Classic / fine-grained GitHub PATs and common cloud API key shapes in free text. */
    private static final Pattern SECRETS = Pattern.compile(
            "(?i)\\b(ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|gho_[A-Za-z0-9]{20,}"
                    + "|sk-[A-Za-z0-9]{20,}|AIza[0-9A-Za-z\\-_]{20,})\\b");

    private static final int RECENT_CAP = 40;

    private final GuardrailProperties props;
    private final AtomicInteger checks = new AtomicInteger();
    private final AtomicInteger denials = new AtomicInteger();
    private final List<Map<String, Object>> recent = Collections.synchronizedList(new ArrayList<>());

    public GuardrailService(GuardrailProperties props) {
        this.props = props;
    }

    public GuardrailProperties properties() {
        return props;
    }

    /** Check then throw {@link GuardrailDeniedException} when enforce mode denies. */
    public GuardDecision require(String tool, Map<String, Object> args) {
        GuardDecision d = check(tool, args);
        if (!d.allowed()) {
            throw new GuardrailDeniedException(d);
        }
        return d;
    }

    /**
     * Evaluate an MCP tool (or internal action name) and its arguments.
     * Unknown tools are allowed so new tools are not fail-closed by default.
     */
    public GuardDecision check(String tool, Map<String, Object> args) {
        String name = tool == null ? "" : tool.trim().toLowerCase(Locale.ROOT);
        if (!props.isEnabled()) {
            GuardDecision allow = GuardDecision.allow();
            remember(name, allow);
            return allow;
        }
        Map<String, Object> a = args == null ? Map.of() : args;
        GuardDecision raw = switch (name) {
            case "push_run", "stages.push", "push" -> checkPush(a);
            case "run_stage", "stages.run", "run_all_stages", "stages.run_all" -> checkStageInput(a);
            case "test_github_token", "stages.github_check" -> checkRepoUrl(str(a, "repoUrl", "repo_url", "url"));
            case "import_stories_git", "ba.import_git" -> checkRepoUrl(str(a, "repoUrl", "repo_url", "url"));
            case "import_stories_local", "ba.import_local" -> GuardDecision.deny("host-only",
                    "Local folder import is host-only and is not exposed over MCP.");
            default -> checkGenericText(a);
        };
        GuardDecision out = props.getMode() == GuardrailMode.OBSERVE ? raw.asObserved() : raw;
        if (raw.wouldDeny()) {
            log.info("guardrail {} tool={} rule={} reason={}",
                    out.allowed() ? "observe-deny" : "enforce-deny", name, raw.ruleId(), raw.reason());
        }
        remember(name, out);
        return out;
    }

    /** Live report card for Stage bots: always-on checks, not a seventh pipeline stage. */
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", props.isEnabled());
        m.put("mode", props.getMode().name());
        m.put("alwaysOn", true);
        m.put("pipelineStage", false);
        m.put("checks", checks.get());
        m.put("denials", denials.get());
        synchronized (recent) {
            m.put("recent", List.copyOf(recent));
        }
        m.put("blocks", List.of(
                "Secrets pasted into a stage input",
                "A direct push to main or master",
                "Repository URLs that are not https",
                "Git hosts outside the allowlist"));
        return m;
    }

    private void remember(String tool, GuardDecision d) {
        checks.incrementAndGet();
        if (d.wouldDeny()) {
            denials.incrementAndGet();
        }
        Map<String, Object> row = receipt(d, tool);
        synchronized (recent) {
            recent.add(0, row);
            while (recent.size() > RECENT_CAP) {
                recent.remove(recent.size() - 1);
            }
        }
    }

    private GuardDecision checkPush(Map<String, Object> a) {
        GuardDecision repo = checkRepoUrl(str(a, "repoUrl", "repo_url", "url"));
        if (repo.wouldDeny()) {
            return repo;
        }
        String folder = str(a, "folder", "path");
        if (props.isBlockDotGithubWorkflows() && folder.replace('\\', '/')
                .toLowerCase(Locale.ROOT).contains(".github/workflows")) {
            return GuardDecision.deny("workflows-path",
                    "Refusing to target .github/workflows/ (classic PATs need the workflow scope). "
                            + "Use deploy/github-workflows/ or leave the folder blank.");
        }
        String branch = str(a, "branch");
        if (branch.equals("main") || branch.equals("master")) {
            return GuardDecision.deny("protected-branch",
                    "Refusing to push directly to " + branch + ". Use a bot/* or feature/* branch, then open a PR.");
        }
        return checkGenericText(a);
    }

    private GuardDecision checkStageInput(Map<String, Object> a) {
        if (props.isBlockSecrets()) {
            String input = str(a, "input", "content", "stories", "code");
            if (SECRETS.matcher(input).find()) {
                return GuardDecision.deny("secrets",
                        "Input looks like it contains an API key or GitHub token. Remove secrets before running a stage.");
            }
        }
        return GuardDecision.allow();
    }

    private GuardDecision checkGenericText(Map<String, Object> a) {
        if (!props.isBlockSecrets()) {
            return GuardDecision.allow();
        }
        for (Map.Entry<String, Object> e : a.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().toLowerCase(Locale.ROOT);
            // Dedicated credential fields are expected on push / token-check tools.
            if (key.contains("token") || key.contains("password") || key.contains("secret") || key.contains("api_key")
                    || key.contains("apikey")) {
                continue;
            }
            if (e.getValue() instanceof String s && SECRETS.matcher(s).find()) {
                return GuardDecision.deny("secrets",
                        "Argument \"" + e.getKey() + "\" looks like it contains a secret. Remove it and retry.");
            }
        }
        return GuardDecision.allow();
    }

    GuardDecision checkRepoUrl(String repoUrl) {
        String url = repoUrl == null ? "" : repoUrl.trim();
        if (url.isEmpty()) {
            return GuardDecision.allow();
        }
        if (props.isRequireHttpsRemote() && !url.toLowerCase(Locale.ROOT).startsWith("https://")
                && !url.toLowerCase(Locale.ROOT).startsWith("http://127.")
                && !url.toLowerCase(Locale.ROOT).startsWith("file:")) {
            return GuardDecision.deny("https-only",
                    "Remote repository URL must be https:// (SSH and other schemes are blocked for visitors).");
        }
        try {
            URI uri = URI.create(url.contains("://") ? url : "https://" + url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!props.getAllowedRepoHosts().isEmpty()
                    && props.getAllowedRepoHosts().stream().noneMatch(h -> host.equals(h.toLowerCase(Locale.ROOT))
                    || host.endsWith("." + h.toLowerCase(Locale.ROOT)))) {
                return GuardDecision.deny("repo-host",
                        "Repository host \"" + host + "\" is not in the allowlist "
                                + props.getAllowedRepoHosts() + ".");
            }
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("^/+|/+$", "");
            String[] parts = path.split("/");
            if (parts.length >= 2 && !props.getAllowedRepos().isEmpty()) {
                String owner = parts[0].toLowerCase(Locale.ROOT);
                String ownerRepo = owner + "/" + parts[1].replaceAll("\\.git$", "").toLowerCase(Locale.ROOT);
                boolean ok = props.getAllowedRepos().stream().map(s -> s.toLowerCase(Locale.ROOT)).anyMatch(s ->
                        s.equals(owner) || s.equals(ownerRepo));
                if (!ok) {
                    return GuardDecision.deny("repo-allowlist",
                            "Repository " + ownerRepo + " is not in agentic.guardrail.allowed-repos.");
                }
            }
        } catch (IllegalArgumentException e) {
            return GuardDecision.deny("repo-url", "That repository URL could not be parsed.");
        }
        return GuardDecision.allow();
    }

    private static String str(Map<String, Object> a, String... keys) {
        for (String k : keys) {
            Object v = a.get(k);
            if (v != null) {
                return String.valueOf(v).trim();
            }
        }
        return "";
    }

    /** Compact receipt for MCP clients and the stages UI. */
    public Map<String, Object> receipt(GuardDecision d, String tool) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("at", Instant.now().toString());
        m.put("tool", tool == null ? "" : tool);
        m.put("allowed", d.allowed());
        m.put("wouldDeny", d.wouldDeny());
        m.put("ruleId", d.ruleId());
        m.put("reason", d.reason());
        m.put("mode", props.getMode().name());
        return m;
    }
}
