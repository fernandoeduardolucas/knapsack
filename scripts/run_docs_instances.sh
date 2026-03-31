#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCS_DIR="${1:-$ROOT_DIR/docs}"
SEARCH_DIR="$DOCS_DIR"
CSV_OUT="${CSV_OUT:-$ROOT_DIR/results/docs_instances_results.csv}"
INSTANCE_FILTERS=()
TOTAL_INSTANCES=0
PROCESSED_INSTANCES=0

show_help() {
  cat <<EOF
Uso:
  $(basename "$0") [PASTA_DOCS] [INSTANCIA_1 INSTANCIA_2 ...]

Exemplos:
  $(basename "$0")
  $(basename "$0") "$ROOT_DIR/docs/inst_test"
  $(basename "$0") "$ROOT_DIR/docs/inst_test" n_1000_1 n_1000_2

Variáveis de ambiente:
  CSV_OUT  Caminho para o CSV de saída (padrão: $ROOT_DIR/results/docs_instances_results.csv)
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  show_help
  exit 0
fi

if [[ $# -ge 2 ]]; then
  INSTANCE_FILTERS=("${@:2}")
fi

if [[ ! -d "$DOCS_DIR" ]]; then
  echo "Pasta docs não encontrada: $DOCS_DIR" >&2
  exit 1
fi

# Quando houver uma subpasta "Instancias", processa apenas ela.
# Isso evita tentar executar arquivos como README.txt que não são instâncias.
if [[ -d "$DOCS_DIR/Instancias" ]]; then
  SEARCH_DIR="$DOCS_DIR/Instancias"
fi

mkdir -p "$(dirname "$CSV_OUT")"
printf 'instancia,capacidade,itens,melhor_valor,peso_total,itens_escolhidos\n' > "$CSV_OUT"

cd "$ROOT_DIR"
./mvnw -q -DskipTests compile

instances=()
while IFS= read -r file; do
  instances+=("$file")
done < <(find "$SEARCH_DIR" -type f | sort)

TOTAL_INSTANCES="${#instances[@]}"

if [[ ${#instances[@]} -eq 0 ]]; then
  echo "Nenhuma instância encontrada em $SEARCH_DIR (esperado test.in ou *.txt/*.dat/*.inst/*.kp)"
  exit 0
fi

for file in "${instances[@]}"; do
  nome="$(basename "$file")"
  if [[ ${#INSTANCE_FILTERS[@]} -gt 0 ]]; then
    manter=0
    for filtro in "${INSTANCE_FILTERS[@]}"; do
      if [[ "$nome" == "$filtro" ]]; then
        manter=1
        break
      fi
    done
    if [[ $manter -eq 0 ]]; then
      continue
    fi
  fi

  if [[ "$nome" =~ ^[Rr][Ee][Aa][Dd][Mm][Ee](\..*)?$ ]]; then
    continue
  fi

  primeira_linha="$(head -n 1 "$file" | tr -d "\r" | xargs)"
  if [[ ! "$primeira_linha" =~ ^[0-9]+$ ]]; then
    continue
  fi

  echo "============================================================"
  echo "Instância: $file"
  output="$(java -cp target/classes org.ant.ACOKnapsack "$file")"
  echo "$output"

  capacidade="$(printf '%s\n' "$output" | awk -F': ' '/^Capacidade: / {print $2; exit}')"
  itens="$(printf '%s\n' "$output" | awk -F': ' '/^Itens: / {print $2; exit}')"
  melhor_valor="$(printf '%s\n' "$output" | awk -F': ' '/^Melhor valor: / {print $2; exit}')"
  peso_total="$(printf '%s\n' "$output" | awk -F': ' '/^Peso total: / {print $2; exit}')"
  itens_escolhidos="$(printf '%s\n' "$output" | sed -n 's/^Itens escolhidos (índices): //p' | head -n 1 | xargs)"

  printf '%s,%s,%s,%s,%s,"%s"\n' \
    "$nome" \
    "${capacidade:-}" \
    "${itens:-}" \
    "${melhor_valor:-}" \
    "${peso_total:-}" \
    "${itens_escolhidos:-}" >> "$CSV_OUT"
  ((PROCESSED_INSTANCES+=1))
  echo

done

echo "CSV gerado em: $CSV_OUT"
echo "Arquivos encontrados: $TOTAL_INSTANCES | Instâncias processadas: $PROCESSED_INSTANCES"
