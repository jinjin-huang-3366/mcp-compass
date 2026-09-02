#!/usr/bin/env bash
set -euo pipefail

export PORT=18080
export SPRING_PROFILES_ACTIVE=local
export MCP_COMPASS_LLM_ENABLED=true
export MCP_COMPASS_VECTOR_ENABLED=true
export MCP_COMPASS_GITHUB_ENRICHMENT_ENABLED=true

./mvnw -pl backend spring-boot:run > "$RUNNER_TEMP/backend.log" 2>&1 &
backend_pid=$!
trap 'kill "$backend_pid" 2>/dev/null || true; wait "$backend_pid" 2>/dev/null || true' EXIT

ready=false
for attempt in $(seq 1 30); do
  if curl --fail --silent http://127.0.0.1:18080/actuator/health --output "$RUNNER_TEMP/local-health.json"; then
    ready=true
    break
  fi
  sleep 5
done
[[ "$ready" == "true" ]] || { tail -n 100 "$RUNNER_TEMP/backend.log"; exit 1; }

curl --fail --silent --show-error --max-time 3000 \
  --request POST \
  "http://127.0.0.1:18080/api/v1/dev/production-relevance/activate?maxPages=$MAX_REGISTRY_PAGES&embeddingBatchSize=$EMBEDDING_BATCH_SIZE" \
  --output "$RUNNER_TEMP/activation.json"

python - "$RUNNER_TEMP/activation.json" "$GITHUB_OUTPUT" <<'PY'
import json
import pathlib
import sys

payload = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
coverage = payload["coverage"]
corpus = coverage["corpusServers"]
if coverage["serversWithSearchDocuments"] != corpus:
    raise SystemExit("Search-document coverage is incomplete")
if coverage["serversWithEmbeddings"] != corpus:
    raise SystemExit("Embedding coverage is incomplete")
with pathlib.Path(sys.argv[2]).open("a", encoding="utf-8") as output:
    for key in ("registryPages", "registryServers", "backfilledDocuments"):
        output.write(f"{key}={payload[key]}\n")
    output.write(f"registryHasMore={str(payload['registryHasMore']).lower()}\n")
    for key, value in coverage.items():
        output.write(f"{key}={value}\n")
print(json.dumps(payload, sort_keys=True))
PY
