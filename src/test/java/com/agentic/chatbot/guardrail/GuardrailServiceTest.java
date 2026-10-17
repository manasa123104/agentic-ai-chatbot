package com.agentic.chatbot.guardrail;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardrailServiceTest {

    private GuardrailService enforce() {
        GuardrailProperties p = new GuardrailProperties();
        p.setEnabled(true);
        p.setMode(GuardrailMode.ENFORCE);
        p.setAllowedRepoHosts(List.of("github.com"));
        return new GuardrailService(p);
    }

    @Test
    void blocksPushToMainAndAllowsFeatureBranch() {
        GuardrailService g = enforce();
        GuardDecision denied = g.check("push_run", Map.of(
                "repoUrl", "https://github.com/manasa123104/T1",
                "branch", "main"));
        assertTrue(denied.wouldDeny());
        assertEquals("protected-branch", denied.ruleId());
        assertThrows(GuardrailDeniedException.class, () -> g.require("push_run", Map.of(
                "repoUrl", "https://github.com/manasa123104/T1", "branch", "main")));

        GuardDecision ok = g.check("push_run", Map.of(
                "repoUrl", "https://github.com/manasa123104/T1",
                "branch", "bot/demo"));
        assertTrue(ok.allowed());
        assertFalse(ok.wouldDeny());
    }

    @Test
    void blocksSecretsInStageInputButNotTokenField() {
        GuardrailService g = enforce();
        assertTrue(g.check("run_stage", Map.of(
                "input", "please use ghp_abcdefghijklmnopqrstuvwxyz012345")).wouldDeny());
        assertFalse(g.check("push_run", Map.of(
                "repoUrl", "https://github.com/o/r",
                "branch", "bot/x",
                "token", "ghp_abcdefghijklmnopqrstuvwxyz012345")).wouldDeny());
    }

    @Test
    void observeModeLogsDenialButAllows() {
        GuardrailProperties p = new GuardrailProperties();
        p.setMode(GuardrailMode.OBSERVE);
        GuardrailService g = new GuardrailService(p);
        GuardDecision d = g.require("push_run", Map.of(
                "repoUrl", "https://github.com/o/r", "branch", "master"));
        assertTrue(d.allowed());
        assertTrue(d.wouldDeny());
    }

    @Test
    void blocksNonGithubHostWhenAllowlisted() {
        GuardrailService g = enforce();
        assertEquals("repo-host", g.check("push_run", Map.of(
                "repoUrl", "https://gitlab.com/o/r", "branch", "bot/x")).ruleId());
    }
}
