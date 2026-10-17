package com.agentic.chatbot.mcp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code agentic.mcp.*} settings for the JSON-RPC MCP endpoint. */
@ConfigurationProperties(prefix = "agentic.mcp")
public class McpProperties {

    private boolean enabled = true;
    /** Optional shared secret: when set, clients must send header {@code X-BOT-MCP-Key}. */
    private String apiKey = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey == null ? "" : apiKey;
    }
}
