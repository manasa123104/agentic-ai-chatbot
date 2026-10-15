package com.agentic.chatbot.service;

import com.agentic.chatbot.llm.LlmClient;
import com.agentic.chatbot.model.ConversionJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryConversionServiceTest {

    @TempDir
    Path temp;

    /** Echoes each class as a Python class so the pipeline can be checked without a real model. */
    private static class FakeLlm implements LlmClient {
        final List<String> prompts = new CopyOnWriteArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            prompts.add(userPrompt);
            if (systemPrompt.contains("business people")) {
                return "Summary: Calculates order totals.\nBusiness rules:\n1. Orders over 100 get 10% off.";
            }
            Matcher m = Pattern.compile("class (\\w+)").matcher(userPrompt.substring(userPrompt.indexOf("=== SOURCE CODE ===")));
            String name = m.find() ? m.group(1) : "Part";
            return "=== NOTES ===\nConverted " + name + ".\n=== CODE ===\nclass " + name + ":\n    pass\n";
        }

        @Override
        public String describe() {
            return "fake model";
        }
    }

    private RepositoryConversionService service(LlmClient llm) {
        CodeConversionService converter = new CodeConversionService(new CodeSampleLibrary(), llm,
                temp.resolve("generated").toString());
        return new RepositoryConversionService(converter, new GitService("", "BOT", "bot@example.com"),
                temp.resolve("jobs").toString(), 300, 2048, true);
    }

    private static ConversionJob await(RepositoryConversionService service, String id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            ConversionJob job = service.get(id).orElseThrow();
            if (job.getStatus() == ConversionJob.Status.DONE || job.getStatus() == ConversionJob.Status.FAILED) {
                return job;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("job did not finish");
    }

    private static byte[] zip(String... namesAndContents) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (int i = 0; i < namesAndContents.length; i += 2) {
                zip.putNextEntry(new ZipEntry(namesAndContents[i]));
                zip.write(namesAndContents[i + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    @Test
    void convertsAZippedProjectRecursivelyWithLinkedImports() throws Exception {
        FakeLlm llm = new FakeLlm();
        RepositoryConversionService service = service(llm);
        byte[] zip = zip(
                "shop/pom.xml", "<project/>",
                "shop/src/main/resources/app.properties", "discount=10",
                "shop/src/main/java/com/shop/App.java",
                "package com.shop;\nimport com.shop.billing.Billing;\npublic class App {\n"
                        + "  public static void main(String[] a) { System.out.println(new Billing().total(120)); }\n}\n",
                "shop/src/main/java/com/shop/billing/Billing.java",
                "package com.shop.billing;\npublic class Billing {\n  public double total(double v) { return v > 100 ? v * 0.9 : v; }\n}\n");

        ConversionJob job = service.startFromUploads(
                List.of(new MockMultipartFile("files", "shop.zip", "application/zip", zip)), "shop",
                new RepositoryConversionService.Options("auto", "python", null, true, true));
        job = await(service, job.getId());

        assertEquals(ConversionJob.Status.DONE, job.getStatus(), job.getMessage());
        assertEquals(2, job.getTotalFiles());
        assertEquals("src/main/java/com/shop/billing/Billing.java", job.getFiles().get(0).sourcePath());
        assertEquals("com/shop/billing/billing.py", job.getFiles().get(0).targetPath());
        assertTrue(job.getFiles().get(0).businessLogic().contains("10% off"));
        assertTrue(job.getSkipped().stream().anyMatch(s -> s.startsWith("pom.xml")));

        String appPrompt = llm.prompts.stream().filter(p -> p.contains("public class App")).findFirst().orElseThrow();
        assertTrue(appPrompt.contains("from com.shop.billing.billing import Billing"), appPrompt);
        assertTrue(appPrompt.contains("class Billing:"), "dependency signatures are passed on");

        Path out = service.outputDir(job.getId());
        assertEquals("class Billing:\n    pass\n", Files.readString(out.resolve("com/shop/billing/billing.py")));
        assertTrue(Files.exists(out.resolve("com/shop/__init__.py")));
        assertTrue(Files.exists(out.resolve("src/main/resources/app.properties")));
        assertTrue(Files.readString(out.resolve("BUSINESS_LOGIC.md")).contains("Orders over 100"));
        assertTrue(Files.readString(out.resolve("CONVERSION_REPORT.md")).contains("com/shop/app.py"));

        service.saveFile(job.getId(), "com/shop/app.py", "print('edited')\n");
        assertEquals("print('edited')\n", service.readFile(job.getId(), "output", "com/shop/app.py"));
        assertTrue(service.readFile(job.getId(), "source", "src/main/java/com/shop/App.java").contains("class App"));

        ByteArrayOutputStream zipped = new ByteArrayOutputStream();
        service.writeZip(job.getId(), zipped);
        assertTrue(zipped.size() > 200);
    }

    @Test
    void largePastedCodeIsConvertedInParts() throws Exception {
        FakeLlm llm = new FakeLlm();
        CodeConversionService converter = new CodeConversionService(new CodeSampleLibrary(), llm,
                temp.resolve("generated").toString(), 1_000);
        RepositoryConversionService service = new RepositoryConversionService(converter,
                new GitService("", "BOT", "bot@example.com"), temp.resolve("jobs").toString(), 300, 2048, true);
        StringBuilder code = new StringBuilder("public class Ledger {\n");
        for (int i = 0; i < 60; i++) {
            code.append("    public int rule").append(i).append("(int v) {\n        return v + ").append(i).append(";\n    }\n\n");
        }
        code.append("}\n");

        ConversionJob job = service.startFromText(null, code.toString(),
                new RepositoryConversionService.Options("java", "python", null, false, false));
        job = await(service, job.getId());

        assertEquals(ConversionJob.Status.DONE, job.getStatus(), job.getMessage());
        assertEquals("ledger.py", job.getFiles().get(0).targetPath());
        assertTrue(job.getFiles().get(0).parts() > 2);
        assertTrue(llm.prompts.stream().anyMatch(p -> p.contains("Output ONLY the converted members")));
    }

    @Test
    void rejectsPathsThatEscapeTheJobFolder() throws Exception {
        RepositoryConversionService service = service(new FakeLlm());
        byte[] evil = zip("../../evil.java", "class Evil {}");
        assertThrows(IllegalArgumentException.class, () -> service.startFromUploads(
                List.of(new MockMultipartFile("files", "evil.zip", "application/zip", evil)), "",
                new RepositoryConversionService.Options("auto", "python", null, false, false)));
        assertFalse(Files.exists(temp.resolve("evil.java")));
        assertThrows(IllegalArgumentException.class,
                () -> RepositoryConversionService.safeResolve(temp, "../outside.txt"));
    }

    @Test
    void missingSubfolderMessageListsTheFoldersThatExist(@TempDir Path repo) throws Exception {
        Files.createDirectories(repo.resolve("complete/src"));
        Files.createDirectories(repo.resolve("initial"));
        Files.createDirectories(repo.resolve(".git"));
        String top = RepositoryConversionService.missingFolderMessage(repo, "src/main/java");
        assertTrue(top.contains("does not exist") && top.contains("complete, initial") && !top.contains(".git"), top);
        String nested = RepositoryConversionService.missingFolderMessage(repo, "complete/app");
        assertTrue(nested.contains("\"complete\"") && nested.contains("src"), nested);
    }}
