package com.agentic.chatbot.stage;

import com.agentic.chatbot.stage.StageResponse.StageFile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageFilesTest {

    @Test
    void bundleRoundTripsThroughJavaSources() {
        String bundle = StageFiles.bundle(List.of(
                new StageFile("src/main/java/a/b/One.java", "package a.b;\n\npublic class One {}\n"),
                new StageFile("src/main/java/a/b/Two.java", "package a.b;\n\npublic record Two(int x) {}\n")));

        Map<String, String> files = StageFiles.javaSources(bundle, "src/main/java", null);

        assertEquals(List.of("src/main/java/a/b/One.java", "src/main/java/a/b/Two.java"), List.copyOf(files.keySet()));
        assertTrue(files.get("src/main/java/a/b/Two.java").contains("record Two"));
    }

    @Test
    void pastedCodeWithoutMarkersIsSplitPerPackageLineAndGetsTheDefaultPackage() {
        Map<String, String> pasted = StageFiles.javaSources("""
                package x;
                public class A {}
                package x;
                class B {}
                """, "src/main/java", null);
        assertEquals(List.of("src/main/java/x/A.java", "src/main/java/x/B.java"), List.copyOf(pasted.keySet()));

        Map<String, String> fenced = StageFiles.javaSources("Here you go:\n```java\npublic class C {}\n```", "src/test/java", "p.q");
        assertTrue(fenced.get("src/test/java/p/q/C.java").startsWith("package p.q;"));
    }

    @Test
    void addsMissingJdkImportsOnly() {
        String fixed = StageFiles.addMissingJdkImports("""
                package a;

                import java.util.List;

                // a List of BigDecimal amounts dated with LocalDate
                class T {
                    List<BigDecimal> amounts;
                    String label = "Instant";
                }
                """);
        assertTrue(fixed.contains("import java.math.BigDecimal;"));
        assertFalse(fixed.contains("import java.time.LocalDate;"), "comment-only mentions are ignored");
        assertFalse(fixed.contains("import java.time.Instant;"), "string-only mentions are ignored");
        assertEquals(1, fixed.split("import java.util.List;", -1).length - 1);
        assertTrue(fixed.indexOf("package a;") < fixed.indexOf("import java.math.BigDecimal;"));
    }

    @Test
    void collapsesStepsALoopingModelRepeated() {
        String looped = """
                Scenario: View shipment
                  Given a shipment
                  Then I see the ETA
                  And the page works in all time zones
                  And the page works in all time zones
                  And the page works in all time zones
                Scenario: Another
                  Given a shipment
                """;
        String collapsed = StageFiles.collapseRepeats(looped);
        assertEquals(1, collapsed.split("all time zones", -1).length - 1);
        assertEquals(2, collapsed.split("Given a shipment", -1).length - 1, "same step in another scenario is kept");
    }

    @Test
    void relativePathsCannotEscape() {
        assertEquals("etc/passwd", StageFiles.safeRelative("../../etc/passwd"));
        assertEquals("Windows/x.txt", StageFiles.safeRelative("C:\\Windows\\x.txt"));
    }
}
