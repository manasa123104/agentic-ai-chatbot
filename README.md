# Agentic AI Chatbot (Java)

Multi-agent Java system that turns a **user story prompt** into:

1. **Bot 1 — UI Generator** → Spring Boot controller + Thymeleaf webpage  
2. **Bot 2 — Unit Testing** → JUnit 5 / MockMvc unit tests  
3. **Bot 3 — Functional Testing** → end-to-end functional tests  

Works **offline** with built-in Java templates, or with an **OpenAI-compatible API** when `OPENAI_API_KEY` is set.

## Prerequisites

- **JDK 17+** on `PATH` (`java -version`)
- **Maven 3.9+** on `PATH` (`mvn -version`)
- Optional: `OPENAI_API_KEY` for LLM-enhanced generation

## Quick start (web UI)

```powershell
cd C:\Users\91984\Projects\agentic-ai-chatbot
.\scripts\run-web.ps1
```

Open [http://localhost:8080](http://localhost:8080), paste a user story, click **Run agent pipeline**.

Example story:

```text
As a user, I want a login page so that I can access my dashboard.
```

## Quick start (script pipeline)

```powershell
.\scripts\run-pipeline.ps1 -UserStory "As a user, I want a login page so that I can access my dashboard."
```

Then:

```powershell
cd generated-app
mvn spring-boot:run   # http://localhost:8090/login
mvn test              # Bot 2 + Bot 3 tests
```

Or:

```powershell
.\scripts\run-generated-tests.ps1
```

## Project layout

```text
agentic-ai-chatbot/
├── scripts/
│   ├── run-web.ps1              # chatbot UI
│   ├── run-pipeline.ps1         # CLI 3-bot pipeline
│   └── run-generated-tests.ps1
├── src/main/java/com/agentic/chatbot/
│   ├── agent/                   # Bot 1/2/3 + orchestrator
│   ├── generator/               # code generators
│   ├── llm/                     # OpenAI client (optional)
│   └── web/                     # chat UI + REST API
├── generated-app/               # output of the agents
└── pom.xml
```

## REST API

```http
POST /api/pipeline
Content-Type: application/json

{
  "userStory": "As a user, I want a registration form...",
  "runUnitTests": true,
  "runFunctionalTests": true
}
```

## LLM configuration

| Variable | Purpose |
|----------|---------|
| `OPENAI_API_KEY` | Enables LLM mode |
| `OPENAI_BASE_URL` | Default `https://api.openai.com/v1` |
| `OPENAI_MODEL` | Default `gpt-4o-mini` |
| `LLM_ENABLED` | `true`/`false` |

Without a key, agents use deterministic Java templates (still generates real Spring Boot screens + tests).

## CLI flags

```text
--story="..."          Run pipeline at startup
--skip-unit            Skip Bot 2
--skip-functional      Skip Bot 3
--exit                 Exit JVM after pipeline (for scripts)
```
