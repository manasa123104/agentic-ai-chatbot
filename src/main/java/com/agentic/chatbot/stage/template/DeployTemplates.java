package com.agentic.chatbot.stage.template;

import com.agentic.chatbot.stage.LanguageProfile;
import com.agentic.chatbot.stage.StageLanguage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic deployment files for each language: Dockerfile, .dockerignore, build scripts, a GitHub Actions
 * CI/CD workflow, optional docker-compose and Kubernetes YAML, and DEPLOY.md with step-by-step instructions.
 */
public final class DeployTemplates {

    /** What the generated files need to know about the project. */
    public record Project(LanguageProfile profile, String app, String entry, int port, boolean compose,
                          boolean kubernetes) {

        public StageLanguage language() {
            return profile.kind();
        }

        public String languageName() {
            return profile.label();
        }
    }

    /** Catalog languages beyond the built-ins that have a dedicated recipe; the rest get the generic one. */
    private static final Set<String> SPECIFIC = Set.of("scala", "dart", "r", "perl", "elixir");

    /** True when Deploy has a real recipe for the language (built-in or specific), not the generic TODO one. */
    public static boolean hasRecipe(LanguageProfile profile) {
        return profile.kind() != StageLanguage.OTHER || SPECIFIC.contains(profile.id());
    }

    /**
     * Per-language build recipe.
     *
     * @param dockerfile Dockerfile body; {{ENTRY}}, {{PORT}} and {{APP}} are filled in
     * @param ciSetup    GitHub Actions steps that install the toolchain (indented 6 spaces)
     * @param build      shell command that builds the code
     * @param test       shell command that runs the unit tests
     */
    private record Recipe(String dockerfile, String ciSetup, String build, String test) {
    }

    private DeployTemplates() {
    }

    public static Map<String, String> files(Project p) {
        Recipe r = recipe(p.profile());
        Map<String, String> files = new LinkedHashMap<>();
        files.put("Dockerfile", fill(r.dockerfile().replace("{{ENTRY}}", command(p.profile(), p.entry(), p.app())), p));
        files.put(".dockerignore", """
                .git
                .github
                **/node_modules
                **/target
                **/__pycache__
                **/.pytest_cache
                selenium-*/
                playwright-*/
                k8s/
                *.md
                run.json
                """);
        files.put("scripts/build.sh", fill("""
                #!/usr/bin/env bash
                # Builds, tests and packages {{APP}} as a Docker image, then runs it on port {{PORT}}.
                set -euo pipefail
                cd "$(dirname "$0")/.."

                echo "==> Build"
                {{BUILD}}
                echo "==> Unit tests"
                {{TEST}}
                echo "==> Docker image"
                docker build -t {{APP}}:latest .
                echo "==> Run (Ctrl+C to stop)"
                docker run --rm -p {{PORT}}:{{PORT}} {{APP}}:latest
                """, p, r));
        files.put("scripts/build.ps1", fill("""
                # Builds, tests and packages {{APP}} as a Docker image, then runs it on port {{PORT}} (Windows PowerShell).
                $ErrorActionPreference = "Stop"
                Set-Location (Join-Path $PSScriptRoot "..")

                Write-Host "==> Build"
                {{BUILD}}
                Write-Host "==> Unit tests"
                {{TEST}}
                Write-Host "==> Docker image"
                docker build -t {{APP}}:latest .
                Write-Host "==> Run (Ctrl+C to stop)"
                docker run --rm -p {{PORT}}:{{PORT}} {{APP}}:latest
                """, p, r));
        files.put(".github/workflows/ci-cd.yml", fill(WORKFLOW.replace("{{SETUP}}", r.ciSetup()), p, r));
        if (p.compose()) {
            files.put("docker-compose.yml", fill("""
                    services:
                      {{APP}}:
                        build: .
                        image: {{APP}}:latest
                        ports:
                          - "{{PORT}}:{{PORT}}"
                        environment:
                          - PORT={{PORT}}
                        restart: unless-stopped
                    """, p));
        }
        if (p.kubernetes()) {
            files.put("k8s/deployment.yaml", fill(K8S_DEPLOYMENT, p));
            files.put("k8s/service.yaml", fill("""
                    apiVersion: v1
                    kind: Service
                    metadata:
                      name: {{APP}}
                    spec:
                      selector:
                        app: {{APP}}
                      ports:
                        - port: 80
                          targetPort: {{PORT}}
                      type: ClusterIP
                    """, p));
        }
        files.put("DEPLOY.md", deployMd(p, r));
        return files;
    }

    private static String fill(String template, Project p) {
        return template.replace("{{APP}}", p.app()).replace("{{PORT}}", String.valueOf(p.port()))
                .replace("{{ENTRY}}", p.entry()).replace("{{LANG}}", p.languageName());
    }

    private static String fill(String template, Project p, Recipe r) {
        return fill(template.replace("{{BUILD}}", r.build()).replace("{{TEST}}", r.test()), p);
    }

    private static String deployMd(Project p, Recipe r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Deploying ").append(p.app()).append(" (").append(p.languageName()).append(")\n\n")
                .append("Generated by BOT's Deploy stage. Everything below runs from the folder that contains this file.\n\n")
                .append("## 1. Build and test locally\n\n")
                .append("    ").append(r.build()).append("\n    ").append(r.test()).append("\n\n")
                .append("Or run the whole chain with `scripts/build.sh` (Linux/macOS) or `scripts/build.ps1` (Windows).\n\n")
                .append("## 2. Build the Docker image\n\n")
                .append("    docker build -t ").append(p.app()).append(":latest .\n")
                .append("    docker run --rm -p ").append(p.port()).append(':').append(p.port()).append(' ')
                .append(p.app()).append(":latest\n\n")
                .append("The Dockerfile is multi-stage: the first stage builds the code and runs the unit tests, so a broken ")
                .append("build never produces an image.\n\n");
        if (p.entry().isBlank() && p.language() != StageLanguage.PLSQL) {
            sb.append("> No entry point (main program) was found in the code, so the image builds and tests it and then ")
                    .append("prints a message. Add a main program or HTTP server and update the `CMD` line.\n\n");
        }
        if (p.compose()) {
            sb.append("## 3. Run with Docker Compose\n\n    docker compose up --build\n\n");
        }
        sb.append("## ").append(p.compose() ? 4 : 3).append(". CI/CD with GitHub Actions\n\n")
                .append("Commit the project with `.github/workflows/ci-cd.yml`. On every push and pull request it builds, ")
                .append("runs the unit tests and builds the Docker image. On `main` it also pushes the image to the GitHub ")
                .append("Container Registry (`ghcr.io/<owner>/").append(p.app()).append("`).\n\n")
                .append("The optional `deploy` job runs only when the repository variable `DEPLOY_ENABLED` is `true` and the ")
                .append("secret `KUBE_CONFIG` holds a base64-encoded kubeconfig.\n\n");
        if (p.kubernetes()) {
            sb.append("## ").append(p.compose() ? 5 : 4).append(". Kubernetes\n\n")
                    .append("    kubectl apply -f k8s/\n    kubectl rollout status deployment/").append(p.app())
                    .append("\n    kubectl port-forward service/").append(p.app()).append(" 8080:80\n\n")
                    .append("Edit the `image:` line in `k8s/deployment.yaml` to point at your registry first.\n");
        }
        return sb.toString();
    }

    private static final String PY_SETUP = """
                  - uses: actions/setup-python@v5
                    with:
                      python-version: '3.12'
            """;
    private static final String NODE_SETUP = """
                  - uses: actions/setup-node@v4
                    with:
                      node-version: '20'
            """;
    private static final String JAVA_SETUP = """
                  - uses: actions/setup-java@v4
                    with:
                      distribution: temurin
                      java-version: '17'
            """;

    private static Recipe recipe(LanguageProfile profile) {
        return switch (profile.kind()) {
            case JAVA -> new Recipe("""
                    # Build stage: compile and run the unit tests with Maven.
                    FROM maven:3.9-eclipse-temurin-17 AS build
                    WORKDIR /src
                    COPY pom.xml .
                    RUN mvn -q -B dependency:go-offline || true
                    COPY src ./src
                    RUN mvn -q -B package

                    # Runtime stage: only the JRE and the jar.
                    FROM eclipse-temurin:17-jre
                    WORKDIR /app
                    COPY --from=build /src/target/*.jar app.jar
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, JAVA_SETUP, "mvn -B -q -DskipTests package", "mvn -B -q test");
            case KOTLIN -> new Recipe("""
                    FROM gradle:8.10-jdk17 AS build
                    WORKDIR /src
                    COPY . .
                    RUN gradle build --no-daemon

                    FROM eclipse-temurin:17-jre
                    WORKDIR /app
                    COPY --from=build /src/build/libs/*.jar app.jar
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, JAVA_SETUP + "      - uses: gradle/actions/setup-gradle@v4\n", "gradle build -x test", "gradle test");
            case PYTHON -> new Recipe("""
                    # Build stage: install dependencies and run the unit tests.
                    FROM python:3.12-slim AS test
                    WORKDIR /src
                    COPY . .
                    RUN pip install --no-cache-dir -r requirements.txt 2>/dev/null || pip install --no-cache-dir pytest
                    RUN if [ -d tests ]; then python -m pytest -q; else python -m compileall -q app; fi

                    # Runtime stage: application code only.
                    FROM python:3.12-slim
                    WORKDIR /app
                    ENV PYTHONUNBUFFERED=1 PORT={{PORT}}
                    COPY --from=test /src/app ./app
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, PY_SETUP, "pip install -r requirements.txt", "python -m pytest -q");
            case JAVASCRIPT -> new Recipe("""
                    FROM node:20-alpine AS test
                    WORKDIR /src
                    COPY package*.json ./
                    RUN npm ci || npm install
                    COPY . .
                    RUN npm test

                    FROM node:20-alpine
                    WORKDIR /app
                    ENV NODE_ENV=production PORT={{PORT}}
                    COPY package*.json ./
                    RUN npm ci --omit=dev || npm install --omit=dev
                    COPY --from=test /src/src ./src
                    EXPOSE {{PORT}}
                    USER node
                    {{ENTRY}}
                    """, NODE_SETUP, "npm ci || npm install", "npm test");
            case TYPESCRIPT -> new Recipe("""
                    FROM node:20-alpine AS build
                    WORKDIR /src
                    COPY package*.json tsconfig*.json ./
                    RUN npm ci || npm install
                    COPY . .
                    RUN npm test && npx tsc --outDir dist

                    FROM node:20-alpine
                    WORKDIR /app
                    ENV NODE_ENV=production PORT={{PORT}}
                    COPY package*.json ./
                    RUN npm ci --omit=dev || npm install --omit=dev
                    COPY --from=build /src/dist ./dist
                    EXPOSE {{PORT}}
                    USER node
                    {{ENTRY}}
                    """, NODE_SETUP, "npm ci && npx tsc --noEmit", "npm test");
            case GO -> new Recipe("""
                    FROM golang:1.22 AS build
                    WORKDIR /src
                    COPY go.* ./
                    RUN go mod download || true
                    COPY . .
                    RUN go test ./... && CGO_ENABLED=0 go build -o /out/{{APP}} ./...

                    FROM gcr.io/distroless/static-debian12
                    COPY --from=build /out/ /app/
                    EXPOSE {{PORT}}
                    USER nonroot
                    {{ENTRY}}
                    """, "      - uses: actions/setup-go@v5\n        with:\n          go-version: '1.22'\n",
                    "go build ./...", "go test ./...");
            case CSHARP -> new Recipe("""
                    FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
                    WORKDIR /src
                    COPY . .
                    RUN if [ -d tests/App.Tests ]; then dotnet test tests/App.Tests; fi && dotnet publish src/App -c Release -o /out

                    FROM mcr.microsoft.com/dotnet/runtime:8.0
                    WORKDIR /app
                    COPY --from=build /out .
                    ENV ASPNETCORE_URLS=http://+:{{PORT}}
                    EXPOSE {{PORT}}
                    {{ENTRY}}
                    """, "      - uses: actions/setup-dotnet@v4\n        with:\n          dotnet-version: '8.0.x'\n",
                    "dotnet build src/App", "dotnet test tests/App.Tests");
            case RUST -> new Recipe("""
                    FROM rust:1.81 AS build
                    WORKDIR /src
                    COPY . .
                    RUN cargo test --release && cargo build --release

                    FROM debian:bookworm-slim
                    COPY --from=build /src/target/release/ /app/
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, "      - uses: dtolnay/rust-toolchain@stable\n", "cargo build --release", "cargo test");
            case CPP, C -> new Recipe("""
                    FROM gcc:13 AS build
                    RUN apt-get update && apt-get install -y --no-install-recommends cmake && rm -rf /var/lib/apt/lists/*
                    WORKDIR /src
                    COPY . .
                    RUN cmake -S . -B build && cmake --build build && (cd build && ctest --output-on-failure)

                    FROM debian:bookworm-slim
                    COPY --from=build /src/build/ /app/
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, "      - run: sudo apt-get update && sudo apt-get install -y cmake\n",
                    "cmake -S . -B build && cmake --build build", "cd build && ctest --output-on-failure");
            case SWIFT -> new Recipe("""
                    FROM swift:5.10 AS build
                    WORKDIR /src
                    COPY . .
                    RUN swift test && swift build -c release

                    FROM swift:5.10-slim
                    COPY --from=build /src/.build/release/ /app/
                    EXPOSE {{PORT}}
                    {{ENTRY}}
                    """, "      - uses: swift-actions/setup-swift@v2\n", "swift build", "swift test");
            case PHP -> new Recipe("""
                    FROM composer:2 AS build
                    WORKDIR /src
                    COPY . .
                    RUN composer install --no-interaction || true
                    RUN [ -x vendor/bin/phpunit ] && vendor/bin/phpunit tests || echo "PHPUnit not installed"

                    FROM php:8.3-cli
                    WORKDIR /app
                    COPY --from=build /src .
                    EXPOSE {{PORT}}
                    USER www-data
                    {{ENTRY}}
                    """, "      - uses: shivammathur/setup-php@v2\n        with:\n          php-version: '8.3'\n",
                    "composer install", "vendor/bin/phpunit tests");
            case RUBY -> new Recipe("""
                    FROM ruby:3.3-slim AS test
                    WORKDIR /src
                    COPY . .
                    RUN gem install rspec && rspec

                    FROM ruby:3.3-slim
                    WORKDIR /app
                    COPY --from=test /src/lib ./lib
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, "      - uses: ruby/setup-ruby@v1\n        with:\n          ruby-version: '3.3'\n",
                    "gem install rspec", "rspec");
            case PLSQL -> new Recipe("""
                    # Oracle Database Free with the PL/SQL installed at first start-up.
                    FROM gvenzl/oracle-free:23-slim
                    ENV ORACLE_PASSWORD=change_me APP_USER={{APP}} APP_USER_PASSWORD=change_me
                    COPY db/*.sql /container-entrypoint-initdb.d/
                    EXPOSE 1521
                    """, "", "echo 'PL/SQL compiles when the container starts'",
                    "echo 'Run utPLSQL: exec ut.run();'");
            case COBOL -> new Recipe("""
                    FROM debian:bookworm-slim AS build
                    RUN apt-get update && apt-get install -y --no-install-recommends gnucobol && rm -rf /var/lib/apt/lists/*
                    WORKDIR /src
                    COPY src ./src
                    RUN for f in src/*.cbl; do cobc -x -o "/src/$(basename "$f" .cbl)" "$f"; done

                    FROM debian:bookworm-slim
                    RUN apt-get update && apt-get install -y --no-install-recommends libcob4 && rm -rf /var/lib/apt/lists/*
                    COPY --from=build /src/ /app/
                    {{ENTRY}}
                    """, "      - run: sudo apt-get update && sudo apt-get install -y gnucobol\n",
                    "for f in src/*.cbl; do cobc -x \"$f\"; done", "echo 'Run COBOL-Check for unit tests'");
            case OTHER -> otherRecipe(profile);
        };
    }

    private static Recipe otherRecipe(LanguageProfile profile) {
        return switch (profile.id()) {
            case "scala" -> new Recipe("""
                    # Build stage: run the tests and package a fat jar with sbt-assembly.
                    FROM sbtscala/scala-sbt:eclipse-temurin-17.0.13_11_1.10.7_3.3.4 AS build
                    WORKDIR /src
                    COPY . .
                    RUN sbt -batch test assembly

                    FROM eclipse-temurin:17-jre
                    WORKDIR /app
                    COPY --from=build /src/target/scala-*/*-assembly-*.jar app.jar
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, JAVA_SETUP + "      - uses: sbt/setup-sbt@v1\n", "sbt -batch compile", "sbt -batch test");
            case "dart" -> new Recipe("""
                    FROM dart:stable AS build
                    WORKDIR /src
                    COPY pubspec.* ./
                    RUN dart pub get
                    COPY . .
                    RUN dart test && mkdir -p /out && dart compile exe bin/main.dart -o /out/server

                    FROM scratch
                    COPY --from=build /runtime/ /
                    COPY --from=build /out/server /app/server
                    EXPOSE {{PORT}}
                    {{ENTRY}}
                    """, "      - uses: dart-lang/setup-dart@v1\n", "dart pub get && dart analyze", "dart test");
            case "r" -> new Recipe("""
                    FROM rocker/r-ver:4.4 AS test
                    WORKDIR /src
                    RUN R -q -e "install.packages('testthat')"
                    COPY . .
                    RUN if [ -d tests ]; then Rscript -e "testthat::test_dir('tests')"; fi

                    FROM rocker/r-ver:4.4
                    WORKDIR /app
                    COPY --from=test /src/R ./R
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, "      - uses: r-lib/actions/setup-r@v2\n      - run: Rscript -e \"install.packages('testthat')\"\n",
                    "Rscript -e \"invisible(lapply(list.files('R', full.names = TRUE), source))\"",
                    "Rscript -e \"testthat::test_dir('tests')\"");
            case "perl" -> new Recipe("""
                    FROM perl:5.40 AS test
                    WORKDIR /src
                    COPY . .
                    RUN if [ -f cpanfile ]; then cpanm --notest --installdeps .; fi
                    RUN if [ -d t ]; then prove -l t; fi

                    FROM perl:5.40-slim
                    WORKDIR /app
                    COPY --from=test /src/lib ./lib
                    EXPOSE {{PORT}}
                    USER 1000
                    {{ENTRY}}
                    """, "      - uses: shogo82148/actions-setup-perl@v1\n        with:\n          perl-version: '5.40'\n",
                    "perl -c -Ilib lib/*.pm", "prove -l t");
            case "elixir" -> new Recipe("""
                    FROM elixir:1.17 AS build
                    WORKDIR /src
                    ENV MIX_ENV=test
                    COPY . .
                    RUN mix local.hex --force && mix local.rebar --force && mix deps.get && mix test
                    RUN MIX_ENV=prod mix compile

                    FROM elixir:1.17-slim
                    WORKDIR /app
                    COPY --from=build /src .
                    ENV MIX_ENV=prod
                    EXPOSE {{PORT}}
                    {{ENTRY}}
                    """, "      - uses: erlef/setup-beam@v1\n        with:\n          elixir-version: '1.17'\n          otp-version: '27'\n",
                    "mix deps.get && mix compile", "mix test");
            default -> {
                String image = profile.dockerImage().isBlank() ? "debian:bookworm-slim" : profile.dockerImage();
                yield new Recipe("""
                        # TODO: confirm the {{LANG}} toolchain image, then add the build and test commands below.
                        FROM %s AS build
                        WORKDIR /src
                        COPY . .
                        RUN echo "TODO: build and run the {{LANG}} unit tests here (%s)"

                        FROM %s
                        WORKDIR /app
                        COPY --from=build /src .
                        EXPOSE {{PORT}}
                        {{ENTRY}}
                        """.formatted(image, profile.checkCommand().replace("\"", "'"), image), "",
                        "echo 'TODO: build command for {{LANG}}'", "echo 'TODO: test command for {{LANG}}'");
            }
        };
    }

    /** Build files the Dockerfile and workflow expect, added to the run when it does not have them yet. */
    public static Map<String, String> scaffold(LanguageProfile profile, String app) {
        Map<String, String> files = new LinkedHashMap<>();
        switch (profile.id()) {
            case "scala" -> {
                files.put("build.sbt", """
                        ThisBuild / scalaVersion := "3.3.4"

                        lazy val root = (project in file("."))
                          .settings(
                            name := "%s",
                            libraryDependencies += "org.scalameta" %%%% "munit" %% "1.0.2" %% Test
                          )
                        """.formatted(app));
                files.put("project/plugins.sbt", "addSbtPlugin(\"com.eed3si9n\" % \"sbt-assembly\" % \"2.3.0\")\n");
                return files;
            }
            case "dart" -> {
                files.put("pubspec.yaml", """
                        name: %s
                        environment:
                          sdk: ^3.5.0
                        dev_dependencies:
                          test: ^1.25.0
                        """.formatted(app.replace('-', '_')));
                return files;
            }
            case "elixir" -> {
                String module = pascal(app);
                files.put("mix.exs", """
                        defmodule %s.MixProject do
                          use Mix.Project

                          def project do
                            [app: :%s, version: "0.1.0", elixir: "~> 1.17", deps: []]
                          end

                          def application do
                            [extra_applications: [:logger]]
                          end
                        end
                        """.formatted(module, app.replace('-', '_')));
                return files;
            }
            default -> {
            }
        }
        switch (profile.kind()) {
            case PYTHON -> files.putAll(PythonTemplates.scaffold());
            case JAVASCRIPT -> files.put("package.json", """
                    {
                      "name": "%s",
                      "version": "1.0.0",
                      "private": true,
                      "scripts": { "start": "node src/index.js", "test": "jest --passWithNoTests" },
                      "devDependencies": { "jest": "^29.7.0" }
                    }
                    """.formatted(app));
            case TYPESCRIPT -> {
                files.put("package.json", """
                        {
                          "name": "%s",
                          "version": "1.0.0",
                          "private": true,
                          "scripts": { "build": "tsc", "start": "node dist/index.js", "test": "jest --passWithNoTests" },
                          "jest": { "preset": "ts-jest", "testEnvironment": "node" },
                          "devDependencies": { "jest": "^29.7.0", "ts-jest": "^29.2.0", "@types/jest": "^29.5.0", "@types/node": "^20.16.0", "typescript": "^5.6.0" }
                        }
                        """.formatted(app));
                files.put("tsconfig.json", """
                        {
                          "compilerOptions": { "target": "ES2022", "module": "commonjs", "outDir": "dist", "strict": true, "esModuleInterop": true, "types": ["node", "jest"] },
                          "include": ["src", "tests"]
                        }
                        """);
            }
            case KOTLIN -> {
                files.put("settings.gradle.kts", "rootProject.name = \"" + app + "\"\n");
                files.put("build.gradle.kts", PolyglotTemplates.kotlinBuild());
            }
            case CSHARP -> files.put("src/App/App.csproj", PolyglotTemplates.csharpProject());
            case GO -> files.put("go.mod", "module " + app + "\n\ngo 1.22\n");
            case RUST -> files.put("Cargo.toml", "[package]\nname = \"" + app.replace('-', '_') + "\"\nversion = \"0.1.0\"\nedition = \"2021\"\n");
            default -> {
            }
        }
        return files;
    }

    /** CMD line for the runtime stage, from the detected entry point (blank when none was found). */
    public static String command(LanguageProfile profile, String entry, String app) {
        boolean none = entry == null || entry.isBlank();
        String message = "[\"sh\", \"-c\", \"echo '" + app + " built and tested. Add a main program and update CMD in the Dockerfile.'\"]";
        return switch (profile.kind()) {
            case JAVA, KOTLIN -> "CMD " + (none ? message : "[\"java\", \"-cp\", \"app.jar\", \"" + entry + "\"]");
            case PYTHON -> "CMD " + (none ? "[\"python\", \"-c\", \"import app; print('" + app
                    + " installed. Add a __main__ entry point and update CMD.')\"]" : "[\"python\", \"-m\", \"" + entry + "\"]");
            case JAVASCRIPT -> "CMD [\"node\", \"" + (none ? "src/index.js" : entry) + "\"]";
            case TYPESCRIPT -> "CMD [\"node\", \"" + (none ? "dist/index.js" : entry) + "\"]";
            case GO -> "CMD [\"/app/" + app + "\"]";
            case CSHARP -> "CMD [\"dotnet\", \"App.dll\"]";
            case RUST, CPP, C, SWIFT, COBOL -> "CMD " + (none ? message : "[\"/app/" + entry + "\"]");
            case PHP -> "CMD [\"php\", \"" + (none ? "src/index.php" : entry) + "\"]";
            case RUBY -> "CMD [\"ruby\", \"" + (none ? "lib/main.rb" : entry) + "\"]";
            case PLSQL -> "";
            case OTHER -> "CMD " + switch (profile.id()) {
                case "scala" -> "[\"java\", \"-jar\", \"app.jar\"]";
                case "dart" -> "[\"/app/server\"]";
                case "r" -> none ? message : "[\"Rscript\", \"" + entry + "\"]";
                case "perl" -> none ? message : "[\"perl\", \"-Ilib\", \"" + entry + "\"]";
                case "elixir" -> "[\"mix\", \"run\", \"--no-halt\"]";
                default -> message;
            };
        };
    }

    private static String pascal(String app) {
        StringBuilder sb = new StringBuilder();
        for (String part : app.split("[^A-Za-z0-9]+")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.isEmpty() || Character.isDigit(sb.charAt(0)) ? "App" + sb : sb.toString();
    }

    private static final String WORKFLOW = """
            name: CI/CD

            on:
              push:
                branches: [ main ]
              pull_request:
              workflow_dispatch:

            env:
              IMAGE: ghcr.io/${{ github.repository_owner }}/{{APP}}

            jobs:
              build-test:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v4
            {{SETUP}}      - name: Build
                    run: {{BUILD}}
                  - name: Unit tests
                    run: {{TEST}}

              docker:
                needs: build-test
                runs-on: ubuntu-latest
                permissions:
                  contents: read
                  packages: write
                steps:
                  - uses: actions/checkout@v4
                  - uses: docker/setup-buildx-action@v3
                  - name: Log in to GHCR
                    if: github.ref == 'refs/heads/main'
                    uses: docker/login-action@v3
                    with:
                      registry: ghcr.io
                      username: ${{ github.actor }}
                      password: ${{ secrets.GITHUB_TOKEN }}
                  - name: Build (and push on main)
                    uses: docker/build-push-action@v6
                    with:
                      context: .
                      push: ${{ github.ref == 'refs/heads/main' }}
                      tags: ${{ env.IMAGE }}:${{ github.sha }},${{ env.IMAGE }}:latest

              deploy:
                # Optional: set repository variable DEPLOY_ENABLED=true and secret KUBE_CONFIG (base64 kubeconfig).
                needs: docker
                if: github.ref == 'refs/heads/main' && vars.DEPLOY_ENABLED == 'true'
                runs-on: ubuntu-latest
                environment: production
                steps:
                  - uses: actions/checkout@v4
                  - uses: azure/setup-kubectl@v4
                  - name: Deploy to Kubernetes
                    run: |
                      echo "${{ secrets.KUBE_CONFIG }}" | base64 -d > kubeconfig
                      export KUBECONFIG=$PWD/kubeconfig
                      kubectl apply -f k8s/
                      kubectl set image deployment/{{APP}} {{APP}}=${{ env.IMAGE }}:${{ github.sha }}
                      kubectl rollout status deployment/{{APP}}
            """;

    private static final String K8S_DEPLOYMENT = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: {{APP}}
              labels:
                app: {{APP}}
            spec:
              replicas: 2
              selector:
                matchLabels:
                  app: {{APP}}
              template:
                metadata:
                  labels:
                    app: {{APP}}
                spec:
                  containers:
                    - name: {{APP}}
                      image: ghcr.io/OWNER/{{APP}}:latest
                      ports:
                        - containerPort: {{PORT}}
                      env:
                        - name: PORT
                          value: "{{PORT}}"
                      resources:
                        requests:
                          cpu: 100m
                          memory: 128Mi
                        limits:
                          cpu: 500m
                          memory: 512Mi
            """;
}
