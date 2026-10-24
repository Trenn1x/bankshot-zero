#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ ! -s "$PROJECT_ROOT/dist/web/game.js" ]]; then
  "$PROJECT_ROOT/scripts/build.sh"
fi
echo "Open http://127.0.0.1:${PORT:-8080} in a desktop browser. Press Ctrl-C to stop."
exec python3 -m http.server "${PORT:-8080}" --bind 127.0.0.1 --directory "$PROJECT_ROOT/dist/web"
