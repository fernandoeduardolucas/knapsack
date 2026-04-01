#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEARCH_DIR="${ROOT_DIR}/docs/inst_test/instancias"
HEURISTIC_NAME="${HEURISTIC_NAME:-aco}"
CSV_OUT="${CSV_OUT:-$ROOT_DIR/results/docs_instances_${HEURISTIC_NAME}_results.csv}"
REPORT_OUT="${REPORT_OUT:-$ROOT_DIR/results/docs_instances_${HEURISTIC_NAME}_report.csv}"
OPTIMAL_PROPS="${OPTIMAL_PROPS:-$ROOT_DIR/src/main/resources/optimal-values.properties}"
INSTANCE_NAME_PROPS="${INSTANCE_NAME_PROPS:-$ROOT_DIR/src/main/resources/instance-name-mapping.properties}"

read_property() {
  local props_file="$1"
  local key="$2"
  awk -F'=' -v k="$key" '
    /^[[:space:]]*($|#|!)/ { next }
    {
      line = $0
      split(line, pair, "=")
      current_key = pair[1]
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", current_key)
      if (current_key == k) {
        sub(/^[^=]*=/, "", line)
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        print line
        exit
      }
    }
  ' "$props_file"
}

map_instance_name() {
  local original="$1"
  local mapped=""
  if [[ -f "$INSTANCE_NAME_PROPS" ]]; then
    mapped="$(read_property "$INSTANCE_NAME_PROPS" "$original")"
  fi
  if [[ -n "$mapped" ]]; then
    printf '%s\n' "$mapped"
  else
    printf '%s\n' "$original"
  fi
}

get_optimal_value() {
  local instancia="$1"
  local optimal=""
  if [[ -f "$OPTIMAL_PROPS" ]]; then
    optimal="$(read_property "$OPTIMAL_PROPS" "$instancia")"
  fi
  if [[ -z "$optimal" ]]; then
    printf '%s\n' ""
    return 0
  fi
  printf '%s\n' "$optimal"
}

if [[ ! -d "$SEARCH_DIR" ]]; then
  echo "Pasta de instâncias não encontrada: $SEARCH_DIR" >&2
  exit 1
fi

mkdir -p "$(dirname "$CSV_OUT")"
mkdir -p "$(dirname "$REPORT_OUT")"
# Legenda de colunas de metadados no nome da instância:
# n = 1000 -> número de itens
# c = 10000000000 -> capacidade da mochila
# g = 10 -> parâmetro g do gerador
# f = 0.1 -> parâmetro f do gerador
# epsilon = 0.0001 -> parâmetro epsilon do gerador
# s = 100 -> seed/semente usada na geração da instância
printf 'instancia,n,c,g,f,eps,s,capacidade,itens,melhor_valor,peso_total,itens_escolhidos,valor_otimo,diferenca_para_otimo,leitura\n' > "$CSV_OUT"

if [[ -f "$OPTIMAL_PROPS" ]]; then
  echo "instancia,melhor_valor,valor_otimo,diferenca_para_otimo,leitura" > "$REPORT_OUT"
fi

cd "$ROOT_DIR"
compile_project() {
  if [[ -x "$ROOT_DIR/mvnw" ]]; then
    if "$ROOT_DIR/mvnw" -q -DskipTests compile; then
      return 0
    fi
    echo "Aviso: falha ao compilar com ./mvnw, tentando Maven do PATH..." >&2
  fi

  if command -v mvn >/dev/null 2>&1; then
    mvn -q -DskipTests compile
    return 0
  fi

  echo "Erro: não foi possível compilar (./mvnw e mvn indisponíveis)." >&2
  return 1
}

compile_project

instances=()
while IFS= read -r file; do
  instances+=("$file")
done < <(find "$SEARCH_DIR" -type f | sort)

if [[ ${#instances[@]} -eq 0 ]]; then
  echo "Nenhuma instância encontrada em $SEARCH_DIR (esperado test.in ou *.txt/*.dat/*.inst/*.kp)"
  exit 0
fi

for file in "${instances[@]}"; do
  nome="$(basename "$file")"
  nome_base="${nome%.*}"
  n_param=""
  c_param=""
  g_param=""
  f_param=""
  eps_param=""
  s_param=""

  nome_parse="$(map_instance_name "$nome_base")"

  if [[ "$nome_parse" =~ n_([^_]+)_c_([^_]+)_g_([^_]+)_f_([^_]+)_eps_([^_]+)_s_([^_]+)$ ]]; then
    n_param="${BASH_REMATCH[1]}"
    c_param="${BASH_REMATCH[2]}"
    g_param="${BASH_REMATCH[3]}"
    f_param="${BASH_REMATCH[4]}"
    eps_param="${BASH_REMATCH[5]}"
    s_param="${BASH_REMATCH[6]}"
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

  java_args=("$file")
  # Quando o nome da instância inclui s (seed), repassa para o Java para
  # tornar a execução reprodutível.
  if [[ "$s_param" =~ ^[0-9]+$ ]]; then
    java_args+=("--seed" "$s_param")
  fi

  output="$(java -cp target/classes org.metaheuristicas.knapsack.ACOKnapsack "${java_args[@]}")"
  echo "$output"

  capacidade="$(printf '%s\n' "$output" | awk -F': ' '/^Capacidade: / {print $2; exit}')"
  itens="$(printf '%s\n' "$output" | awk -F': ' '/^Itens: / {print $2; exit}')"
  melhor_valor="$(printf '%s\n' "$output" | awk -F': ' '/^Melhor valor: / {print $2; exit}')"
  peso_total="$(printf '%s\n' "$output" | awk -F': ' '/^Peso total: / {print $2; exit}')"
  itens_escolhidos="$(printf '%s\n' "$output" | sed -n 's/^Itens escolhidos (índices): //p' | head -n 1 | xargs)"

  optimal_csv=""
  diff_csv=""
  leitura_csv=""

  if [[ -f "$OPTIMAL_PROPS" && "$nome_base" =~ ^n_[0-9]+_[0-9]+$ ]]; then
    optimal="$(get_optimal_value "$nome_base")"
    if [[ "$optimal" =~ ^[0-9]+$ && "${melhor_valor:-}" =~ ^[0-9]+$ ]]; then
      diff=$((optimal - melhor_valor))
      if (( diff >= 0 )); then
        if (( diff <= 1000 )); then
          leitura="praticamente ótimo"
        elif (( diff <= 20000 )); then
          leitura="muito perto do ótimo"
        else
          leitura="abaixo do ótimo"
        fi
      else
        leitura="inconsistente"
      fi

      optimal_csv="$optimal"
      diff_csv="$diff"
      leitura_csv="$leitura"

      printf '%s,%s,%s,%s,%s\n' \
        "$nome_base" \
        "$melhor_valor" \
        "$optimal" \
        "$diff" \
        "$leitura" >> "$REPORT_OUT"
    fi
  fi

  # Ordem do printf segue a legenda do cabeçalho CSV acima.
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,"%s",%s,%s,%s\n' \
    "$nome" \
    "${n_param:-}" \
    "${c_param:-}" \
    "${g_param:-}" \
    "${f_param:-}" \
    "${eps_param:-}" \
    "${s_param:-}" \
    "${capacidade:-}" \
    "${itens:-}" \
    "${melhor_valor:-}" \
    "${peso_total:-}" \
    "${itens_escolhidos:-}" \
    "${optimal_csv:-}" \
    "${diff_csv:-}" \
    "${leitura_csv:-}" >> "$CSV_OUT"
  echo

done

echo "CSV gerado em: $CSV_OUT"
if [[ -f "$OPTIMAL_PROPS" ]]; then
  echo "Relatório CSV gerado em: $REPORT_OUT"
fi
