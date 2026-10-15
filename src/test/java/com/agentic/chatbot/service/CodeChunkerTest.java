package com.agentic.chatbot.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeChunkerTest {

    private static String bigJavaClass(int methods) {
        StringBuilder sb = new StringBuilder("package com.acme;\n\nimport java.util.List;\n\npublic class Billing {\n\n"
                + "    private final String name = \"{ not a brace }\";\n\n");
        for (int i = 0; i < methods; i++) {
            sb.append("    /** Method ").append(i).append(" } in a comment */\n")
                    .append("    public int rule").append(i).append("(int amount) {\n")
                    .append("        if (amount > ").append(i).append(") {\n")
                    .append("            return amount * ").append(i).append("; // } trailing\n")
                    .append("        }\n")
                    .append("        return 0;\n")
                    .append("    }\n\n");
        }
        return sb.append("}\n").toString();
    }

    @Test
    void smallCodeIsOneChunk() {
        List<CodeChunker.Chunk> chunks = CodeChunker.split("class A {}\n", "java", 1000);
        assertEquals(1, chunks.size());
        assertFalse(chunks.get(0).continuesType());
    }

    @Test
    void largeClassSplitsBetweenMethodsWithoutLosingText() {
        String code = bigJavaClass(80);
        List<CodeChunker.Chunk> chunks = CodeChunker.split(code, "java", 1500);

        assertTrue(chunks.size() > 5, "expected several chunks, got " + chunks.size());
        assertEquals(code, String.join("", chunks.stream().map(CodeChunker.Chunk::text).toList()));
        for (int i = 1; i < chunks.size(); i++) {
            CodeChunker.Chunk chunk = chunks.get(i);
            assertTrue(chunk.text().length() <= 1500);
            assertTrue(chunk.continuesType(), "chunk " + i + " continues the class");
            assertTrue(chunk.text().stripLeading().startsWith("/** Method") || chunk.text().strip().equals("}"),
                    "chunk " + i + " starts at a member: " + chunk.text().substring(0, 40));
        }
    }

    @Test
    void headerStopsAtTheClassBrace() {
        String header = CodeChunker.header(bigJavaClass(3), 2000);
        assertTrue(header.startsWith("package com.acme;"));
        assertTrue(header.endsWith("public class Billing {"));
    }

    @Test
    void pythonSplitsAtDefinitions() {
        StringBuilder sb = new StringBuilder("import math\n\nclass Shapes:\n");
        for (int i = 0; i < 60; i++) {
            sb.append("    def area").append(i).append("(self, r):\n        return math.pi * r * r * ").append(i).append("\n\n");
        }
        List<CodeChunker.Chunk> chunks = CodeChunker.split(sb.toString(), "python", 800);
        assertTrue(chunks.size() > 3);
        for (int i = 1; i < chunks.size(); i++) {
            assertTrue(chunks.get(i).text().startsWith("    def area"), chunks.get(i).text());
            assertTrue(chunks.get(i).continuesType());
        }
    }

    @Test
    void stitchInsertsMembersBeforeTheClosingBrace() {
        String first = "public class Billing {\n    int a() { return 1; }\n}\n";
        String second = "    int b() { return 2; }";
        String third = "    int c() { return 3; }\n";
        String joined = CodeChunker.stitch("java", List.of(first, second, third), true);

        assertEquals("public class Billing {\n    int a() { return 1; }\n\n    int b() { return 2; }\n\n"
                + "    int c() { return 3; }\n}\n", joined);
        assertEquals(0, CodeChunker.braceBalance(joined));
    }

    @Test
    void stitchUnwrapsAClassTheModelRepeatedAndHoistsImports() {
        String first = "import fs from 'fs';\n\nexport class TaxRules {\n    static a() { return 1; }\n}\n";
        String second = "import path from 'path';\nclass TaxRules {\n    static b() { return 2; }\n}\n";
        String third = "    static c() { return 3; }\n}\n\nconsole.log(TaxRules.a());\n";
        String joined = CodeChunker.stitch("javascript", List.of(first, second, third), true);

        assertEquals("import fs from 'fs';\nimport path from 'path';\n\nexport class TaxRules {\n"
                + "    static a() { return 1; }\n\n    static b() { return 2; }\n\n    static c() { return 3; }\n}\n\n"
                + "console.log(TaxRules.a());\n", joined);
    }

    @Test
    void mergeFollowsClassesAcrossPartsAndKeepsTrailingCode() {
        String acc = "export class Ledger {\n    a() { return 1; }\n}\n\nLedger.main();\n";
        acc = CodeChunker.merge("javascript", acc,
                "    b() { return 2; }\n}\n\nexport class TaxRules {\n    static r0() { return 0; }\n}\n", true);
        acc = CodeChunker.merge("javascript", acc, "    static r1() { return 1; }\n", true);

        assertEquals("export class Ledger {\n    a() { return 1; }\n\n    b() { return 2; }\n}\n\nLedger.main();\n\n"
                + "export class TaxRules {\n    static r0() { return 0; }\n\n    static r1() { return 1; }\n}\n", acc + "\n");
        assertEquals(0, CodeChunker.braceBalance(acc));
    }

    @Test
    void mergeAppendsWhenTheModelUsedTopLevelFunctions() {
        String acc = "function rule0(x) {\n    return x;\n}\n";
        assertFalse(CodeChunker.keepsType(acc, "javascript"));
        String joined = CodeChunker.merge("javascript", acc, "function rule1(x) {\n    return x;\n}\n}\n", false);
        assertEquals("function rule0(x) {\n    return x;\n}\n\nfunction rule1(x) {\n    return x;\n}", joined);
    }

    @Test
    void mergeIndentsPythonMethodsIntoTheClassBeforeMainCode() {
        String acc = "class Ledger:\n    def a(self):\n        return 1\n\n\nif __name__ == \"__main__\":\n    print(Ledger().a())\n";
        String joined = CodeChunker.merge("python", acc, "def b(self):\n    return 2\n", true);
        assertEquals("class Ledger:\n    def a(self):\n        return 1\n\n    def b(self):\n        return 2\n\n\n"
                + "if __name__ == \"__main__\":\n    print(Ledger().a())\n", joined);
    }

    @Test
    void finishDropsUnmatchedClosersAndClosesOpenBraces() {
        String extra = "class A {\n    static f() {\n        return 1;\n    }\n}\n}\n}\n";
        assertEquals("class A {\n    static f() {\n        return 1;\n    }\n}\n", CodeChunker.finish("javascript", extra));
        assertEquals("class A {\n    f() {\n    }\n}\n", CodeChunker.finish("javascript", "class A {\n    f() {\n    }"));
        assertEquals("const s = \"}\";\n", CodeChunker.finish("javascript", "const s = \"}\";"));
    }

    @Test
    void splitsPartsIntoFunctionBlocks() {
        String js = "    static a() {\n        return 1;\n    }\n\n    static b() {\n        if (x) { y(); }\n    }\n";
        List<String> blocks = CodeChunker.blocks(js, "javascript");
        assertEquals(2, blocks.size());
        assertEquals(js, String.join("", blocks));
        assertTrue(blocks.get(1).contains("static b()"));

        String py = "    @staticmethod\n    def a():\n        return 1\n\n    def b(self):\n        return 2\n";
        List<String> pyBlocks = CodeChunker.blocks(py, "python");
        assertEquals(2, pyBlocks.size());
        assertTrue(pyBlocks.get(0).contains("@staticmethod") && pyBlocks.get(0).contains("def a"));
        assertEquals(py, String.join("", pyBlocks));
    }

    @Test
    void mergeMovesFreeJavaScriptFunctionsOutOfTheClass() {
        String acc = "export class T {\n    static a() {\n        return 1;\n    }\n}\n";
        String part = "    static b() {\n        return 2;\n    }\n\nfunction main() {\n    console.log(T.a() + T.b());\n}\n"
                + "new T().run();\n}\n";
        String merged = CodeChunker.finish("javascript", CodeChunker.merge("javascript", acc, part, true));
        assertEquals("export class T {\n    static a() {\n        return 1;\n    }\n\n    static b() {\n        return 2;\n"
                + "    }\n}\n\nfunction main() {\n    console.log(T.a() + T.b());\n}\nnew T().run();\n", merged);
    }

    @Test
    void mergeRemovesTheContinuedClassRepeatedInsideAPart() {
        String acc = "export class T {\n    static a() {\n        return 1;\n    }\n}\n";
        String part = "    static b() {\n        return 2;\n    }\n\nclass T {\n    static c() {\n        return 3;\n    }\n}\n";
        String merged = CodeChunker.finish("javascript", CodeChunker.merge("javascript", acc, part, true));
        assertEquals("export class T {\n    static a() {\n        return 1;\n    }\n\n    static b() {\n        return 2;\n"
                + "    }\n\n    static c() {\n        return 3;\n    }\n}\n", merged);
    }

    @Test
    void mergePutsANewClassStartedInAPartAfterTheContinuedClass() {
        String acc = "export class Ledger {\n    a() {\n        return 1;\n    }\n}\n";
        String part = "    b() {\n        return 2;\n    }\n}\n\nclass TaxRules {\n    static r0() {\n        return 0;\n    }\n";
        String merged = CodeChunker.finish("javascript", CodeChunker.merge("javascript", acc, part, true));
        assertEquals("export class Ledger {\n    a() {\n        return 1;\n    }\n\n    b() {\n        return 2;\n    }\n}\n\n"
                + "class TaxRules {\n    static r0() {\n        return 0;\n    }\n}\n", merged);
        String next = CodeChunker.merge("javascript", merged, "    static r1() {\n        return 1;\n    }\n", true);
        assertTrue(next.indexOf("static r1()") > next.indexOf("class TaxRules"), next);
    }

    @Test
    void addsTheMissingMainCall() {
        assertTrue(CodeChunker.ensureMainCall("javascript", "function main() {\n}\n").endsWith("\nmain();\n"));
        assertEquals("function main() {\n}\nmain();\n",
                CodeChunker.ensureMainCall("javascript", "function main() {\n}\nmain();\n"));
        assertTrue(CodeChunker.ensureMainCall("javascript", "export class T {\n    static main() {\n    }\n}\n")
                .endsWith("\nT.main();\n"));
        assertTrue(CodeChunker.ensureMainCall("python", "def main():\n    pass\n")
                .endsWith("if __name__ == \"__main__\":\n    main()\n"));
        assertTrue(CodeChunker.ensureMainCall("python", "class T:\n    @staticmethod\n    def main(args):\n        pass\n")
                .endsWith("    T.main([])\n"));
        String guarded = "def main():\n    pass\n\nif __name__ == \"__main__\":\n    main()\n";
        assertEquals(guarded, CodeChunker.ensureMainCall("python", guarded));
    }

    @Test
    void keepsALongMethodWholeInsteadOfCuttingItsBody() {
        StringBuilder sb = new StringBuilder("public class A {\n    void small() {\n        x();\n    }\n\n"
                + "    public static void main(String[] args) {\n");
        for (int i = 0; i < 60; i++) {
            sb.append("        total += compute").append(i).append("(value);\n");
        }
        sb.append("    }\n}\n");
        List<CodeChunker.Chunk> chunks = CodeChunker.split(sb.toString(), "java", 1000);
        assertTrue(chunks.stream().anyMatch(c -> c.text().contains("main(String[] args)") && c.text().contains("compute59")));
    }

    @Test
    void findsTheEnclosingTypeOfAnOffset() {
        String code = "public class A {\n    void x() {}\n}\n\nclass B {\n    void y() {}\n}\n";
        assertEquals("public class A {", CodeChunker.enclosingType(code, code.indexOf("void x")));
        assertEquals("class B {", CodeChunker.enclosingType(code, code.indexOf("void y")));
        assertEquals("", CodeChunker.enclosingType(code, code.indexOf("class B")));
    }

    @Test
    void stitchClosesARubyClassOnce() {
        String joined = CodeChunker.stitch("ruby",
                List.of("class Billing\n  def a\n    1\n  end\nend\n", "  def b\n    2\n  end\n"), true);
        assertEquals("class Billing\n  def a\n    1\n  end\n\n  def b\n    2\n  end\nend\n", joined);
    }

    @Test
    void stitchAppendsTopLevelPythonCode() {
        String joined = CodeChunker.stitch("python", List.of("def a():\n    return 1\n", "def b():\n    return 2"), false);
        assertEquals("def a():\n    return 1\n\ndef b():\n    return 2\n", joined);
    }
}
