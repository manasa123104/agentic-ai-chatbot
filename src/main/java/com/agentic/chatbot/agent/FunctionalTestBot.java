package com.agentic.chatbot.agent;

import com.agentic.chatbot.generator.FunctionalTestGenerator;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class FunctionalTestBot implements AgentBot {

    private final FunctionalTestGenerator functionalTestGenerator;

    public FunctionalTestBot(FunctionalTestGenerator functionalTestGenerator) {
        this.functionalTestGenerator = functionalTestGenerator;
    }

    @Override
    public String name() {
        return "Bot 4 - Functional Testing";
    }

    @Override
    public AgentResult run(AgentContext context) {
        try {
            List<Path> files = functionalTestGenerator.generate(context.getUserStory(), context.getProjectDir());
            files.forEach(context::addGeneratedFile);
            return AgentResult.ok(
                    "Generated functional (MockMvc end-to-end) tests. Files: " + files.size()
                            + ". Run: mvn test (inside generated-app)",
                    files);
        } catch (Exception e) {
            return AgentResult.fail("Functional Tester failed: " + e.getMessage());
        }
    }
}
