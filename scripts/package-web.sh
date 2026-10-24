#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/toolchain.sh"
cd "$PROJECT_ROOT"

if [[ ! -s target/web/game.js || ! -f web/index.html ]]; then
  echo "Missing compiled game or web page. Run ./scripts/build.sh first." >&2
  exit 1
fi

mkdir -p dist
rm -rf dist/web
mkdir -p dist/web
cp -R web/. dist/web/
cp target/web/game.js dist/web/game.js
cp LICENSE dist/web/LICENSE.txt
mkdir -p dist/web/licenses
cp docs/licenses/TeaVM-LICENSE.txt docs/licenses/TeaVM-NOTICE.txt dist/web/licenses/
find dist/web -name .DS_Store -delete
rm -f dist/bankshot-zero-web.zip
jar --create --file dist/bankshot-zero-web.zip --no-manifest -C dist/web .
printf 'Browser build: %s/dist/web/index.html\nUpload archive: %s/dist/bankshot-zero-web.zip\n' "$PROJECT_ROOT" "$PROJECT_ROOT"
