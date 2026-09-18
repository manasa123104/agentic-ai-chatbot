package com.agentic.chatbot.service;

import com.agentic.chatbot.model.SiteStyle;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class SiteStyleService {

    private final Path outputDir;
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public SiteStyleService(@Value("${agentic.output-dir:generated-app}") String outputDir) {
        this.outputDir = Path.of(outputDir).toAbsolutePath().normalize();
    }

    public Path styleFile() {
        return outputDir.resolve("SITE_STYLE.json");
    }

    public SiteStyle load() {
        try {
            Path f = styleFile();
            if (Files.exists(f)) {
                return mapper.readValue(Files.readString(f), SiteStyle.class);
            }
        } catch (Exception ignored) {
        }
        return new SiteStyle();
    }

    public void save(SiteStyle style) throws IOException {
        Files.createDirectories(outputDir);
        mapper.writeValue(styleFile().toFile(), style);
    }

    public String storeUpload(MultipartFile file, String kind) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        Path uploads = outputDir.resolve("uploads");
        Files.createDirectories(uploads);
        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) {
            ext = original.substring(dot);
        }
        String name = kind + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = uploads.resolve(name);
        file.transferTo(dest.toFile());
        return "/uploads/" + name;
    }

    public Path uploadsDir() {
        return outputDir.resolve("uploads");
    }

    public Path outputDir() {
        return outputDir;
    }
}
