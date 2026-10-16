package com.agentic.chatbot.agent;

import com.agentic.chatbot.generator.ScreenCodeGenerator;
import com.agentic.chatbot.generator.SiteAgent;
import com.agentic.chatbot.generator.SiteBackends;
import com.agentic.chatbot.generator.UploadedStory;
import com.agentic.chatbot.stage.StageLanguage;
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
            String forDev = UploadedStory.forGeneration(context.getUserStory());
            List<Path> files = screenCodeGenerator.generate(forDev, context.getProjectDir());
            files.forEach(context::addGeneratedFile);
            var language = SiteBackends.detect(forDev);
            String lang = language == StageLanguage.JAVA
                    ? "Generated Java website code (Spring Boot controllers + Thymeleaf templates)."
                    : "Generated the website (Spring Boot preview project + a " + language.label() + " server in server/"
                    + language.id() + "/)" + (SiteBackends.hasServer(language) ? "." : " with a README for converting it.");
            String agent = SiteAgent.wantsAgent(forDev) ? " Chatbot agent added to every page." : "";
            return AgentResult.ok(lang + agent + " Files: " + files.size(), files);
        } catch (Exception e) {
            return AgentResult.fail("Java Developer bot failed: " + e.getMessage());
        }
    }
}
