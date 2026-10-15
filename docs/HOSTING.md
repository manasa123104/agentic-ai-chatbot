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
- Safe by design: through the link, "Folder on this computer" and "Push to branch" are blocked (HTTP 403); remote users use a GitHub https URL, upload, or paste code. Those two features still work when you open BOT on the laptop itself (`http://localhost:8080`).

## 2. Permanent link on Render (free tier)

Files: `Dockerfile`, `render.yaml` (already in the repo once pushed).

1. Push this repo to GitHub.
2. Go to <https://render.com> and sign in with GitHub.
3. **New +** -> **Blueprint** -> pick this repository -> **Apply**.
4. When asked, set `OPENAI_API_KEY` (optional but recommended - the free server has no Ollama, so without a key only the built-in samples convert).
5. Wait for the build; Render gives you a permanent `https://bot-code-converter.onrender.com`-style URL.

How the model is chosen: `OPENAI_API_KEY` set -> OpenAI (`OPENAI_MODEL`, default `gpt-4o-mini`); otherwise a reachable Ollama; otherwise built-in samples only.

Limits of the free tier: the service **sleeps after ~15 minutes idle** (the first visit afterwards takes about a minute to wake), has 512 MB RAM, and its disk is wiped on each deploy/restart, so converted job files are temporary.

## 3. Notes on the quick tunnel

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