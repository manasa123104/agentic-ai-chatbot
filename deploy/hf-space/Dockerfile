# Hugging Face Space (Docker SDK, free CPU basic: 2 vCPU / 16 GB RAM) for BOT.
#
# Build context = the Space repo root, laid out by scripts/deploy-hf-space.ps1:
#   Dockerfile, start.sh, README.md, pom.xml, src/
# Stage 1 builds the Spring Boot jar from source. Stage 2 is the official Ollama image (Ubuntu 24.04, so the
# Ollama binary AND its CPU libraries match) plus Java 17, git and the pre-pulled model.

# Pinned so a surprise Ollama release cannot break the Space; bump it deliberately.
# (A global ARG must come before the first FROM to be usable in a later FROM line.)
ARG OLLAMA_VERSION=0.40.4

# ---- Stage 1: build the jar -------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -Dmaven.test.skip=true

# ---- Stage 2: runtime (Ollama + Java 17) --------------------------------------------------------------------------
FROM ollama/ollama:${OLLAMA_VERSION}

# jdk-headless (not jre) so BOT's "compile check" of generated Java works; git for cloning GitHub URLs;
# python3 for the optional Python syntax check.
RUN apt-get update \
 && apt-get install -y --no-install-recommends openjdk-17-jdk-headless git python3 curl ca-certificates bash \
 && rm -rf /var/lib/apt/lists/*

# Hugging Face runs the container as uid 1000. Ubuntu 24.04 already ships a user "ubuntu" with uid 1000: replace it.
RUN (id -u 1000 >/dev/null 2>&1 && userdel -r "$(id -nu 1000)" || true) \
 && useradd -m -u 1000 -s /bin/bash user

ENV HOME=/home/user \
    OLLAMA_MODELS=/home/user/ollama-models \
    OLLAMA_HOST=127.0.0.1:11434 \
    OLLAMA_KEEP_ALIVE=24h \
    OLLAMA_NUM_PARALLEL=1 \
    CONVERTER_MODEL=qwen2.5-coder:1.5b \
    PUBLIC_MODE=true \
    CONVERTER_ALLOW_LOCAL_PATHS=false \
    JAVA_TOOL_OPTIONS="-Xmx2g -XX:+UseSerialGC" \
    PORT=7860

# Writable app + model directories. NOTE: Space storage is ephemeral - data/ and generated-app/ are wiped on every
# restart/rebuild, which is fine because BOT only keeps temporary conversion jobs there.
RUN mkdir -p /app/data /app/generated-app /home/user/ollama-models \
 && chown -R 1000:1000 /app /home/user

USER user
WORKDIR /app

# Pre-pull the model at build time (~1 GB) so the Space starts fast: start Ollama, pull, stop it.
RUN set -eu; \
    ollama serve > /tmp/ollama-build.log 2>&1 & OLLAMA_PID=$!; \
    ready=0; \
    for i in $(seq 1 60); do \
      if curl -sf http://127.0.0.1:11434/api/tags > /dev/null; then ready=1; break; fi; \
      sleep 1; \
    done; \
    if [ "$ready" != 1 ]; then cat /tmp/ollama-build.log; exit 1; fi; \
    ollama pull "$CONVERTER_MODEL"; \
    ollama list | grep -q "qwen2.5-coder"; \
    kill "$OLLAMA_PID"; \
    wait "$OLLAMA_PID" || true; \
    rm -f /tmp/ollama-build.log

COPY --from=build --chown=1000:1000 /build/target/agentic-ai-chatbot-*.jar /app/app.jar
COPY --chown=1000:1000 start.sh /app/start.sh
# Strip Windows line endings if the file was committed from Windows, then make it executable.
RUN sed -i 's/\r$//' /app/start.sh && chmod +x /app/start.sh

EXPOSE 7860
# The base image's entrypoint is "ollama"; replace it with our launcher.
ENTRYPOINT ["bash", "/app/start.sh"]
CMD []
