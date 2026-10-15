# BOT: Agentic AI Chatbot

BOT is a Spring Boot / Java 17 app that runs a **5-stage AI development pipeline** (Business Analyst, Developer, Tester, Deploy, Trainer) for any domain and any language, and includes a **repo-scale code converter**. It runs on a local Ollama model or any OpenAI compatible API.

## Workflow

![BOT 5-stage workflow](docs/workflow.svg)

### 5-stage pipeline (`/stages`)

```mermaid
flowchart LR
    P["Prompt + domain"] --> BA
    BA["1 Business Analyst<br/>39 domains, domain sample"] -->|"user stories"| DEV
    DEV["2 Developer<br/>57 languages, with comments"] -->|"source code"| TEST
    TEST["3 Tester<br/>JUnit, pytest, Jest, Selenium"] -->|"unit and UI tests"| DEP
    DEP["4 Deploy<br/>Dockerfile, CI/CD, Kubernetes"] -->|"DEPLOY.md and configs"| TR
    TR["5 Trainer<br/>learns from approved outputs"] -->|"bot-training.json"| STORE[("data/bot-training.json")]
    STORE -.->|"feedback loop"| BA
    STORE -.->|"feedback loop"| DEV
    STORE -.->|"feedback loop"| TEST
    STORE -.->|"feedback loop"| DEP
```

Each stage is independent or chainable, and **Run all** executes the whole chain. You approve the outputs you like, and the Trainer stores them so later runs follow your examples.

### Code converter (`/convert`)

```mermaid
flowchart LR
    IN["Input<br/>paste, GitHub repo URL, upload or ZIP"] --> PRE["Prepare<br/>large-file chunking"]
    PRE --> CONV["Convert with LLM<br/>comments, plain-English business logic, module linking"]
    CONV --> OUT["Output"]
    OUT --> D["Download file or ZIP"]
    OUT --> PU["Push to a branch"]
    OUT --> T["Run timers"]
```

### Chat pipeline (`/`)

Prompt, then user stories, then a generated website you can view and edit (`/code`, `/edit`).

## Features

- **Business Analyst:** user stories for any of 39 domains, with a domain sample requirement.
- **Developer:** code in any of 57 languages, with comments.
- **Tester:** JUnit, pytest or Jest unit tests, plus Selenium (Java or Ruby) automation.
- **Deploy:** Dockerfile, CI/CD pipeline, Kubernetes manifests and `DEPLOY.md`.
- **Trainer:** learns from approved outputs, stored in `data/bot-training.json`.
- **Code converter:** paste code, give a GitHub repo URL, or upload a file or ZIP. Handles large files by chunking, adds comments, explains business logic in plain English, and links modules. Download a file or ZIP, push to a branch, and see run timers.

## Pages

| URL | What it does |
|-----|--------------|
| `/` | Chat pipeline: prompt, user stories, website |
| `/stages` | 5-stage pipeline, run each stage or Run all |
| `/convert` | Code converter |
| `/code` | View generated code |
| `/edit` | Edit the generated site |

## Run locally

Requirements: **JDK 17+** and **Maven 3.9+**.

```powershell
mvn clean package
java -jar target/agentic-ai-chatbot-1.0.0.jar
```

Or run `.\scripts\run-web.ps1`. Then open <http://localhost:8080>.

### Model

- **Local Ollama (default):** install [Ollama](https://ollama.com) and pull the model:

  ```powershell
  ollama pull qwen2.5-coder:1.5b
  ```

  Set `CONVERTER_MODEL` to choose another installed model. When it is blank, BOT picks the largest installed coding model.
- **OpenAI compatible API:** set `OPENAI_API_KEY` (optional `OPENAI_BASE_URL`, `OPENAI_MODEL`, default `gpt-4o-mini`).
- With neither, BOT falls back to built-in templates and samples.

| Variable | Purpose |
|----------|---------|
| `OPENAI_API_KEY` | Enables OpenAI mode |
| `OPENAI_BASE_URL` | Default `https://api.openai.com/v1` |
| `OPENAI_MODEL` | Default `gpt-4o-mini` |
| `LLM_ENABLED` | `true` or `false` |
| `OLLAMA_URL` | Default `http://localhost:11434` |
| `CONVERTER_MODEL` | Ollama model for the converter |
| `GITHUB_TOKEN` | Optional, for private repos and pushing |

## Share it publicly

```powershell
.\scripts\start-public.ps1
```

Starts BOT in safe public mode (local folder input and push-to-branch are blocked, visitors only see their own jobs) and a free Cloudflare quick tunnel, then prints a `https://<random>.trycloudflare.com` link. Share `<link>/convert` or `<link>/stages`. The link changes each time the tunnel restarts, and the laptop must stay on.

For a permanent link, deploy to Render with `render.yaml` and the `Dockerfile`. See [docs/HOSTING.md](docs/HOSTING.md).

## Project layout

```text
agentic-ai-chatbot/
├── src/main/java/com/agentic/chatbot/   # agents, generators, converter, web controllers
├── src/main/resources/                  # templates, static assets, code samples, application.yml
├── scripts/                             # run-web, start-public, pipeline helpers
├── docs/                                # workflow.svg, HOSTING.md, guides
├── data/bot-training.json               # approved training examples
├── Dockerfile, render.yaml              # hosting
└── pom.xml
```

## REST API

- `POST /api/pipeline`: run the chat pipeline.
- `POST /api/stages/{stage}`, `POST /api/stages/run-all`: run one stage or all stages.
- `POST /api/stages/{stage}/approve`: approve an output for the Trainer.
- `POST /api/convert/jobs`: start a repo or file conversion job (download, ZIP and push endpoints live under `/api/convert/jobs/{id}`).
