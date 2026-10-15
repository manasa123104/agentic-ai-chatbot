#!/usr/bin/env bash
# Launcher for the BOT Hugging Face Space: Ollama in the background, then the Spring Boot app on port 7860.
set -u

MODEL="${CONVERTER_MODEL:-qwen2.5-coder:1.5b}"
export CONVERTER_MODEL="$MODEL"
export OLLAMA_HOST="${OLLAMA_HOST:-127.0.0.1:11434}"
OLLAMA_URL="http://${OLLAMA_HOST}"

start_ollama() {
  ollama serve >> /tmp/ollama.log 2>&1 &
  OLLAMA_PID=$!
}

echo "[start] starting Ollama (models in ${OLLAMA_MODELS:-default})"
start_ollama

ready=0
for _ in $(seq 1 90); do
  if curl -sf "${OLLAMA_URL}/api/tags" > /dev/null; then ready=1; break; fi
  sleep 1
done
if [ "$ready" != 1 ]; then
  echo "[start] Ollama did not come up in 90 s; BOT will start without a local model. Log:"
  tail -n 30 /tmp/ollama.log
else
  # The model is pre-pulled at build time; only pull if it is somehow missing.
  if ! ollama list | grep -q "${MODEL%%:*}"; then
    echo "[start] model ${MODEL} not found, pulling it (this takes a few minutes)"
    ollama pull "$MODEL" || echo "[start] pull failed; converter falls back to built-in samples"
  fi
  # Load the model into RAM in the background so the first visitor does not wait for it.
  (curl -s -m 600 "${OLLAMA_URL}/api/generate" \
     -d "{\"model\":\"${MODEL}\",\"prompt\":\"hi\",\"stream\":false,\"keep_alive\":\"24h\",\"options\":{\"num_predict\":1}}" \
     > /dev/null 2>&1 &)
fi

# Tiny watchdog: restart Ollama if it ever dies (BOT re-detects it within 30 s).
(
  while sleep 30; do
    if ! kill -0 "$OLLAMA_PID" 2> /dev/null; then
      echo "[start] Ollama stopped, restarting it"
      start_ollama
    fi
  done
) &

echo "[start] starting BOT on port 7860 (public mode, local folders and push disabled)"
exec java -jar /app/app.jar \
  --server.port=7860 \
  --agentic.converter.allow-local-paths=false \
  --agentic.public-mode=true
