# Sharing BOT's Code Converter

## 1. Quick public link (from this laptop, no account)

```powershell
.\scripts\start-public.ps1
```

Starts BOT on port 8080 if needed, starts a Cloudflare quick tunnel, and prints a
`https://<random>.trycloudflare.com` link. Share `<link>/convert`.

- The link **changes every time the tunnel restarts** (reboot, script re-run). Run the script again and share the new one.
- The laptop must stay on and awake. Uses the local Ollama model (`qwen2.5-coder:1.5b`), which is slow when several people convert at once.
- Stop the tunnel: `Get-Process cloudflared | Stop-Process`.
- Safe by design: through the link, "Folder on this computer" and "Push to branch" are blocked (HTTP 403); remote users use a GitHub https URL, upload, or paste code. Those two features still work when you open BOT on the laptop itself (`http://localhost:8080`). The Stage bots page (`/stages`) is the exception for pushing: visitors can push generated code to their own repo by pasting their own GitHub token (used for that one push only, never stored or logged; the server's `GITHUB_TOKEN` is never used for them).

## 2. Permanent free link on Render + free Google Gemini AI (recommended)

Files: `Dockerfile`, `render.yaml` (both in the repo). Render builds the app from GitHub; the AI is Google Gemini's free tier,
so there is no Ollama, no laptop and no payment.

Step by step (no coding needed):

1. **Get a free Gemini key.** Open <https://aistudio.google.com/apikey>, sign in with a Google account, click
   **Create API key**, and copy the key (it starts with `AIza...`). Keep it private.
2. **Open Render.** Go to <https://dashboard.render.com> and **sign in with GitHub** (allow access to the
   `agentic-ai-chatbot` repository if asked).
3. Click **New +** -> **Blueprint** -> pick the **agentic-ai-chatbot** repository -> **Connect**.
4. When Render asks for `GEMINI_API_KEY`, **paste your key**, then click **Apply**. (Leave everything else as it is: the plan is
   already *Free*.)
5. **Wait about 5-10 minutes** for the first build. When the service shows **Live**, your link is
   `https://<name>.onrender.com` (for example `https://bot-code-converter.onrender.com`; add `/convert` for the converter).
   Every later push to GitHub redeploys automatically.

The page banner then reads **"AI model connected: Gemini (gemini-3.5-flash-lite)"**.

How the model is chosen (first match wins): `GEMINI_API_KEY` -> Gemini (`GEMINI_MODEL`, default `gemini-3.5-flash-lite`, through Google's
OpenAI-compatible endpoint `https://generativelanguage.googleapis.com/v1beta/openai/`) -> `OPENAI_API_KEY` (OpenAI,
`OPENAI_MODEL`) -> a reachable local Ollama -> built-in samples only. The converter, the stage bots and the chat all use the
selected model. With a cloud model, large files are split into bigger parts (about 12,000 characters) than with a small local model.

Public mode (`PUBLIC_MODE=true`) is on in `render.yaml`: all traffic arrives through Render's proxy, so "Folder on this
computer" and "Push to GitHub" are disabled for everyone, and each visitor only sees their own jobs.

Limits of the free setup
- The service **sleeps after ~15 minutes idle**; the first visit afterwards takes **about a minute** to wake up.
- 512 MB RAM (the JVM is capped at 300 MB), and storage is **ephemeral**: converted files vanish on restart/redeploy, so download the ZIP.
- Gemini's free tier has **rate and daily limits**. When they are hit, BOT shows **"Free AI quota reached - try again later"**.
  Wait a bit (per-minute limits) or until the next day (daily limit).
- Free keys may have their prompts used by Google to improve its products; do not convert confidential code with a free key.

## 3. Hugging Face Spaces (needs PRO, about $9/month) - not used

Hugging Face **Docker Spaces require a PRO subscription (about $9/month)**, so this option was prepared but is **not** the free
route; use Render + Gemini above. The files stay in the repo in case you subscribe.
With PRO, a Space (Docker SDK, CPU basic: 2 vCPU, 16 GB RAM) has enough memory to run Ollama with
`qwen2.5-coder:1.5b` itself, so visitors get real conversions with no API key, no laptop and no source code access.
Files: `deploy/hf-space/Dockerfile`, `deploy/hf-space/start.sh`, `deploy/hf-space/README.md` (Space front-matter),
`scripts/deploy-hf-space.ps1`.

**What you provide:** a free Hugging Face account and a **write** access token
(<https://huggingface.co/settings/tokens> -> *Create new token* -> type *Write*; a fine-grained token must be allowed to
create and write Spaces under your own account).

```powershell
$env:HF_TOKEN = 'hf_xxxxxxxx'              # this PowerShell window only, or store it once:
# [Environment]::SetEnvironmentVariable('HF_TOKEN','hf_xxxxxxxx','User')
.\scripts\deploy-hf-space.ps1 -DryRun       # optional: builds and checks the staging folder, sends nothing
.\scripts\deploy-hf-space.ps1               # creates <you>/bot if missing, pushes, prints the public URL
```

The script finds your user name (`GET /api/whoami-v2`), creates the public Docker Space `<you>/bot` if needed, copies
`Dockerfile`, `start.sh`, `README.md`, `pom.xml` and `src/main` into a temp folder and force-pushes that single commit
to `https://huggingface.co/spaces/<you>/bot` over https (the token is sent for that one `git push` only: nothing is saved in git
config, and it is never printed). It ends by printing `https://<you>-bot.hf.space` (converter: `/convert`). Re-run it
whenever the code changes; each run rebuilds the Space. Use `-SpaceName` or `-Owner` for another name or an organisation.
It does not touch your GitHub repository.

How the Space works
- The image is built by Hugging Face from source: Maven build -> runtime on the official `ollama/ollama` image
  (pinned version) + Java 17 + git, running as uid 1000. `qwen2.5-coder:1.5b` is pulled **at build time** (about 1 GB),
  so startup only has to start Ollama and the app.
- `start.sh` starts `ollama serve`, waits for it, warms the model, then runs
  `java -jar app.jar --server.port=7860 --agentic.converter.allow-local-paths=false --agentic.public-mode=true`.
- `agentic.public-mode=true` (env `PUBLIC_MODE`) makes **every** request count as a remote visitor, because on a Space all
  traffic arrives through Hugging Face's proxy. "Folder on this computer", `file://` / `git@` repository links and
  "Push to GitHub" are therefore refused (HTTP 403) and hidden, and the page shows an explanatory message. Each visitor still
  only sees their own jobs (the `bot_visitor` cookie is `SameSite=None; Secure; Partitioned` in public mode so it also works
  when the Space is opened inside the huggingface.co page iframe).
- Optional: add a Space **secret** `OPENAI_API_KEY` (Settings -> Variables and secrets) to use OpenAI instead of the local model.
- `data/` and `generated-app/` live inside the container and are writable, but Space storage is **ephemeral**: everything
  there is wiped on every restart or rebuild. Conversions are temporary; visitors should download the ZIP.

Limits and risks
- First build is slow: about 10-25 minutes (Maven dependencies, ~4 GB Ollama base image, ~1 GB model). Later builds reuse cached layers where they can.
- The model runs on 2 CPU cores: expect roughly a few tokens per second, so big projects take a long time and concurrent
  users queue (`OLLAMA_NUM_PARALLEL=1`). Cold start after a sleep or restart: about 1-3 minutes.
- A Space **sleeps after about 48 hours without visitors** and wakes on the next visit (a few minutes); it can also be
  restarted by Hugging Face at any time, which clears job files.
- Spaces have no uptime guarantee. If the Space shows a build error, open its **Logs** tab.

## 4. Notes on the quick tunnel

- `C:\Users\91984\Tools\cloudflared\start-public-bot.ps1` is the launcher used on this laptop: it starts BOT in public-safe mode
  (`--agentic.converter.allow-local-paths=false`, GitHub/OpenAI secrets and Git logins hidden from the process) if it is not
  running, then starts or reuses the quick tunnel and prints the `https://*.trycloudflare.com/convert` link.
  - Run it plain to bring BOT and the tunnel back after a reboot (it reuses a running tunnel, so the URL stays the same).
  - `-Restart` stops BOT and the tunnel and starts both again: **new URL**.
  - After rebuilding the jar, stop the BOT process first, then run the script plain; the running tunnel (and its URL) is kept.
- Quick-tunnel limits: no uptime guarantee, the URL changes whenever the tunnel process restarts, the laptop must stay on and
  awake, and `qwen2.5-coder:1.5b` on a laptop CPU is slow when several people convert at the same time.
- Privacy: each browser gets an anonymous `bot_visitor` cookie (HttpOnly, SameSite=Lax) and only sees its own conversion jobs;
  the host machine (opening `http://localhost:8080` directly) sees all of them.