package com.agentic.chatbot.model;

import java.util.List;

/**
 * A language the code converter understands. {@code id} doubles as the sample file name
 * under {@code classpath:code-samples/<sample>/<id>.<extension>}.
 */
public record ProgrammingLanguage(
        String id,
        String name,
        String extension,
        String runCommand,
        List<String> aliases) {
}
