package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.StageDomain;
import com.agentic.chatbot.stage.StageFiles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline Java Developer and Unit Tester output.
 */
public final class CodeTemplates {

    private static final Pattern PUBLIC_METHOD = Pattern.compile(
            "^\\s*public\\s+(?:static\\s+|final\\s+|synchronized\\s+)*([\\w<>\\[\\], ?]+)\\s+(\\w+)\\s*\\(([^)]*)\\)",
            Pattern.MULTILINE);

    private CodeTemplates() {
    }

    /** Java sources (path to code) implementing the stories. */
    public static Map<String, String> developer(StageDomain domain, String stories, String packageOverride) {
        StageSample sample = StageSample.detect(stories, domain);
        return switch (sample) {
            case FUND_TRANSFER -> FundTransferTemplates.code();
            case SHIPMENT_TRACKING -> ShipmentTrackingTemplates.code();
            case GENERIC -> GenericWorkflowTemplates.code(domain,
                    packageOverride == null || packageOverride.isBlank() ? sample.packageName(domain, stories) : packageOverride,
                    sample.featureName(stories));
        };
    }

    public static String packageFor(StageDomain domain, String stories) {
        StageSample sample = StageSample.detect(stories, domain);
        return sample.packageName(domain, stories);
    }

    /**
     * JUnit 5 tests for the given sources. Code from the offline samples gets the full hand-written suites;
     * anything else gets a compilable skeleton per type listing the methods still to test.
     */
    public static Map<String, String> unitTests(StageDomain domain, Map<String, String> sources) {
        String all = String.join("\n", sources.values());
        Map<String, String> tests = new LinkedHashMap<>();
        if (all.contains("class FundTransferService") && FundTransferTemplates.code().keySet().stream()
                .allMatch(p -> sources.keySet().stream().anyMatch(s -> s.endsWith(p.substring(p.lastIndexOf('/')))))) {
            tests.putAll(FundTransferTemplates.tests());
            return tests;
        }
        if (all.contains("class ShipmentTrackingService") && all.contains("enum ShipmentStatus")) {
            tests.putAll(ShipmentTrackingTemplates.tests());
            return tests;
        }
        for (String code : sources.values()) {
            if (code.contains(GenericWorkflowTemplates.MARKER) && code.matches("(?s).*public class \\w+Service\\b.*")) {
                String pkg = StageFiles.packageOf(code).orElse("");
                String type = StageFiles.primaryType(code).orElse("");
                if (type.endsWith("Service")) {
                    tests.putAll(GenericWorkflowTemplates.tests(domain, pkg, type.substring(0, type.length() - "Service".length())));
                    return tests;
                }
            }
        }
        for (String code : sources.values()) {
            skeleton(code).ifPresent(e -> tests.put(e.getKey(), e.getValue()));
        }
        return tests;
    }

    private static java.util.Optional<Map.Entry<String, String>> skeleton(String code) {
        String pkg = StageFiles.packageOf(code).orElse("");
        java.util.Optional<String> typeName = StageFiles.primaryType(code);
        if (typeName.isEmpty()) {
            return java.util.Optional.empty();
        }
        String type = typeName.get();
        boolean isEnum = Pattern.compile("\\benum\\s+" + type + "\\b").matcher(code).find();
        boolean isClass = Pattern.compile("\\bclass\\s+" + type + "\\b").matcher(code).find()
                && !Pattern.compile("\\babstract\\s+class\\s+" + type + "\\b").matcher(code).find();
        boolean hasCtor = Pattern.compile("\\b" + type + "\\s*\\(").matcher(code.replaceFirst("class\\s+" + type, "")).find();
        boolean noArgCtor = Pattern.compile("public\\s+" + type + "\\s*\\(\\s*\\)").matcher(code).find();
        boolean exception = code.matches("(?s).*class\\s+" + type + "\\s+extends\\s+\\w*(Exception|Error)\\b.*");

        List<String> methods = new ArrayList<>();
        Matcher m = PUBLIC_METHOD.matcher(code);
        while (m.find()) {
            if (!m.group(2).equals(type)) {
                methods.add(m.group(1).trim() + " " + m.group(2) + "(" + m.group(3).trim() + ")");
            }
        }

        StringBuilder sb = new StringBuilder();
        if (!pkg.isBlank()) {
            sb.append("package ").append(pkg).append(";\n\n");
        }
        sb.append("import org.junit.jupiter.api.Test;\n\n");
        sb.append("import static org.junit.jupiter.api.Assertions.assertNotNull;\n");
        if (isEnum) {
            sb.append("import static org.junit.jupiter.api.Assertions.assertTrue;\n");
        }
        sb.append("\n/**\n * Unit test skeleton for {@link ").append(type).append("} generated offline by BOT.\n")
                .append(" * Connect an AI model (Ollama or OpenAI) for full behavioural tests, or complete the TODOs below.\n */\n");
        sb.append("class ").append(type).append("Test {\n\n");
        sb.append("    @Test\n    void typeIsAvailable() {\n        assertNotNull(").append(type).append(".class);\n    }\n");
        if (isEnum) {
            sb.append("\n    @Test\n    void declaresConstants() {\n        assertTrue(").append(type).append(".values().length > 0);\n    }\n");
        } else if (isClass && !exception && (noArgCtor || !hasCtor)) {
            sb.append("\n    @Test\n    void canBeCreated() {\n        assertNotNull(new ").append(type).append("());\n    }\n");
        }
        if (!methods.isEmpty()) {
            sb.append("\n    // TODO: test each public method: happy path, invalid input and edge cases.\n");
            for (String method : methods) {
                sb.append("    //   ").append(method).append('\n');
            }
        }
        sb.append("}\n");
        String path = StageFiles.javaPath("src/test/java", pkg, type + "Test");
        return java.util.Optional.of(Map.entry(path, sb.toString()));
    }

    /** Maven build for the generated code and its unit tests. */
    public static String pom(String groupId, String artifactId) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>GROUP</groupId>
                    <artifactId>ARTIFACT</artifactId>
                    <version>1.0.0</version>
                    <description>Generated by BOT stage bots. Run: mvn test</description>

                    <properties>
                        <maven.compiler.release>17</maven.compiler.release>
                        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.11.4</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-surefire-plugin</artifactId>
                                <version>3.5.2</version>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.replace("GROUP", groupId).replace("ARTIFACT", artifactId);
    }
}
