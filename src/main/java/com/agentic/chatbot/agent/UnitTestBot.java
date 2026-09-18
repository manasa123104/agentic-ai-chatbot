package com.agentic.chatbot.agent;

import com.agentic.chatbot.generator.UnitTestGenerator;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class UnitTestBot implements AgentBot {

    private final UnitTestGenerator unitTestGenerator;

    public UnitTestBot(UnitTestGenerator unitTestGenerator) {
        this.unitTestGenerator = unitTestGenerator;
    }

    @Override
    public String name() {
        return "Bot 2 - Unit Testing";
    }

    @Override
    public AgentResult run(AgentContext context) {
        try {
            List<Path> files = unitTestGenerator.generate(context.getUserStory(), context.getProjectDir());
            files.forEach(context::addGeneratedFile);
            return AgentResult.ok(
                    "Generated JUnit 5 unit tests for controllers/services. Files: " + files.size()
                            + ". Run: mvn test (inside generated-app)",
                    files);
        } catch (Exception e) {
            return AgentResult.fail("Unit test generation failed: " + e.getMessage());
        }
    }
}
