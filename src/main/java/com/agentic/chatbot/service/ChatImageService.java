package com.agentic.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pictures pasted or dropped into the home chat. Each one is checked (a few images, png / jpeg / webp / gif, a size
 * limit, and the real file type from its first bytes, never the name the browser claims) and stored under
 * {@code generated-app/uploads/chat/}, which the app serves as {@code /uploads/chat/...}.
 */
@Service
public class ChatImageService {

    public static final int MAX_IMAGES = 4;
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** A stored picture; {@code base64} is kept so it can be sent on to a vision model. */
    public record Saved(String name, String url, String mimeType, int bytes, String base64) {
    }

    /** What was stored, and a plain-words note for every picture that was refused. */
    public record Result(List<Saved> saved, List<String> problems) {
        public boolean isEmpty() {
            return saved.isEmpty();
        }
    }

    private final Path chatDir;

    public ChatImageService(@Value("${agentic.output-dir:generated-app}") String outputDir) {
        this.chatDir = Path.of(outputDir).toAbsolutePath().normalize().resolve("uploads").resolve("chat");
    }

    /** @param raw the browser's list of {@code {name, type, data}} (data is base64, optionally a data: URL) */
    public Result save(List<?> raw) {
        List<Saved> saved = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        if (raw == null) {
            return new Result(saved, problems);
        }
        int seen = 0;
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            seen++;
            String label = m.get("name") == null || String.valueOf(m.get("name")).isBlank()
                    ? "image " + seen : cleanName(String.valueOf(m.get("name")));
            if (saved.size() >= MAX_IMAGES) {
                problems.add(label + ": only " + MAX_IMAGES + " images per message");
                continue;
            }
            byte[] bytes;
            try {
                bytes = decode(String.valueOf(m.get("data")));
            } catch (IllegalArgumentException e) {
                problems.add(label + ": could not read the image data");
                continue;
            }
            if (bytes.length == 0) {
                problems.add(label + ": the image is empty");
                continue;
            }
            if (bytes.length > MAX_BYTES) {
                problems.add(label + ": larger than " + (MAX_BYTES / (1024 * 1024)) + " MB");
                continue;
            }
            String ext = sniff(bytes);
            if (ext == null) {
                problems.add(label + ": only png, jpeg, webp and gif images are accepted");
                continue;
            }
            try {
                Files.createDirectories(chatDir);
                String file = "chat-" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
                Files.write(chatDir.resolve(file), bytes);
                saved.add(new Saved(label, "/uploads/chat/" + file, mimeFor(ext), bytes.length,
                        Base64.getEncoder().encodeToString(bytes)));
            } catch (IOException e) {
                problems.add(label + ": could not be stored (" + e.getMessage() + ")");
            }
        }
        return new Result(saved, problems);
    }

    private static byte[] decode(String data) {
        String text = data == null ? "" : data.strip();
        int comma = text.indexOf(',');
        if (text.startsWith("data:") && comma > 0) {
            text = text.substring(comma + 1);
        }
        return Base64.getMimeDecoder().decode(text.getBytes(StandardCharsets.US_ASCII));
    }

    /** The real type from the first bytes: png, jpg, gif or webp; null for anything else. */
    static String sniff(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') {
            return "gif";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    static String mimeFor(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private static String cleanName(String name) {
        String n = name.replaceAll("[\\r\\n\\\\/]+", " ").strip();
        return n.length() > 60 ? n.substring(0, 60) : n;
    }
}
