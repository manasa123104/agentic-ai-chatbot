# Stage 1: build the jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# Stage 2: small JRE 17 runtime (no Ollama; the AI is Google Gemini via GEMINI_API_KEY)
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/agentic-ai-chatbot-1.0.0.jar app.jar
# Fit a 512 MB free instance: small heap, single-threaded GC. Scratch data lives in /tmp (ephemeral).
ENV JAVA_TOOL_OPTIONS="-Xmx300m -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -Xss512k" \
    PUBLIC_MODE=true \
    CONVERTER_ALLOW_LOCAL_PATHS=false \
    CONVERTER_OLLAMA_ENABLED=false \
    AGENTIC_OUTPUT_DIR=/tmp/generated-app \
    AGENTIC_CONVERTER_JOBS_DIR=/tmp/conversion-jobs
# server.port=${PORT:8080}: Render injects PORT and the app binds it.
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]