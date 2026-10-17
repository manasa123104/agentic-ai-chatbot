package com.agentic.chatbot.mcp;

import com.agentic.chatbot.guardrail.GuardrailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({McpProperties.class, GuardrailProperties.class})
public class McpConfig {
}
