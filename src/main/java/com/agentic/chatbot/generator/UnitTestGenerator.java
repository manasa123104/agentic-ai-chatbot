package com.agentic.chatbot.generator;

import com.agentic.chatbot.llm.LlmClient;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class UnitTestGenerator {

    private static final Pattern FILE_BLOCK = Pattern.compile(
            "(?s)```(?:java)?\\s*\\n?//\\s*FILE:\\s*(.+?)\\n(.*?)```");

    private final LlmClient llmClient;

    public UnitTestGenerator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public List<Path> generate(String userStory, Path projectDir) throws IOException {
        StoryAnalyzer.Analysis analysis = StoryAnalyzer.analyze(userStory);
        List<Path> written = new ArrayList<>();

        if (llmClient.isAvailable()) {
            try {
                String sources = readMainSources(projectDir);
                String system = """
                        You are a Java unit testing specialist.
                        Write JUnit 5 + MockMvc (or Mockito) unit tests for the generated Spring controllers.
                        Output ONLY markdown fences starting with // FILE: relative/path
                        Place tests under src/test/java/...
                        Cover happy path and validation/error path from the user story.
                        """;
                String content = llmClient.complete(system,
                        "User story:\n" + userStory + "\n\nExisting sources:\n" + sources);
                written.addAll(writeFences(content, projectDir));
                if (!written.isEmpty()) {
                    return written;
                }
            } catch (Exception ignored) {
                // offline fallback
            }
        }

        written.add(writeOfflineTest(analysis, projectDir));
        return written;
    }

    private Path writeOfflineTest(StoryAnalyzer.Analysis a, Path projectDir) throws IOException {
        String pkgPath = a.packageName().replace('.', '/');
        Path testFile = projectDir.resolve("src/test/java/" + pkgPath + "/" + a.classPrefix() + "ControllerTest.java");
        Files.createDirectories(testFile.getParent());
        String route = "/" + a.featureName().toLowerCase().replaceAll("[^a-z0-9-]", "");

        String body = """
                package %s;

                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.test.web.servlet.MockMvc;

                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

                @SpringBootTest(classes = com.generated.app.GeneratedAppApplication.class)
                @AutoConfigureMockMvc
                class %sControllerTest {

                    @Autowired
                    private MockMvc mockMvc;

                    @Test
                    void showPage_returnsOk() throws Exception {
                        mockMvc.perform(get("%s"))
                                .andExpect(status().isOk())
                                .andExpect(view().name("%s"))
                                .andExpect(model().attributeExists("form"));
                    }

                    @Test
                    void submit_withData_succeeds() throws Exception {
                        mockMvc.perform(post("%s")
                                        .param("field1", "alice")
                                        .param("field2", "secret"))
                                .andExpect(result -> {
                                    int status = result.getResponse().getStatus();
                                    if (status >= 500) {
                                        throw new AssertionError("Unexpected server error: " + status);
                                    }
                                });
                    }
                }
                """.formatted(a.packageName(), a.classPrefix(), route,
                a.featureName().toLowerCase(), route);

        // WebMvcTest needs a slice config — for generated apps use @SpringBootTest instead if login redirects
        if (a.type() == StoryAnalyzer.ScreenType.LOGIN) {
            body = """
                    package %s;

                    import org.junit.jupiter.api.Test;
                    import org.springframework.beans.factory.annotation.Autowired;
                    import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
                    import org.springframework.boot.test.context.SpringBootTest;
                    import org.springframework.test.web.servlet.MockMvc;

                    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
                    import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
                    import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

                    @SpringBootTest(classes = com.generated.app.GeneratedAppApplication.class)
                    @AutoConfigureMockMvc
                    class %sControllerTest {

                        @Autowired
                        private MockMvc mockMvc;

                        @Test
                        void showLogin_returnsOk() throws Exception {
                            mockMvc.perform(get("%s"))
                                    .andExpect(status().isOk())
                                    .andExpect(view().name("%s"));
                        }

                        @Test
                        void login_missingFields_showsError() throws Exception {
                            mockMvc.perform(post("%s")
                                            .param("field1", "")
                                            .param("field2", ""))
                                    .andExpect(status().isOk())
                                    .andExpect(model().attributeExists("error"));
                        }

                        @Test
                        void login_valid_redirectsToNextPage() throws Exception {
                            mockMvc.perform(post("%s")
                                            .param("field1", "alice")
                                            .param("field2", "secret"))
                                    .andExpect(status().is3xxRedirection())
                                    .andExpect(redirectedUrl("%s"));
                        }
                    }
                    """.formatted(a.packageName(), a.classPrefix(), route, a.featureName().toLowerCase(),
                    route, route, a.redirectPath() != null ? a.redirectPath() : "/dashboard");
        }

        Files.writeString(testFile, body, StandardCharsets.UTF_8);
        return testFile;
    }

    private List<Path> writeFences(String content, Path projectDir) throws IOException {
        List<Path> paths = new ArrayList<>();
        Matcher m = FILE_BLOCK.matcher(content);
        while (m.find()) {
            Path out = projectDir.resolve(m.group(1).trim().replace('\\', '/'));
            Files.createDirectories(out.getParent());
            Files.writeString(out, m.group(2).strip() + System.lineSeparator(), StandardCharsets.UTF_8);
            paths.add(out);
        }
        return paths;
    }

    private String readMainSources(Path projectDir) throws IOException {
        Path javaRoot = projectDir.resolve("src/main/java");
        if (!Files.exists(javaRoot)) {
            return "(no sources yet)";
        }
        StringBuilder sb = new StringBuilder();
        try (Stream<Path> walk = Files.walk(javaRoot)) {
            walk.filter(p -> p.toString().endsWith(".java")).limit(20).forEach(p -> {
                try {
                    sb.append("// ").append(projectDir.relativize(p)).append('\n');
                    sb.append(Files.readString(p)).append("\n\n");
                } catch (IOException ignored) {
                }
            });
        }
        return sb.toString();
    }
}
