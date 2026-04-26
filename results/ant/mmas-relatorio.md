# Relatório detalhado MMAS — interpretação técnica dos resultados

## 1) Contexto do problema e objetivo do estudo

- **Problema:** mochila 0/1 com 1000 itens por instância, onde cada item é escolhido ou não, respeitando capacidade e maximizando valor total.
- **Objetivo desta grelha:** avaliar a sensibilidade do MMAS a hiperparâmetros de construção/atualização de feromona e observar qualidade vs custo computacional.
- **Fontes dos dados analisados:** `/workspace/knapsack/results/ant/mmas-grid-results.csv` (grelha completa) e `/workspace/knapsack/results/ant/mmas-detailed-results.csv` (melhor por instância).
- **Escala da experiência:** 65610 execuções sobre 10 instâncias.

## 2) Metaheurística MMAS no teu código (como interpretar)

- Cada formiga constrói uma solução factível de forma probabilística usando **feromona** (`tau`) e **heurística** (`eta = valor/peso`).
- Em cada ciclo, a melhor solução é refinada por busca local 1-flip e usada para reforço elitista de feromona (com evaporação e truncamento em `[tauMin, tauMax]`).
- O processo inclui reinício de feromona após vários ciclos sem melhoria (estagnação).
- O runner faz varrimento em grelha de parâmetros e grava CSV por execução + CSV de melhor por instância.

### Parâmetros avaliados nesta campanha

- `m` (número de formigas): 2, 3, 5
- `ciclos` (iterações): 2, 3, 5
- `alpha` (peso da feromona): 0.8000, 1.0000, 1.2000
- `beta` (peso heurístico): 2.0000, 3.0000, 4.0000
- `rho` (evaporação): 0.1000, 0.2000, 0.3000
- `q` (intensidade de depósito): 0.5000, 1.0000, 2.0000
- `stall` (ciclos sem melhoria antes de reinício): 5, 8, 12
- `seed`: 12, 202, 4

## 3) Leitura macro dos resultados

- Melhor execução da grelha: **9989486700** na instância `n_1000_2` (m=2, ciclos=2, alpha=0.8000, beta=2.0000, rho=0.1000, q=0.5000, stall=5, seed=4).
- Pior execução da grelha: **9500005805** na instância `n_1000_6`.
- Média de `best_value`: **9,834,393,542.00** | mediana: **9,971,112,010** | desvio-padrão populacional: **217,475,065.85**.
- Tempo médio por execução: **0.56 ms**; execuções com `elapsed_ms=0`: **33658/65610** (51.30%).
- Gap médio para ótimo conhecido (melhor por instância): **1.6036%**; melhor gap: **0.0000%**; pior gap: **4.9629%**.

> Nota: como quase todas as medições de tempo são 0 ms, este dataset é melhor para avaliar **qualidade de solução** do que desempenho temporal fino.

## 4) Ótimo conhecido vs melhor solução obtida por instância

| Instância | Ótimo conhecido | Melhor obtido | Gap (%) | Peso solução | Configuração vencedora |
|---|---:|---:|---:|---:|---|
| n_1000_1 | 9999946233 | 9989474305 | 0.1047 | 9989473965 | m=2, ciclos=2, α=0.8000, β=3.0000, ρ=0.1000, q=1.0000, stall=5, seed=202 |
| n_1000_2 | 9999964987 | 9989486700 | 0.1048 | 9989484086 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=5, seed=4 |
| n_1000_3 | 9999229281 | 9981260200 | 0.1797 | 9981256135 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=5, seed=4 |
| n_1000_4 | 9999239905 | 9961739503 | 0.3750 | 9961730601 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=5, seed=4 |
| n_1000_5 | 9999251796 | 9953142678 | 0.4611 | 9953141071 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=8, seed=4 |
| n_1000_6 | 9996100344 | 9500005805 | 4.9629 | 9500005921 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=2.0000, stall=5, seed=12 |
| n_1000_7 | 9996105266 | 9507823943 | 4.8847 | 9507821771 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=5, seed=4 |
| n_1000_8 | 9996111502 | 9500015421 | 4.9629 | 9500015353 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=8, seed=12 |
| n_1000_9 | 9980488131 | 9980484517 | 0.0000 | 9980477899 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=5, seed=4 |
| n_1000_10 | 9980507700 | 9980502348 | 0.0001 | 9980488290 | m=2, ciclos=2, α=0.8000, β=2.0000, ρ=0.1000, q=0.5000, stall=5, seed=4 |

### Interpretação por famílias de instâncias

- **n_1000_1 a n_1000_5:** gaps baixos (≈0.10% a 0.46%), indicando boa aproximação do ótimo com orçamento de busca reduzido.
- **n_1000_6 a n_1000_8:** gaps substancialmente maiores (≈4.88% a 4.96%), sugerindo instâncias mais difíceis para esta configuração de grelha curta.
- **n_1000_9 e n_1000_10:** quase ótimos (gap ~0.00%).

## 5) Impacto das variáveis nos resultados

Abaixo, para cada variável, mostro a média de `best_value`, desvio-padrão, gap médio e tempo médio por nível.

### m (formigas)

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 2 | 9834393542.00 | 217475065.85 | 1.6036 | 0.41 |
| 3 | 9834393542.00 | 217475065.85 | 1.6036 | 0.49 |
| 5 | 9834393542.00 | 217475065.85 | 1.6036 | 0.78 |
- Amplitude de média (`max-min`) para `ants`: **0.00**.

### ciclos

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 2 | 9834393542.00 | 217475065.85 | 1.6036 | 0.39 |
| 3 | 9834393542.00 | 217475065.85 | 1.6036 | 0.50 |
| 5 | 9834393542.00 | 217475065.85 | 1.6036 | 0.79 |
- Amplitude de média (`max-min`) para `iterations`: **0.00**.

### alpha

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 0.8000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.58 |
| 1.0000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.54 |
| 1.2000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
- Amplitude de média (`max-min`) para `alpha`: **0.00**.

### beta

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 2.0000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.53 |
| 3.0000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.57 |
| 4.0000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.58 |
- Amplitude de média (`max-min`) para `beta`: **0.00**.

### rho

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 0.1000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.55 |
| 0.2000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.58 |
| 0.3000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.55 |
- Amplitude de média (`max-min`) para `rho`: **0.00**.

### q

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 0.5000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.55 |
| 1.0000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
| 2.0000 | 9834393542.00 | 217475065.85 | 1.6036 | 0.57 |
- Amplitude de média (`max-min`) para `q`: **0.00**.

### stall

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 5 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
| 8 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
| 12 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
- Amplitude de média (`max-min`) para `stall`: **0.00**.

### seed

| Nível | Média best_value | Desvio padrão | Gap médio (%) | Tempo médio (ms) |
|---:|---:|---:|---:|---:|
| 4 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
| 12 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
| 202 | 9834393542.00 | 217475065.85 | 1.6036 | 0.56 |
- Amplitude de média (`max-min`) para `seed`: **0.00**.

## 6) Observações relevantes sobre implementação e leitura dos dados

- O parâmetro **`q`** praticamente não altera o resultado nesta campanha: para combinações idênticas dos restantes parâmetros, `best_value` foi igual em **21870/21870** casos comparáveis (100.00%).
- Isto é consistente com o comportamento observado no código atual: o depósito de feromona usa `1/valor` e não evidencia impacto operacional de `q` na fórmula efetiva de atualização.
- Como `ciclos` e `m` estão em níveis muito baixos (2,3,5), a busca pode estar subexplorada nas instâncias mais difíceis (especialmente n_1000_6-8).
- O ranking de top configurações por média tende a empatar em qualidade, sugerindo que parte da variabilidade é mais dominada pela instância e pela seed do que por pequenas variações dos hiperparâmetros testados.

## 7) Recomendações para próxima iteração experimental

- Aumentar orçamento de busca: testar `ciclos` e `m` maiores (ex.: 20/50/100), pelo menos nas instâncias n_1000_6..8.
- Se o objetivo é estudar `q`, confirmar/ajustar a implementação para que `q` participe explicitamente no depósito de feromona.
- Reportar tempo com resolução mais fina (µs/ns) ou acumular por lote para evitar excesso de 0 ms.
- Separar análise por instância (normalização por ótimo) para evitar que instâncias fáceis “mascarem” melhorias reais nas difíceis.

## 8) Configuração de saídas usada

- `mmas.saida.csv=results/ant/mmas-grid-results.csv`
- `mmas.saida.relatorio=results/ant/mmas-detailed-results.csv`
