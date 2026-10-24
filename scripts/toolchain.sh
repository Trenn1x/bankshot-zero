#!/usr/bin/env bash
# Source from another script. Uses an installed JDK/Maven or the local .tools copies.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in "$PROJECT_ROOT/.tools/jdk" "$PROJECT_ROOT/.tools/jdk/Contents/Home" "$PROJECT_ROOT"/.tools/jdk-*/Contents/Home "$PROJECT_ROOT"/.tools/jdk-*; do
    if [[ -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! java -version >/dev/null 2>&1; then
  echo "Java 21+ is required. Install a JDK and set JAVA_HOME, or put it in .tools/jdk." >&2
  exit 1
fi

if command -v mvn >/dev/null 2>&1; then
  MAVEN_BIN="$(command -v mvn)"
elif [[ -x "$PROJECT_ROOT/.tools/maven/bin/mvn" ]]; then
  MAVEN_BIN="$PROJECT_ROOT/.tools/maven/bin/mvn"
else
  MAVEN_BIN=""
  for candidate in "$PROJECT_ROOT"/.tools/apache-maven-*/bin/mvn; do
    if [[ -x "$candidate" ]]; then
      MAVEN_BIN="$candidate"
      break
    fi
  done
fi

if [[ -z "$MAVEN_BIN" ]]; then
  echo "Maven 3.9+ is required. Install Maven or put it in .tools/maven." >&2
  exit 1
fi
