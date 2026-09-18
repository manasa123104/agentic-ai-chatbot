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
        return "Bot 1 - UI / Webpage Screen Generator";
    }

    @Override
    public AgentResult run(AgentContext context) {
        try {
            List<Path> files = screenCodeGenerator.generate(context.getUserStory(), context.getProjectDir());
            files.forEach(context::addGeneratedFile);
            return AgentResult.ok(
                    "Created Java web screens (controller + Thymeleaf) from the user story. Files: " + files.size(),
                    files);
        } catch (Exception e) {
            return AgentResult.fail("UI generation failed: " + e.getMessage());
        }
    }
}
