#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS_FILE="${1:-$ROOT_DIR/src/main/resources/mmas-experiments.properties}"

cd "$ROOT_DIR"

if [[ -x "$ROOT_DIR/mvnw" ]]; then
  "$ROOT_DIR/mvnw" -q -DskipTests compile
else
  mvn -q -DskipTests compile
fi

java -cp target/classes org.metaheuristicas.knapsack.experiments.MMASExperimentRunner "$PROPS_FILE"
