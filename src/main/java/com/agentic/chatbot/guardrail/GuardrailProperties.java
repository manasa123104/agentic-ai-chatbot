package com.agentic.chatbot.guardrail;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** {@code agentic.guardrail.*} settings. */
@ConfigurationProperties(prefix = "agentic.guardrail")
public class GuardrailProperties {

    private boolean enabled = true;
    private GuardrailMode mode = GuardrailMode.ENFORCE;
    /** Hostnames allowed for git clone / push (empty = any https host). */
    private List<String> allowedRepoHosts = new ArrayList<>(List.of("github.com"));
    /** When non-empty, only these {@code owner} or {@code owner/repo} values may be pushed to. */
    private List<String> allowedRepos = new ArrayList<>();
    private boolean blockSecrets = true;
    private boolean blockDotGithubWorkflows = true;
    private boolean requireHttpsRemote = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public GuardrailMode getMode() {
        return mode;
    }

    public void setMode(GuardrailMode mode) {
        this.mode = mode == null ? GuardrailMode.ENFORCE : mode;
    }

    public List<String> getAllowedRepoHosts() {
        return allowedRepoHosts;
    }

    public void setAllowedRepoHosts(List<String> allowedRepoHosts) {
        this.allowedRepoHosts = allowedRepoHosts == null ? new ArrayList<>() : allowedRepoHosts;
    }

    public List<String> getAllowedRepos() {
        return allowedRepos;
    }

    public void setAllowedRepos(List<String> allowedRepos) {
        this.allowedRepos = allowedRepos == null ? new ArrayList<>() : allowedRepos;
    }

    public boolean isBlockSecrets() {
        return blockSecrets;
    }

    public void setBlockSecrets(boolean blockSecrets) {
        this.blockSecrets = blockSecrets;
    }

    public boolean isBlockDotGithubWorkflows() {
        return blockDotGithubWorkflows;
    }

    public void setBlockDotGithubWorkflows(boolean blockDotGithubWorkflows) {
        this.blockDotGithubWorkflows = blockDotGithubWorkflows;
    }

    public boolean isRequireHttpsRemote() {
        return requireHttpsRemote;
    }

    public void setRequireHttpsRemote(boolean requireHttpsRemote) {
        this.requireHttpsRemote = requireHttpsRemote;
    }
}
