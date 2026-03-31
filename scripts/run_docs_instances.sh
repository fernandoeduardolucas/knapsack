#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DOCS_DIR="${1:-$ROOT_DIR/docs}"
SEARCH_DIR="$DOCS_DIR"
CSV_OUT="${CSV_OUT:-$ROOT_DIR/results/docs_instances_results.csv}"
INSTANCE_FILTERS=()

# Mapeamento manual para instâncias curtas (n_1000_1...n_1000_10) para os
# nomes completos com metadados do gerador.
declare -A INSTANCE_NAME_MAP=(
  ["n_1000_1"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.0001_s_100"
  ["n_1000_2"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.0001_s_300"
  ["n_1000_3"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.01_s_100"
  ["n_1000_4"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.01_s_200"
  ["n_1000_5"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.01_s_300"
  ["n_1000_6"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.1_s_100"
  ["n_1000_7"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.1_s_200"
  ["n_1000_8"]="n_1000_c_10000000000_g_10_f_0.1_eps_0.1_s_300"
  ["n_1000_9"]="n_1000_c_10000000000_g_10_f_0.1_eps_0_s_100"
  ["n_1000_10"]="n_1000_c_10000000000_g_10_f_0.1_eps_0_s_200"
)


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
# Legenda de colunas de metadados no nome da instância:
# n = 1000 -> número de itens
# c = 10000000000 -> capacidade da mochila
# g = 10 -> parâmetro g do gerador
# f = 0.1 -> parâmetro f do gerador
# epsilon = 0.0001 -> parâmetro epsilon do gerador
# s = 100 -> seed/semente usada na geração da instância
printf 'instancia,n,c,g,f,eps,s,capacidade,itens,melhor_valor,peso_total,itens_escolhidos\n' > "$CSV_OUT"

cd "$ROOT_DIR"
./mvnw -q -DskipTests compile

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

  nome_parse="$nome_base"
  if [[ -n "${INSTANCE_NAME_MAP[$nome_base]:-}" ]]; then
    nome_parse="${INSTANCE_NAME_MAP[$nome_base]}"
  fi

  if [[ "$nome_parse" =~ n_([^_]+)_c_([^_]+)_g_([^_]+)_f_([^_]+)_eps_([^_]+)_s_([^_]+)$ ]]; then
    n_param="${BASH_REMATCH[1]}"
    c_param="${BASH_REMATCH[2]}"
    g_param="${BASH_REMATCH[3]}"
    f_param="${BASH_REMATCH[4]}"
    eps_param="${BASH_REMATCH[5]}"
    s_param="${BASH_REMATCH[6]}"
  fi

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

  java_args=("$file")
  # Quando o nome da instância inclui s (seed), repassa para o Java para
  # tornar a execução reprodutível.
  if [[ "$s_param" =~ ^[0-9]+$ ]]; then
    java_args+=("--seed" "$s_param")
  fi

  output="$(java -cp target/classes org.ant.ACOKnapsack "${java_args[@]}")"
  echo "$output"

  capacidade="$(printf '%s\n' "$output" | awk -F': ' '/^Capacidade: / {print $2; exit}')"
  itens="$(printf '%s\n' "$output" | awk -F': ' '/^Itens: / {print $2; exit}')"
  melhor_valor="$(printf '%s\n' "$output" | awk -F': ' '/^Melhor valor: / {print $2; exit}')"
  peso_total="$(printf '%s\n' "$output" | awk -F': ' '/^Peso total: / {print $2; exit}')"
  itens_escolhidos="$(printf '%s\n' "$output" | sed -n 's/^Itens escolhidos (índices): //p' | head -n 1 | xargs)"

  # Ordem do printf segue a legenda do cabeçalho CSV acima.
  printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,"%s"\n' \
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
    "${itens_escolhidos:-}" >> "$CSV_OUT"
  echo

done

echo "CSV gerado em: $CSV_OUT"
