#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/toolchain.sh"
cd "$PROJECT_ROOT"
exec "$MAVEN_BIN" "$@"
