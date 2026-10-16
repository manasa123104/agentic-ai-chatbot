package com.agentic.chatbot.web;

import com.agentic.chatbot.service.SiteCodeService;
import com.agentic.chatbot.service.SiteZipService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Downloads for the generated site: the user stories, one file, the code in the generated language as a ZIP, and the
 * whole site (stories + code + pages) as a ZIP.
 */
@RestController
public class CodeDownloadController {

    private static final String NOTHING = "There is no generated website yet. Ask BOT to create one on the home page first.";

    private final SiteCodeService code;
    private final SiteZipService zips;

    public CodeDownloadController(SiteCodeService code, SiteZipService zips) {
        this.code = code;
        this.zips = zips;
    }

    @GetMapping("/code/download/stories")
    public ResponseEntity<?> stories() throws IOException {
        String md = code.storiesMarkdown();
        if (md == null || md.isBlank()) {
            return missing();
        }
        return attachment(md.getBytes(StandardCharsets.UTF_8), code.baseName() + "-user-stories.md",
                "text/markdown; charset=UTF-8");
    }

    @GetMapping("/code/download/code.zip")
    public ResponseEntity<?> codeZip() throws IOException {
        if (code.describe().isEmpty()) {
            return missing();
        }
        return attachment(code.codeZip(), code.baseName() + "-code.zip", "application/zip");
    }

    /** Stories + code in the generated language + the website pages, in one ZIP (the home chat links /download/site.zip). */
    @GetMapping({"/download/site.zip", "/code/download/site.zip"})
    public ResponseEntity<?> siteZip() {
        if (!zips.hasContent()) {
            return missing();
        }
        return attachment(zips.build(), zips.fileName(), "application/zip");
    }

    @GetMapping("/code/download/file")
    public ResponseEntity<?> file(@RequestParam("file") String file) throws IOException {
        if (!code.isDeliverable(file)) {
            return ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN)
                    .body("That file is not part of the generated site.");
        }
        Path root = code.root().toAbsolutePath().normalize();
        Path resolved = root.resolve(file).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            return ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body("File not found.");
        }
        String name = resolved.getFileName().toString();
        return attachment(Files.readAllBytes(resolved), name, "text/plain; charset=UTF-8");
    }

    private static ResponseEntity<byte[]> attachment(byte[] body, String name, String type) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .header("Cache-Control", "no-store")
                .contentType(MediaType.parseMediaType(type))
                .body(body);
    }

    private static ResponseEntity<String> missing() {
        return ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body(NOTHING);
    }
}
