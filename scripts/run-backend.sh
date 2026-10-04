#!/usr/bin/env bash
set -euo pipefail
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
backend_dir="${CUE_BACKEND_DIR:-$project_dir/../superintelligence}"
if [[ "$(node -p 'process.versions.node.split(".")[0]')" != 24 ]]; then
  echo 'Use Node 24 (for example: nvm use 24) before starting Eve.' >&2
  exit 1
fi
cd "$backend_dir"
eve_pid=''
adapter_pid=''
cleanup() {
  [[ -z "$adapter_pid" ]] || kill "$adapter_pid" 2>/dev/null || true
  [[ -z "$eve_pid" ]] || kill "$eve_pid" 2>/dev/null || true
}
trap cleanup EXIT
trap 'exit 130' INT TERM
pnpm exec eve dev --no-ui --no-default-extensions --port 2001 &
eve_pid=$!
node mobile/server.mjs &
adapter_pid=$!
wait "$eve_pid"
