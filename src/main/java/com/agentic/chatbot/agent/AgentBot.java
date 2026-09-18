package com.agentic.chatbot.agent;

public interface AgentBot {
    String name();

    AgentResult run(AgentContext context);
}
