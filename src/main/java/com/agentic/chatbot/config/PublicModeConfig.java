package com.agentic.chatbot.config;

import com.agentic.chatbot.web.RequestOrigin;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads {@code agentic.public-mode} (env PUBLIC_MODE). When true, every request is treated as a remote visitor:
 * no local folders, no push with the server's Git login, and each visitor only sees their own jobs.
 * Use it whenever BOT sits behind a hosting proxy (for example a Hugging Face Space).
 */
@Component
public class PublicModeConfig {

    private static final Logger log = LoggerFactory.getLogger(PublicModeConfig.class);

    private final boolean publicMode;

    public PublicModeConfig(@Value("${agentic.public-mode:false}") boolean publicMode) {
        this.publicMode = publicMode;
    }

    @PostConstruct
    public void apply() {
        RequestOrigin.setPublicMode(publicMode);
        if (publicMode) {
            log.info("Public mode is ON: every request is treated as remote (no local folders, no push)");
        }
    }
}
