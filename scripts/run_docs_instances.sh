#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCS_DIR="${1:-$ROOT_DIR/docs}"
SEARCH_DIR="$DOCS_DIR"

if [[ ! -d "$DOCS_DIR" ]]; then
  echo "Pasta docs não encontrada: $DOCS_DIR" >&2
  exit 1
fi

# Quando houver uma subpasta "Instancias", processa apenas ela.
# Isso evita tentar executar arquivos como README.txt que não são instâncias.
if [[ -d "$DOCS_DIR/Instancias" ]]; then
  SEARCH_DIR="$DOCS_DIR/Instancias"
fi

cd "$ROOT_DIR"
./mvnw -q -DskipTests compile

instances=()
while IFS= read -r file; do
  instances+=("$file")
done < <(find "$SEARCH_DIR" -type f \( -name "*.txt" -o -name "*.dat" -o -name "*.inst" -o -name "*.kp" \) | sort)

if [[ ${#instances[@]} -eq 0 ]]; then
  while IFS= read -r file; do
    instances+=("$file")
  done < <(find "$SEARCH_DIR" -type f -name "test.in" | sort)
fi

if [[ ${#instances[@]} -eq 0 ]]; then
  echo "Nenhuma instância encontrada em $SEARCH_DIR (esperado test.in ou *.txt/*.dat/*.inst/*.kp)"
  exit 0
fi

for file in "${instances[@]}"; do
  echo "============================================================"
  echo "Instância: $file"
  java -cp target/classes org.ant.ACOKnapsack "$file"
  echo

done
