package com.agentic.chatbot.agent;

import com.agentic.chatbot.generator.ScreenCodeGenerator;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class UiGeneratorBot implements AgentBot {

    private final ScreenCodeGenerator screenCodeGenerator;

    public UiGeneratorBot(ScreenCodeGenerator screenCodeGenerator) {
        this.screenCodeGenerator = screenCodeGenerator;
    }

    @Override
    public String name() {
        return "Bot 2 - Java Developer";
    }

    @Override
    public AgentResult run(AgentContext context) {
        try {
            // Prefer BA-refined story; generators still key off original topic keywords in the prompt.
            String forDev = context.getUserStory();
            List<Path> files = screenCodeGenerator.generate(forDev, context.getProjectDir());
            files.forEach(context::addGeneratedFile);
            return AgentResult.ok(
                    "Generated Java website code (Spring Boot controllers + Thymeleaf templates). Files: " + files.size(),
                    files);
        } catch (Exception e) {
            return AgentResult.fail("Java Developer bot failed: " + e.getMessage());
        }
    }
}
