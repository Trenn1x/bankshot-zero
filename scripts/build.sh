#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
"$SCRIPT_DIR/mvn.sh" --batch-mode --no-transfer-progress clean verify
"$SCRIPT_DIR/package-web.sh"
