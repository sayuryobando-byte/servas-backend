#!/usr/bin/env bash
# Ejecuta un script k6 de performance usando la imagen Docker grafana/k6.
#
# Uso:
#   ./perf/run.sh scenarios/smoke.test.js
#   ./perf/run.sh scenarios/load.test.js http://localhost:8080 BROWSER_VUS=10,HOLD_DURATION=3m 1
set -euo pipefail

SCRIPT="${1:?uso: $0 <script relativo a perf/> [BASE_URL] [EXTRA_ENV] [SAVE_JSON=1]}"
BASE_URL="${2:-http://host.docker.internal:8080}"
EXTRA_ENV="${3:-}"
SAVE_JSON="${4:-0}"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$ROOT/output"

ARGS=(run --rm -v "$ROOT:/perf" --add-host host.docker.internal:host-gateway -e "BASE_URL=$BASE_URL")

IFS=',' read -r -a ENVS <<< "$EXTRA_ENV"
for e in "${ENVS[@]:-}"; do
  [ -n "$e" ] && ARGS+=(-e "$e")
done

ARGS+=(grafana/k6 run)

if [ "$SAVE_JSON" = "1" ]; then
  NAME="$(basename "$SCRIPT" .js)-$(date +%Y%m%d%H%M%S).json"
  ARGS+=(--summary-export="/perf/output/$NAME")
  echo "Reporte JSON en perf/output/$NAME"
fi

ARGS+=("/perf/${SCRIPT//\\/\/}")

echo "==> docker ${ARGS[*]}"
docker "${ARGS[@]}"