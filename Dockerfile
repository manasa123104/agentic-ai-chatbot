# Stage 1: build the jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# Stage 2: small JRE 17 runtime
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/agentic-ai-chatbot-1.0.0.jar app.jar
# Keep memory small enough for a 512 MB free instance
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:+UseSerialGC" \
    CONVERTER_ALLOW_LOCAL_PATHS=false \
    CONVERTER_OLLAMA_ENABLED=false
# The app listens on $PORT (server.port=${PORT:8080}); Render sets PORT itself.
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
