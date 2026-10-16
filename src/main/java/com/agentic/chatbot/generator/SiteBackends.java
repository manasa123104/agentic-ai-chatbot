package com.agentic.chatbot.generator;

import com.agentic.chatbot.stage.StageLanguage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Picks the programming language for a generated website and writes a small runnable server for it.
 *
 * <p>The Spring Boot / Thymeleaf project stays the reference app that powers BOT's preview. When the prompt (or the
 * home page language picker) names another language, the same pages are also written to {@code server/<language>/}
 * together with a dependency-light server (standard library or the language's own runtime) so the site can be run
 * in that language. Languages without a server template get the pages plus a README that points to the converter.
 */
public final class SiteBackends {

    /** Written by the home-page language picker: "(backend language: Go)". */
    private static final Pattern EXPLICIT = Pattern.compile("(?i)backend language\\s*[:=]\\s*([A-Za-z#+/]{1,12})");

    private record Rule(StageLanguage language, Pattern pattern) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(StageLanguage.TYPESCRIPT, Pattern.compile("(?i)\\b(typescript|nest\\.?js|deno)\\b")),
            new Rule(StageLanguage.JAVASCRIPT, Pattern.compile("(?i)\\b(javascript|node\\.?js|node|express)\\b")),
            new Rule(StageLanguage.PYTHON, Pattern.compile("(?i)\\b(python|django|flask|fastapi)\\b")),
            new Rule(StageLanguage.GO, Pattern.compile(
                    "(?i)\\b(golang|(?:in|using|with|use|write|written in)\\s+go\\b|go\\s+(?:code|backend|server|website|app|language|lang)\\b)")),
            new Rule(StageLanguage.KOTLIN, Pattern.compile("(?i)\\b(kotlin|ktor)\\b")),
            new Rule(StageLanguage.CSHARP, Pattern.compile("(?i)(\\bc#|\\bcsharp\\b|asp\\.net|\\.net\\b|\\bdotnet\\b)")),
            new Rule(StageLanguage.RUST, Pattern.compile("(?i)\\b(rust|actix|axum)\\b")),
            new Rule(StageLanguage.PHP, Pattern.compile("(?i)\\b(php|laravel|symfony)\\b")),
            new Rule(StageLanguage.RUBY, Pattern.compile("(?i)\\b(ruby|rails|sinatra)\\b")),
            new Rule(StageLanguage.SWIFT, Pattern.compile("(?i)\\b(swift|vapor)\\b")),
            new Rule(StageLanguage.CPP, Pattern.compile("(?i)(\\bc\\+\\+|\\bcpp\\b)")),
            new Rule(StageLanguage.COBOL, Pattern.compile("(?i)\\bcobol\\b")),
            new Rule(StageLanguage.PLSQL, Pattern.compile("(?i)(pl/sql|\\bplsql\\b)")),
            new Rule(StageLanguage.C, Pattern.compile("(?i)\\bc\\s+(?:code|program|language|backend|server)\\b")),
            new Rule(StageLanguage.JAVA, Pattern.compile("(?i)\\b(java|spring(?:\\s+boot)?|thymeleaf)\\b"))
    );

    private SiteBackends() {
    }

    /** Language named in the prompt; Java (the original behaviour) when none is named. */
    public static StageLanguage detect(String story) {
        if (story == null || story.isBlank()) {
            return StageLanguage.JAVA;
        }
        Matcher explicit = EXPLICIT.matcher(story);
        String picked = null;
        while (explicit.find()) {
            picked = explicit.group(1).trim();
        }
        if (picked != null) {
            StageLanguage lang = StageLanguage.from(picked);
            if (lang != StageLanguage.OTHER) {
                return lang;
            }
        }
        StageLanguage best = StageLanguage.JAVA;
        int bestIndex = Integer.MAX_VALUE;
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(story);
            if (m.find() && m.start() < bestIndex) {
                bestIndex = m.start();
                best = rule.language();
            }
        }
        return best;
    }

    /** True when {@link #write} produces a runnable server for the language. */
    public static boolean hasServer(StageLanguage lang) {
        return switch (lang) {
            case PYTHON, JAVASCRIPT, TYPESCRIPT, GO, KOTLIN, CSHARP, RUST, PHP, RUBY -> true;
            default -> false;
        };
    }

    /** The run command shown to the user. */
    public static String runCommand(StageLanguage lang) {
        return switch (lang) {
            case PYTHON -> "cd server/python && python server.py";
            case JAVASCRIPT -> "cd server/javascript && node server.js";
            case TYPESCRIPT -> "cd server/typescript && npm install && npm start";
            case GO -> "cd server/go && go run .";
            case KOTLIN -> "cd server/kotlin && kotlinc server.kt -include-runtime -d server.jar && java -jar server.jar";
            case CSHARP -> "cd server/csharp && dotnet run";
            case RUST -> "cd server/rust && cargo run";
            case PHP -> "cd server/php && php -S localhost:8090 router.php";
            case RUBY -> "cd server/ruby && gem install webrick && ruby server.rb";
            default -> "mvn spring-boot:run";
        };
    }

    /**
     * Writes {@code server/<language>/} under {@code projectDir}: the pages, a server and a README. Java returns
     * nothing (the Spring Boot project is the Java backend). Old {@code server/} folders are removed first.
     */
    public static List<Path> write(StageLanguage lang, Path projectDir, String brand, Map<String, String> pages,
                                   boolean agent) throws IOException {
        Path serverRoot = projectDir.resolve("server");
        deleteTree(serverRoot);
        List<Path> written = new ArrayList<>();
        if (lang == StageLanguage.JAVA) {
            return written;
        }
        Path dir = serverRoot.resolve(lang.id());
        Files.createDirectories(dir.resolve("public"));
        for (var e : pages.entrySet()) {
            put(dir, "public/" + e.getKey() + ".html", e.getValue(), written);
        }
        put(dir, "public/contact-success.html", successPage(brand), written);

        Map<String, String> files = new LinkedHashMap<>(serverFiles(lang, brand));
        files.put("README.md", readme(lang, brand, agent));
        for (var e : files.entrySet()) {
            put(dir, e.getKey(), e.getValue(), written);
        }
        return written;
    }

    private static void put(Path dir, String rel, String content, List<Path> written) throws IOException {
        Path out = dir.resolve(rel);
        Files.createDirectories(out.getParent());
        Files.writeString(out, content, StandardCharsets.UTF_8);
        written.add(out);
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String successPage(String brand) {
        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="UTF-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/>
                <title>Message sent</title>
                <style>body{font-family:system-ui,Arial,sans-serif;margin:0;min-height:100vh;display:grid;place-items:center;background:#f8fafc;color:#0f172a}
                main{max-width:30rem;padding:2rem;text-align:center}a{color:#0f766e;font-weight:600}</style></head>
                <body><main><h1>Message sent</h1><p>Thanks for contacting @@BRAND@@. We will reply within one business day.</p>
                <p><a href="/site/home">Back to home</a></p></main></body></html>
                """.replace("@@BRAND@@", escHtml(brand));
    }

    private static String readme(StageLanguage lang, String brand, boolean agent) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(brand).append(" - ").append(lang.label()).append(" server\n\n");
        sb.append("Generated by BOT. The pages in `public/` are plain HTML, so they work with any server.\n\n");
        if (hasServer(lang)) {
            sb.append("Run it:\n\n```\n").append(runCommand(lang)).append("\n```\n\n");
            sb.append("Then open http://localhost:8090/site/home (set `PORT` to change the port).\n\n");
        } else {
            sb.append("BOT has no ready-made ").append(lang.label()).append(" server template yet. ")
                    .append("Serve the `public/` folder with any web server, or open the Code converter in BOT ")
                    .append("(`/convert`) and convert the Spring Boot controller in `src/main/java` to ")
                    .append(lang.label()).append(".\n\n");
        }
        if (agent) {
            sb.append("The chatbot widget on every page asks BOT's `/api/site-agent` first and falls back to the ")
                    .append("rules built into the page, so it keeps answering when this server runs on its own.\n");
        }
        return sb.toString();
    }

    private static String escHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String clean(String brand) {
        return brand == null ? "" : brand.replace("\"", "").replace("'", "").replace("\\", "");
    }

    // ---------------------------------------------------------------- servers

    private static Map<String, String> serverFiles(StageLanguage lang, String brand) {
        String b = clean(brand);
        Map<String, String> f = new LinkedHashMap<>();
        switch (lang) {
            case PYTHON -> f.put("server.py", PYTHON.replace("@@BRAND@@", b));
            case JAVASCRIPT -> {
                f.put("server.js", NODE.replace("@@BRAND@@", b));
                f.put("package.json", "{\n  \"name\": \"site-server\",\n  \"version\": \"1.0.0\",\n  \"private\": true,\n"
                        + "  \"scripts\": { \"start\": \"node server.js\" }\n}\n");
            }
            case TYPESCRIPT -> {
                f.put("server.ts", TYPESCRIPT.replace("@@BRAND@@", b));
                f.put("package.json", "{\n  \"name\": \"site-server\",\n  \"version\": \"1.0.0\",\n  \"private\": true,\n"
                        + "  \"scripts\": { \"start\": \"tsx server.ts\" },\n"
                        + "  \"devDependencies\": { \"@types/node\": \"^20.0.0\", \"tsx\": \"^4.0.0\", \"typescript\": \"^5.0.0\" }\n}\n");
                f.put("tsconfig.json", "{\n  \"compilerOptions\": {\n    \"target\": \"ES2020\",\n    \"module\": \"commonjs\",\n"
                        + "    \"strict\": true,\n    \"esModuleInterop\": true,\n    \"skipLibCheck\": true\n  }\n}\n");
            }
            case GO -> {
                f.put("main.go", GO.replace("@@BRAND@@", b));
                f.put("go.mod", "module sitesrv\n\ngo 1.21\n");
            }
            case KOTLIN -> f.put("server.kt", KOTLIN.replace("@@BRAND@@", b));
            case CSHARP -> {
                f.put("Program.cs", CSHARP.replace("@@BRAND@@", b));
                f.put("SiteServer.csproj", "<Project Sdk=\"Microsoft.NET.Sdk.Web\">\n  <PropertyGroup>\n"
                        + "    <TargetFramework>net8.0</TargetFramework>\n    <Nullable>enable</Nullable>\n"
                        + "    <ImplicitUsings>enable</ImplicitUsings>\n  </PropertyGroup>\n</Project>\n");
            }
            case RUST -> {
                f.put("src/main.rs", RUST.replace("@@BRAND@@", b));
                f.put("Cargo.toml", "[package]\nname = \"site-server\"\nversion = \"1.0.0\"\nedition = \"2021\"\n\n[dependencies]\n");
            }
            case PHP -> f.put("router.php", PHP.replace("@@BRAND@@", b));
            case RUBY -> f.put("server.rb", RUBY.replace("@@BRAND@@", b));
            default -> {
            }
        }
        return f;
    }

    private static final String PYTHON = """
            # @@BRAND@@ - site server (Python standard library only)
            # Run: python server.py   then open http://localhost:8090/site/home
            import os
            from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
            from pathlib import Path

            PUBLIC = Path(__file__).parent / "public"
            PORT = int(os.environ.get("PORT", "8090"))


            def page_for(path):
                name = path.split("?")[0].strip("/")
                if name.startswith("site/"):
                    name = name[5:]
                return name or "home"


            class Handler(BaseHTTPRequestHandler):
                def _send(self, status, body, headers=None):
                    data = body.encode("utf-8")
                    self.send_response(status)
                    self.send_header("Content-Type", "text/html; charset=utf-8")
                    self.send_header("Content-Length", str(len(data)))
                    for key, value in (headers or {}).items():
                        self.send_header(key, value)
                    self.end_headers()
                    self.wfile.write(data)

                def do_GET(self):
                    name = page_for(self.path)
                    file = PUBLIC / (name + ".html")
                    if not name.replace("-", "").isalnum() or not file.is_file():
                        self._send(404, "<h1>Page not found</h1><p><a href='/site/home'>Home</a></p>")
                        return
                    self._send(200, file.read_text(encoding="utf-8"))

                def do_POST(self):
                    self.rfile.read(int(self.headers.get("Content-Length", 0)))
                    self._send(303, "", {"Location": "/site/contact-success"})


            if __name__ == "__main__":
                print("@@BRAND@@ on http://localhost:%d" % PORT)
                ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
            """;

    private static final String NODE = """
            // @@BRAND@@ - site server (Node.js built-ins only)
            // Run: node server.js   then open http://localhost:8090/site/home
            const http = require('http');
            const fs = require('fs');
            const path = require('path');

            const PUBLIC = path.join(__dirname, 'public');
            const PORT = process.env.PORT || 8090;

            function pageFor(url) {
              let name = url.split('?')[0].split('/').filter(Boolean).join('/');
              if (name.startsWith('site/')) name = name.slice(5);
              return name || 'home';
            }

            http.createServer((req, res) => {
              if (req.method === 'POST') {
                req.on('data', () => {});
                req.on('end', () => {
                  res.writeHead(303, { Location: '/site/contact-success' });
                  res.end();
                });
                return;
              }
              const name = pageFor(req.url);
              const file = path.join(PUBLIC, name + '.html');
              if (!/^[a-z0-9-]+$/.test(name) || !fs.existsSync(file)) {
                res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
                res.end('<h1>Page not found</h1><p><a href="/site/home">Home</a></p>');
                return;
              }
              res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
              res.end(fs.readFileSync(file));
            }).listen(PORT, () => console.log('@@BRAND@@ on http://localhost:' + PORT));
            """;

    private static final String TYPESCRIPT = """
            // @@BRAND@@ - site server (Node.js built-ins, TypeScript)
            // Run: npm install && npm start   then open http://localhost:8090/site/home
            import http from 'http';
            import fs from 'fs';
            import path from 'path';

            const PUBLIC: string = path.join(__dirname, 'public');
            const PORT: number = Number(process.env.PORT) || 8090;

            function pageFor(url: string): string {
              let name = url.split('?')[0].split('/').filter(Boolean).join('/');
              if (name.startsWith('site/')) name = name.slice(5);
              return name || 'home';
            }

            http.createServer((req: http.IncomingMessage, res: http.ServerResponse) => {
              if (req.method === 'POST') {
                req.on('data', () => {});
                req.on('end', () => {
                  res.writeHead(303, { Location: '/site/contact-success' });
                  res.end();
                });
                return;
              }
              const name = pageFor(req.url ?? '/');
              const file = path.join(PUBLIC, name + '.html');
              if (!/^[a-z0-9-]+$/.test(name) || !fs.existsSync(file)) {
                res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
                res.end('<h1>Page not found</h1><p><a href="/site/home">Home</a></p>');
                return;
              }
              res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
              res.end(fs.readFileSync(file));
            }).listen(PORT, () => console.log('@@BRAND@@ on http://localhost:' + PORT));
            """;

    private static final String GO = """
            // @@BRAND@@ - site server (Go standard library only)
            // Run: go run .   then open http://localhost:8090/site/home
            package main

            import (
                "fmt"
                "log"
                "net/http"
                "os"
                "path/filepath"
                "regexp"
                "strings"
            )

            var validName = regexp.MustCompile(`^[a-z0-9-]+$`)

            func pageFor(path string) string {
                name := strings.Trim(path, "/")
                name = strings.TrimPrefix(name, "site/")
                if name == "" {
                    return "home"
                }
                return name
            }

            func handler(w http.ResponseWriter, r *http.Request) {
                if r.Method == http.MethodPost {
                    http.Redirect(w, r, "/site/contact-success", http.StatusSeeOther)
                    return
                }
                name := pageFor(r.URL.Path)
                if !validName.MatchString(name) {
                    http.NotFound(w, r)
                    return
                }
                w.Header().Set("Content-Type", "text/html; charset=utf-8")
                http.ServeFile(w, r, filepath.Join("public", name+".html"))
            }

            func main() {
                port := os.Getenv("PORT")
                if port == "" {
                    port = "8090"
                }
                http.HandleFunc("/", handler)
                fmt.Println("@@BRAND@@ on http://localhost:" + port)
                log.Fatal(http.ListenAndServe(":"+port, nil))
            }
            """;

    private static final String KOTLIN = """
            // @@BRAND@@ - site server (Kotlin, JDK built-in HTTP server)
            // Run: kotlinc server.kt -include-runtime -d server.jar && java -jar server.jar
            import com.sun.net.httpserver.HttpExchange
            import com.sun.net.httpserver.HttpServer
            import java.io.File
            import java.net.InetSocketAddress

            val publicDir = File("public")
            val validName = Regex("[a-z0-9-]+")

            fun pageFor(path: String): String {
                val name = path.trim('/').removePrefix("site/")
                return name.ifEmpty { "home" }
            }

            fun respond(ex: HttpExchange, status: Int, body: ByteArray) {
                ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                if (body.isEmpty()) {
                    ex.sendResponseHeaders(status, -1)
                    ex.close()
                } else {
                    ex.sendResponseHeaders(status, body.size.toLong())
                    ex.responseBody.use { it.write(body) }
                }
            }

            fun main() {
                val port = (System.getenv("PORT") ?: "8090").toInt()
                val server = HttpServer.create(InetSocketAddress(port), 0)
                server.createContext("/") { ex ->
                    if (ex.requestMethod == "POST") {
                        ex.requestBody.readBytes()
                        ex.responseHeaders.add("Location", "/site/contact-success")
                        respond(ex, 303, ByteArray(0))
                        return@createContext
                    }
                    val name = pageFor(ex.requestURI.path)
                    val file = File(publicDir, "$name.html")
                    if (!validName.matches(name) || !file.isFile) {
                        respond(ex, 404, "<h1>Page not found</h1>".toByteArray())
                    } else {
                        respond(ex, 200, file.readBytes())
                    }
                }
                server.start()
                println("@@BRAND@@ on http://localhost:$port")
            }
            """;

    private static final String CSHARP = """
            // @@BRAND@@ - site server (ASP.NET Core minimal API)
            // Run: dotnet run   then open http://localhost:8090/site/home
            using System.Text.RegularExpressions;

            var app = WebApplication.CreateBuilder(args).Build();
            var publicDir = Path.Combine(Directory.GetCurrentDirectory(), "public");
            app.Urls.Add("http://0.0.0.0:" + (Environment.GetEnvironmentVariable("PORT") ?? "8090"));

            IResult Page(string? name)
            {
                name = string.IsNullOrEmpty(name) ? "home" : name;
                var file = Path.Combine(publicDir, name + ".html");
                if (!Regex.IsMatch(name, "^[a-z0-9-]+$") || !File.Exists(file))
                {
                    return Results.NotFound("Page not found");
                }
                return Results.Content(File.ReadAllText(file), "text/html; charset=utf-8");
            }

            app.MapGet("/", () => Page("home"));
            app.MapGet("/site/{name}", (string name) => Page(name));
            app.MapPost("/site/{name}", () => Results.Redirect("/site/contact-success"));
            app.Run();
            """;

    private static final String RUST = """
            // @@BRAND@@ - site server (Rust standard library only)
            // Run: cargo run   then open http://localhost:8090/site/home
            use std::fs;
            use std::io::{Read, Write};
            use std::net::{TcpListener, TcpStream};

            fn page_for(path: &str) -> String {
                let path = path.split('?').next().unwrap_or("").trim_matches('/');
                let name = path.strip_prefix("site/").unwrap_or(path);
                if name.is_empty() { "home".to_string() } else { name.to_string() }
            }

            fn respond(stream: &mut TcpStream, status: &str, extra: &str, body: &str) {
                let response = format!(
                    "HTTP/1.1 {}\\r\\nContent-Type: text/html; charset=utf-8\\r\\nContent-Length: {}\\r\\nConnection: close\\r\\n{}\\r\\n{}",
                    status, body.len(), extra, body
                );
                let _ = stream.write_all(response.as_bytes());
            }

            fn handle(mut stream: TcpStream) {
                let mut buf = [0u8; 4096];
                let n = stream.read(&mut buf).unwrap_or(0);
                let request = String::from_utf8_lossy(&buf[..n]).to_string();
                let mut parts = request.lines().next().unwrap_or("").split_whitespace();
                let method = parts.next().unwrap_or("");
                let path = parts.next().unwrap_or("/");
                if method == "POST" {
                    respond(&mut stream, "303 See Other", "Location: /site/contact-success\\r\\n", "");
                    return;
                }
                let name = page_for(path);
                let valid = !name.is_empty()
                    && name.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '-');
                if valid {
                    if let Ok(html) = fs::read_to_string(format!("public/{}.html", name)) {
                        respond(&mut stream, "200 OK", "", &html);
                        return;
                    }
                }
                respond(&mut stream, "404 Not Found", "", "<h1>Page not found</h1>");
            }

            fn main() {
                let port = std::env::var("PORT").unwrap_or_else(|_| "8090".to_string());
                let listener = TcpListener::bind(format!("0.0.0.0:{}", port)).expect("cannot bind port");
                println!("@@BRAND@@ on http://localhost:{}", port);
                for stream in listener.incoming().flatten() {
                    handle(stream);
                }
            }
            """;

    private static final String PHP = """
            <?php
            // @@BRAND@@ - site server (PHP built-in server)
            // Run: php -S localhost:8090 router.php   then open http://localhost:8090/site/home
            $path = trim(parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH), '/');
            if (strpos($path, 'site/') === 0) {
                $path = substr($path, 5);
            }
            $name = $path === '' ? 'home' : $path;
            if ($_SERVER['REQUEST_METHOD'] === 'POST') {
                header('Location: /site/contact-success', true, 303);
                exit;
            }
            $file = __DIR__ . '/public/' . $name . '.html';
            if (!preg_match('/^[a-z0-9-]+$/', $name) || !is_file($file)) {
                http_response_code(404);
                echo '<h1>Page not found</h1>';
                exit;
            }
            header('Content-Type: text/html; charset=utf-8');
            readfile($file);
            """;

    private static final String RUBY = """
            # @@BRAND@@ - site server (Ruby, WEBrick)
            # Run: gem install webrick && ruby server.rb   then open http://localhost:8090/site/home
            require 'webrick'

            PUBLIC = File.join(__dir__, 'public')
            server = WEBrick::HTTPServer.new(Port: (ENV['PORT'] || 8090).to_i)

            server.mount_proc '/' do |req, res|
              if req.request_method == 'POST'
                res.set_redirect(WEBrick::HTTPStatus::SeeOther, '/site/contact-success')
                next
              end
              name = req.path.split('/').reject(&:empty?).join('/').sub(%r{^site/}, '')
              name = 'home' if name.empty?
              file = File.join(PUBLIC, "#{name}.html")
              if name =~ /^[a-z0-9-]+$/ && File.file?(file)
                res['Content-Type'] = 'text/html; charset=utf-8'
                res.body = File.read(file)
              else
                res.status = 404
                res.body = '<h1>Page not found</h1>'
              end
            end

            trap('INT') { server.shutdown }
            server.start
            """;

    /** Languages offered by the home page picker (id, label). */
    public static List<String[]> pickerLanguages() {
        List<String[]> out = new ArrayList<>();
        for (StageLanguage l : StageLanguage.values()) {
            if (l != StageLanguage.OTHER) {
                out.add(new String[]{l.id(), l.label()});
            }
        }
        return out;
    }

    static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
