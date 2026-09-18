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

@Component
public class ScreenCodeGenerator {

    private static final Pattern FILE_BLOCK = Pattern.compile(
            "(?s)```(?:java|html|xml|thymeleaf)?\\s*\\n?//\\s*FILE:\\s*(.+?)\\n(.*?)```");

    private final LlmClient llmClient;

    public ScreenCodeGenerator(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public List<Path> generate(String userStory, Path projectDir) throws IOException {
        StoryAnalyzer.Analysis analysis = StoryAnalyzer.analyze(userStory);
        List<Path> written = new ArrayList<>();

        if (llmClient.isAvailable()) {
            try {
                String system = """
                        You are a senior Java Spring Boot engineer.
                        Generate a complete minimal web screen for the user story.
                        Output ONLY markdown code fences. Each fence must start with:
                        // FILE: relative/path/from/project/root
                        Generate:
                        1) A Spring MVC @Controller under src/main/java/...
                        2) A Thymeleaf HTML template under src/main/resources/templates/
                        3) A simple DTO/model if needed
                        Use package com.generated.app
                        Keep code production-ready but minimal. No explanations outside fences.
                        """;
                String content = llmClient.complete(system, "User story:\n" + userStory);
                written.addAll(writeFromLlmFences(content, projectDir));
                if (!written.isEmpty()) {
                    ensurePom(projectDir, written);
                    return written;
                }
            } catch (Exception ignored) {
                // fall through to offline templates
            }
        }

        written.addAll(writeOfflineTemplates(analysis, userStory, projectDir));
        ensurePom(projectDir, written);
        return written;
    }

    private List<Path> writeFromLlmFences(String content, Path projectDir) throws IOException {
        List<Path> paths = new ArrayList<>();
        Matcher m = FILE_BLOCK.matcher(content);
        while (m.find()) {
            String rel = m.group(1).trim().replace('\\', '/');
            String body = m.group(2);
            Path out = projectDir.resolve(rel);
            Files.createDirectories(out.getParent());
            Files.writeString(out, body.strip() + System.lineSeparator(), StandardCharsets.UTF_8);
            paths.add(out);
        }
        return paths;
    }

    private List<Path> writeOfflineTemplates(StoryAnalyzer.Analysis a, String userStory, Path projectDir)
            throws IOException {
        List<Path> paths = new ArrayList<>();
        String pkgPath = a.packageName().replace('.', '/');
        Path controller = projectDir.resolve("src/main/java/" + pkgPath + "/" + a.classPrefix() + "Controller.java");
        Path template = projectDir.resolve("src/main/resources/templates/" + a.featureName().toLowerCase() + ".html");
        Path dto = projectDir.resolve("src/main/java/" + pkgPath + "/" + a.classPrefix() + "Form.java");
        Path app = projectDir.resolve("src/main/java/com/generated/app/GeneratedAppApplication.java");

        Files.createDirectories(controller.getParent());
        Files.createDirectories(template.getParent());
        Files.createDirectories(app.getParent());

        Files.writeString(app, """
                package com.generated.app;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication(scanBasePackages = "com.generated")
                public class GeneratedAppApplication {
                    public static void main(String[] args) {
                        SpringApplication.run(GeneratedAppApplication.class, args);
                    }
                }
                """, StandardCharsets.UTF_8);
        paths.add(app);

        String route = "/" + a.featureName().toLowerCase().replaceAll("[^a-z0-9-]", "");
        String view = a.featureName().toLowerCase();

        // Big multi-page company website
        if (wantsBigSite(userStory, a)) {
            return writeBigSite(a, userStory, projectDir, paths);
        }

        Files.writeString(dto, """
                package %s;

                public class %sForm {
                    private String field1;
                    private String field2;
                    private String field3;
                    private String field4;

                    public String getField1() { return field1; }
                    public void setField1(String field1) { this.field1 = field1; }
                    public String getField2() { return field2; }
                    public void setField2(String field2) { this.field2 = field2; }
                    public String getField3() { return field3; }
                    public void setField3(String field3) { this.field3 = field3; }
                    public String getField4() { return field4; }
                    public void setField4(String field4) { this.field4 = field4; }
                }
                """.formatted(a.packageName(), a.classPrefix()), StandardCharsets.UTF_8);
        paths.add(dto);

        Files.writeString(controller, buildController(a, route, view, userStory), StandardCharsets.UTF_8);
        paths.add(controller);

        Files.writeString(template, buildHtml(a, route, userStory), StandardCharsets.UTF_8);
        paths.add(template);

        // Destination confirmation page (never overwrite the main feature page)
        if (a.redirectPath() != null && !a.redirectPath().isBlank()
                && !a.redirectPath().equalsIgnoreCase(a.pagePath())) {
            String destView = viewNameFromPath(a.redirectPath());
            Path destTemplate = projectDir.resolve("src/main/resources/templates/" + destView + ".html");
            Files.writeString(destTemplate, CompanyWebTemplates.confirmationFor(a, route), StandardCharsets.UTF_8);
            paths.add(destTemplate);
        } else if (a.type() != StoryAnalyzer.ScreenType.LOGIN && a.type() != StoryAnalyzer.ScreenType.REGISTER) {
            // Success page for form-style stories
            Path thanks = projectDir.resolve("src/main/resources/templates/" + view + "-success.html");
            Files.writeString(thanks, CompanyWebTemplates.confirmationFor(a, route), StandardCharsets.UTF_8);
            paths.add(thanks);
        }

        // Google / company default theme for the editor
        Path styleJson = projectDir.resolve("SITE_STYLE.json");
        Files.writeString(styleJson, """
                {
                  "pageTitle": "%s",
                  "heading": "%s",
                  "subtitle": "%s",
                  "backgroundColor": "#f8fafc",
                  "cardColor": "#ffffff",
                  "accentColor": "#1a73e8",
                  "textColor": "#202124",
                  "footerColor": "#f8f9fa",
                  "footerText": "© 2026 Company · Privacy · Terms",
                  "fontFamily": "Inter, Roboto, Arial, sans-serif",
                  "fontSize": "16px",
                  "headingSize": "2rem",
                  "logoUrl": "",
                  "heroImageUrl": "",
                  "backgroundImageUrl": "",
                  "showGoogleSignIn": %s,
                  "showFooter": true
                }
                """.formatted(
                escapeJava(a.headline()),
                escapeJava(a.headline()),
                escapeJava(a.domain() == StoryAnalyzer.Domain.LOGIN ? "to continue to your workspace" : "Generated from your user story"),
                a.domain() == StoryAnalyzer.Domain.LOGIN || a.domain() == StoryAnalyzer.Domain.REGISTER
        ), StandardCharsets.UTF_8);
        paths.add(styleJson);

        Path meta = projectDir.resolve("SITE_META.json");
        Files.writeString(meta, """
                {
                  "pagePath": "%s",
                  "redirectPath": %s,
                  "featureName": "%s",
                  "previewUrl": "/site%s"
                }
                """.formatted(
                a.pagePath(),
                a.redirectPath() == null ? "null" : "\"" + a.redirectPath() + "\"",
                a.featureName(),
                a.pagePath()
        ), StandardCharsets.UTF_8);
        paths.add(meta);

        Path readme = projectDir.resolve("USER_STORY.md");
        Files.writeString(readme, "# Generated from user story\n\n" + userStory + "\n", StandardCharsets.UTF_8);
        paths.add(readme);

        return paths;
    }

    private static boolean wantsBigSite(String userStory, StoryAnalyzer.Analysis a) {
        if (a.type() == StoryAnalyzer.ScreenType.LOGIN || a.type() == StoryAnalyzer.ScreenType.REGISTER) {
            return false;
        }
        String s = userStory == null ? "" : userStory.toLowerCase();
        if (s.contains("big site") || s.contains("full website") || s.contains("full site")
                || s.contains("multi-page") || s.contains("entire website") || s.contains("complete website")
                || s.contains("corporate site") || s.contains("company website") || s.contains("marketing website")) {
            return true;
        }
        return a.domain() == StoryAnalyzer.Domain.LANDING;
    }

    private List<Path> writeBigSite(
            StoryAnalyzer.Analysis a,
            String userStory,
            Path projectDir,
            List<Path> paths) throws IOException {
        String brand = a.classPrefix();
        if (brand.equalsIgnoreCase("FeatureScreen") || brand.equalsIgnoreCase("CompanyLanding")
                || brand.length() < 3) {
            brand = "NovaTech";
        }
        // Prefer a readable brand from the story
        if (userStory != null) {
            var m = java.util.regex.Pattern.compile("(?i)\\b(?:for|called|named)\\s+([A-Z][A-Za-z0-9]+)").matcher(userStory);
            if (m.find()) {
                brand = m.group(1);
            }
        }

        var pages = BigSiteTemplates.generate(brand, userStory);
        Path templatesDir = projectDir.resolve("src/main/resources/templates");
        Files.createDirectories(templatesDir);
        StringBuilder pageList = new StringBuilder();
        for (var e : pages.entrySet()) {
            Path out = templatesDir.resolve(e.getKey() + ".html");
            Files.writeString(out, e.getValue(), StandardCharsets.UTF_8);
            paths.add(out);
            if (!pageList.isEmpty()) {
                pageList.append(", ");
            }
            pageList.append("\"").append(e.getKey()).append("\"");
        }

        // Contact success page
        Path contactSuccess = templatesDir.resolve("contact-success.html");
        Files.writeString(contactSuccess, CompanyWebTemplates.confirmationPage(
                "Message sent",
                "Thanks for contacting " + brand + ". We’ll reply within one business day.",
                "/home",
                new String[]{"Ticket created", "Sales notified", "Check your inbox"}
        ), StandardCharsets.UTF_8);
        paths.add(contactSuccess);

        String pkg = a.packageName();
        String pkgPath = pkg.replace('.', '/');
        Path controller = projectDir.resolve("src/main/java/" + pkgPath + "/" + a.classPrefix() + "Controller.java");
        Files.createDirectories(controller.getParent());
        Files.writeString(controller, """
                package %s;

                import org.springframework.stereotype.Controller;
                import org.springframework.ui.Model;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestParam;

                @Controller
                public class %sController {
                    @GetMapping({"/", "/home"}) public String home() { return "home"; }
                    @GetMapping("/about") public String about() { return "about"; }
                    @GetMapping("/services") public String services() { return "services"; }
                    @GetMapping("/pricing") public String pricing() { return "pricing"; }
                    @GetMapping("/blog") public String blog() { return "blog"; }
                    @GetMapping("/careers") public String careers() { return "careers"; }
                    @GetMapping("/contact") public String contact() { return "contact"; }
                    @PostMapping("/contact")
                    public String contactSubmit(@RequestParam(required=false) String field1,
                                               @RequestParam(required=false) String field2,
                                               @RequestParam(required=false) String field3,
                                               Model model) {
                        model.addAttribute("title", "Message sent");
                        model.addAttribute("message", "Thanks — we received your message.");
                        return "redirect:/contact-success";
                    }
                    @GetMapping("/contact-success")
                    public String contactSuccess(Model model) {
                        model.addAttribute("title", "Message sent");
                        model.addAttribute("message", "Thanks — we received your message.");
                        return "contact-success";
                    }
                }
                """.formatted(pkg, a.classPrefix()), StandardCharsets.UTF_8);
        paths.add(controller);

        Path styleJson = projectDir.resolve("SITE_STYLE.json");
        Files.writeString(styleJson, """
                {
                  "pageTitle": "%s",
                  "heading": "%s",
                  "subtitle": "Full multi-page company website",
                  "backgroundColor": "#ffffff",
                  "cardColor": "#ffffff",
                  "accentColor": "#1a73e8",
                  "textColor": "#0f172a",
                  "footerColor": "#0b1220",
                  "footerText": "© 2026 %s",
                  "fontFamily": "Inter, Arial, sans-serif",
                  "fontSize": "16px",
                  "headingSize": "2rem",
                  "logoUrl": "",
                  "heroImageUrl": "",
                  "backgroundImageUrl": "",
                  "showGoogleSignIn": false,
                  "showFooter": true
                }
                """.formatted(escapeJava(brand), escapeJava(brand), escapeJava(brand)), StandardCharsets.UTF_8);
        paths.add(styleJson);

        Path meta = projectDir.resolve("SITE_META.json");
        Files.writeString(meta, """
                {
                  "pagePath": "/home",
                  "redirectPath": null,
                  "featureName": "home",
                  "previewUrl": "/site/home",
                  "bigSite": true,
                  "pages": [%s]
                }
                """.formatted(pageList), StandardCharsets.UTF_8);
        paths.add(meta);

        Path readme = projectDir.resolve("USER_STORY.md");
        Files.writeString(readme, "# Big site generated\n\n" + userStory + "\n\nPages: home, about, services, pricing, blog, careers, contact\n",
                StandardCharsets.UTF_8);
        paths.add(readme);

        ensurePom(projectDir, paths);
        return paths;
    }

    private static String redirectOrDefault(StoryAnalyzer.Analysis a) {
        return a.redirectPath() == null || a.redirectPath().isBlank() ? "/dashboard" : a.redirectPath();
    }

    private static String viewNameFromPath(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        return p.replaceAll("[^a-zA-Z0-9-]", "-").toLowerCase();
    }

    private String buildController(StoryAnalyzer.Analysis a, String route, String view, String userStory) {
        return switch (a.type()) {
            case LOGIN -> """
                    package %s;

                    import org.springframework.stereotype.Controller;
                    import org.springframework.ui.Model;
                    import org.springframework.web.bind.annotation.GetMapping;
                    import org.springframework.web.bind.annotation.ModelAttribute;
                    import org.springframework.web.bind.annotation.PostMapping;

                    /**
                     * Generated from user story.
                     * %s
                     */
                    @Controller
                    public class %sController {

                        @GetMapping("%s")
                        public String show(Model model) {
                            model.addAttribute("form", new %sForm());
                            model.addAttribute("title", "Login");
                            return "%s";
                        }

                        @PostMapping("%s")
                        public String submit(@ModelAttribute %sForm form, Model model) {
                            if (form.getField1() == null || form.getField1().isBlank()
                                    || form.getField2() == null || form.getField2().isBlank()) {
                                model.addAttribute("error", "Username and password are required");
                                model.addAttribute("form", form);
                                model.addAttribute("title", "Login");
                                return "%s";
                            }
                            return "redirect:%s";
                        }

                        @GetMapping("%s")
                        public String destination(Model model) {
                            model.addAttribute("title", "%s");
                            model.addAttribute("message", "Welcome! You were redirected as instructed.");
                            return "%s";
                        }
                    }
                    """.formatted(a.packageName(), escapeComment(userStory), a.classPrefix(), route,
                    a.classPrefix(), view, route, a.classPrefix(), view,
                    redirectOrDefault(a), redirectOrDefault(a),
                    StoryAnalyzer.toPascalCase(redirectOrDefault(a).substring(1)),
                    viewNameFromPath(redirectOrDefault(a)));
            default -> """
                    package %s;

                    import org.springframework.stereotype.Controller;
                    import org.springframework.ui.Model;
                    import org.springframework.web.bind.annotation.GetMapping;
                    import org.springframework.web.bind.annotation.ModelAttribute;
                    import org.springframework.web.bind.annotation.PostMapping;

                    /**
                     * Generated from user story.
                     * %s
                     */
                    @Controller
                    public class %sController {

                        @GetMapping("%s")
                        public String show(Model model) {
                            model.addAttribute("form", new %sForm());
                            model.addAttribute("title", "%s");
                            model.addAttribute("story", "%s");
                            return "%s";
                        }

                        @PostMapping("%s")
                        public String submit(@ModelAttribute %sForm form, Model model) {
                            model.addAttribute("title", "%s");
                            model.addAttribute("form", form);
                            model.addAttribute("success", true);
                            model.addAttribute("message", "Submitted from your generated webpage.");
                            %s
                        }

                        @GetMapping("%s-success")
                        public String success(Model model) {
                            model.addAttribute("title", "%s");
                            model.addAttribute("message", "Your request was completed.");
                            return "%s-success";
                        }
                    }
                    """.formatted(a.packageName(), escapeComment(userStory), a.classPrefix(), route,
                    a.classPrefix(), escapeJava(a.headline()), escapeJava(userStory), view, route, a.classPrefix(),
                    escapeJava(a.headline()),
                    a.redirectPath() == null || a.redirectPath().isBlank()
                            ? "return \"redirect:" + route + "-success\";"
                            : "return \"redirect:" + a.redirectPath() + "\";",
                    route, escapeJava(a.headline() + " — done"), view);
        };
    }

    private String buildHtml(StoryAnalyzer.Analysis a, String route, String userStory) {
        return CompanyWebTemplates.pageFor(a, escapeHtml(userStory), route);
    }

    private void ensurePom(Path projectDir, List<Path> written) throws IOException {
        Path pom = projectDir.resolve("pom.xml");
        if (Files.exists(pom)) {
            return;
        }
        String pomXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.4.3</version>
                        <relativePath/>
                    </parent>
                    <groupId>com.generated</groupId>
                    <artifactId>generated-app</artifactId>
                    <version>1.0.0</version>
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-thymeleaf</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-test</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """;
        Files.writeString(pom, pomXml, StandardCharsets.UTF_8);
        written.add(pom);

        Path appYml = projectDir.resolve("src/main/resources/application.yml");
        Files.createDirectories(appYml.getParent());
        Files.writeString(appYml, "server:\n  port: 8090\n", StandardCharsets.UTF_8);
        written.add(appYml);

        // dashboard template for login flows
        Path dash = projectDir.resolve("src/main/resources/templates/dashboard.html");
        if (!Files.exists(dash)) {
            Files.writeString(dash, CompanyWebTemplates.companyDashboardPage("/login"), StandardCharsets.UTF_8);
            written.add(dash);
        }
    }

    private static String escapeComment(String s) {
        return s == null ? "" : s.replace("*/", "* /").replace("\r", " ").replace("\n", " ");
    }

    private static String escapeJava(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
