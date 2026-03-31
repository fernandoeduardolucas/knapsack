#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCS_DIR="${1:-$ROOT_DIR/docs}"

if [[ ! -d "$DOCS_DIR" ]]; then
  echo "Pasta docs não encontrada: $DOCS_DIR" >&2
  exit 1
fi

cd "$ROOT_DIR"
./mvnw -q -DskipTests compile

mapfile -t instances < <(find "$DOCS_DIR" -type f \( -name "*.txt" -o -name "*.dat" -o -name "*.inst" -o -name "*.kp" \) | sort)
if [[ ${#instances[@]} -eq 0 ]]; then
  mapfile -t instances < <(find "$DOCS_DIR" -type f -name "test.in" | sort)
fi

if [[ ${#instances[@]} -eq 0 ]]; then
  echo "Nenhuma instância encontrada em $DOCS_DIR (esperado test.in ou *.txt/*.dat/*.inst/*.kp)"
  exit 0
fi

for file in "${instances[@]}"; do
  echo "============================================================"
  echo "Instância: $file"
  java -cp target/classes ACOKnapsack "$file"
  echo

done
