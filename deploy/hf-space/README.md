---
title: BOT
colorFrom: blue
colorTo: indigo
sdk: docker
app_port: 7860
pinned: false
short_description: Code converter and AI agent bots
---

# BOT

BOT converts code and whole projects between programming languages and runs agent bots for user stories, code and
tests. It runs a small local model (`qwen2.5-coder:1.5b` through Ollama) on this Space's CPU, so it works without any
API key.

- Open the app, then go to **/convert** to convert code.
- Use a **GitHub https URL**, **upload files / a ZIP**, or **paste code**, then download the result as a ZIP.
- "Folder on this computer" and "Push to GitHub" are disabled on this public Space.
- The model runs on 2 vCPUs, so large projects take a while, and several people converting at once queue up.
- Storage is temporary: converted files disappear when the Space restarts. Download what you need.
- A free Space goes to sleep after about 48 hours without visitors; the first visit afterwards wakes it up (a few minutes).

Source: <https://github.com/manasa123104/agentic-ai-chatbot>
