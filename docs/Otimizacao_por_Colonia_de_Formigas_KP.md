# Otimização por Colónia de Formigas (ACO)
## Otimização por Inteligência Coletiva e Estigmergia
### Problema da Mochila 0/1 | Meta-heurísticas

## 1. O que é a Otimização por Colónia de Formigas?

A **Otimização por Colónia de Formigas** (**ACO — Ant Colony Optimization**) é uma meta-heurística inspirada no comportamento coletivo de formigas reais na procura de caminhos entre o ninho e fontes de alimento. Proposta por **Marco Dorigo, Vittorio Maniezzo e Alberto Colorni** em 1991/1996, baseia-se no conceito de **estigmergia** — comunicação indireta entre agentes através de modificações no ambiente.

Na natureza, as formigas depositam feromonas no chão ao percorrer um caminho. Outros agentes detectam estas feromonas e tendem a seguir os caminhos mais marcados. Caminhos mais curtos são percorridos mais vezes, acumulando mais feromonas, e tornam-se progressivamente mais atraentes — emergindo assim a solução ótima de forma distribuída e sem coordenação central.

Para o **Problema da Mochila 0/1**, o ACO adapta este mecanismo ao problema de seleção binária: cada formiga constrói iterativamente uma solução (seleciona itens), guiada por feromonas e informação heurística local.

### Ficha Técnica

| Campo | Descrição |
|---|---|
| Paradigma | Construtivo, baseado em colónia / inteligência de enxame |
| Proposto por | Dorigo, Maniezzo & Colorni (1991/1996), IEEE Trans. SMC-B |
| Tipo de pesquisa | Construtiva estocástica, guiada por feromonas |
| Memória | Distribuída — matriz de feromonas `τ` partilhada por todas as formigas |
| Soluções mantidas | `m` formigas constroem `m` soluções por iteração |
| Referência-chave | Stützle & Hoos (2000), Future Gen. Comp. Sys. 16(9):889–914 |

## 2. Conceitos Fundamentais

### 2.1 Representação das Feromonas

Para o KP binário com `n` itens, a estrutura de feromonas é um vetor `τ = (τ₁, τ₂, ..., τₙ)`, onde `τᵢ` representa o nível de feromona associado a incluir o item `i`. Valores elevados de `τᵢ` indicam que incluir o item `i` tem sido historicamente benéfico.

`τᵢ ∈ [τ_min, τ_max]  ∀ i ∈ {1, ..., n}`

### 2.2 Regra de Decisão Probabilística

Cada formiga `k` constrói uma solução percorrendo todos os itens e, para cada item `i`, decide incluir ou excluir com base numa probabilidade que combina feromona e informação heurística:

`P(xᵢ = 1) = (τᵢᵅ · ηᵢᵝ) / (τᵢᵅ · ηᵢᵝ + (1−τᵢ)ᵅ)`

Onde:
- `τᵢ` é o nível de feromona para o item `i`
- `ηᵢ = pᵢ/wᵢ` é a informação heurística (rácio valor/peso)
- `α` controla a influência da feromona — típico: `α = 1`
- `β` controla a influência da heurística — típico: `β = 2–5`

A informação heurística `ηᵢ = pᵢ/wᵢ` codifica o conhecimento do domínio: itens com maior valor por unidade de peso são intrinsecamente mais atrativos para a mochila.

### 2.3 Gestão da Feasibilidade

À medida que uma formiga adiciona itens, a capacidade residual `W_res` diminui. Para evitar soluções infeasíveis:

- Se `wᵢ > W_res`, o item `i` é automaticamente excluído (`P(xᵢ=1) = 0`)
- A decisão é tomada apenas sobre os itens ainda feasíveis
- Quando `W_res < min(wᵢ)` para todos os itens restantes, a construção termina

### 2.4 Atualização de Feromonas

Após todas as formigas construírem as suas soluções, as feromonas são atualizadas em dois passos.

#### Evaporação

Todas as feromonas evaporam proporcionalmente, simulando a evaporação natural e evitando convergência prematura:

`τᵢ ← (1 − ρ) · τᵢ      ∀ i`

Onde `ρ ∈ (0,1)` é a taxa de evaporação — típico: `ρ ∈ [0.1, 0.5]`.

#### Reforço

As formigas que encontraram boas soluções reforçam as feromonas dos itens que selecionaram:

`τᵢ ← τᵢ + Δτᵢ^best     onde  Δτᵢ^best = 1/z(x_best)  se xᵢ = 1`

Apenas a melhor formiga (ou a melhor global) reforça — estratégia do **MAX–MIN Ant System (MMAS)**.

## 3. Variantes Principais

### 3.1 Ant System (AS) — Original

Todas as `m` formigas reforçam as feromonas. Simples mas com convergência lenta e tendência para estagnação em instâncias grandes.

### 3.2 MAX–MIN Ant System (MMAS)

Proposto por Stützle & Hoos (2000), é a variante mais eficaz para o KP. Principais características:

- Apenas a melhor formiga (da iteração ou global) reforça feromonas
- Feromonas limitadas ao intervalo `[τ_min, τ_max]`, evitando estagnação
- Inicialização com `τ_max` e reinicialização periódica quando há estagnação
- `τ_max = 1/(ρ · z*_estimate)` e `τ_min = τ_max / (2n)`

### 3.3 Rank-Based Ant System

As `r` melhores formigas reforçam, com peso proporcional ao rank. Compromisso entre AS e MMAS.

## 4. Funcionamento Detalhado

### 4.1 Ciclo Principal do MMAS para KP

**Pseudocódigo — MMAS para o KP**

1. **Inicialização**: `τᵢ = τ_max ∀i`; construir solução inicial greedy `x*`
2. **Construção**: cada formiga `k` constrói `xᵏ`; para cada item `i`, decidir `xᵢ∈{0,1}` segundo `P(xᵢ=1)`
3. **Avaliação**: calcular `z(xᵏ)` para todas as `m` formigas; identificar melhor da iteração
4. **Atualização τ**: evaporar `τᵢ ← (1−ρ)τᵢ`; reforçar com best `τᵢ ← τᵢ + Δτᵢ^best`
5. **Limitação τ**: truncar `τᵢ ← min(τ_max, max(τ_min, τᵢ))`
6. **Reinicialização**: se estagnação, `τᵢ = τ_max ∀i` e reiniciar pesquisa
7. **Critério paragem**: número máximo de ciclos ou sem melhoria em `x*`

### 4.2 Pesquisa Local Integrada

O desempenho do ACO melhora substancialmente quando cada solução construída é submetida a uma pesquisa local simples (por exemplo, **1-flip**) antes da atualização de feromonas. Esta integração transforma o ACO num algoritmo memético distribuído.

## 5. Ligação direta com o código deste projeto

Esta secção liga a teoria à implementação real em Java e ao guião de execução em lote.

### 5.1 Excertos do núcleo ACO (MMAS)

No ficheiro `src/main/java/org/ant/knapsack/algo/AcoCore.java`, o ciclo principal segue exatamente o fluxo MMAS:

```java
for (int t = 0; t < iteracoes; t++) {
    Solucao melhorIteracao = null;

    for (int formiga = 0; formiga < numFormigas; formiga++) {
        Solucao candidata = construirSolucaoProbabilistica();
        Solucao refinada = melhorarComBuscaLocal1Flip(candidata);
        if (melhorIteracao == null || refinada.valorTotal > melhorIteracao.valorTotal) {
            melhorIteracao = refinada;
        }
    }

    evaporarFeromonio();
    depositarFeromonio(melhorGlobal);
    limitarFeromonio();
}
```

**Leitura prática do excerto acima:**
- Cada formiga constrói uma solução probabilística viável.
- Em seguida aplica-se uma melhoria local 1-flip.
- Após avaliar a melhor solução, as feromonas são atualizadas com evaporação, reforço e truncamento.

Outro excerto importante é a **seleção probabilística** de itens:

```java
double atratividade = Math.pow(tau[indice], alpha) * Math.pow(eta[indice], beta);
atratividade = Math.max(atratividade, 1e-12);
probabilidades[i] = atratividade;
```

Aqui, `tau` representa memória coletiva (feromonas) e `eta` representa heurística (`valor/peso`), materializando a equação da secção 2.2.

### 5.2 Excerto da CLI de execução

No ficheiro `src/main/java/org/ant/ACOKnapsack.java`, o `main` permite correr instâncias individuais:

```bash
java -cp target/classes org.ant.ACOKnapsack <ficheiro-instancia> \
  [--ants N] [--iters N] [--alpha A] [--beta B] [--rho R] [--q Q] [--stall N] [--seed S]
```

Exemplo:

```bash
java -cp target/classes org.ant.ACOKnapsack docs/inst_test/instancias/n_1000_1 --seed 100
```

Isto é útil para depuração e análise de uma instância específica.

### 5.3 Excerto do guião de benchmark

No ficheiro `scripts/run_docs_instances.sh`, o guião automatiza o processamento em lote:

```bash
./mvnw -q -DskipTests compile
...
output="$(java -cp target/classes org.ant.ACOKnapsack "${java_args[@]}")"
...
printf '%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,"%s","%s"\n' ... >> "$CSV_OUT"
```

**Resumo do papel do guião:**
- Compila o projeto.
- Percorre as instâncias em `docs/inst_test/instancias`.
- Executa o ACO para cada ficheiro de instância.
- Extrai métricas (valor, peso, itens escolhidos).
- Gera um CSV consolidado para comparação experimental.

## 6. Como as instâncias de teste são usadas e para que servem

As instâncias em `docs/inst_test/instancias` representam problemas de mochila 0/1 com parâmetros controlados.

### 6.1 Como são lidas pelo sistema

O loader `InstanciaLoader` lê cada ficheiro com o formato:
1. Primeira linha: número de itens `n`
2. Próximas `n` linhas: item com lucro e peso
3. Última linha: capacidade da mochila

Assim, o ACO recebe uma estrutura uniforme (`Instancia`) para todas as execuções.

### 6.2 Como entram no fluxo experimental

No guião, cada instância:
1. É identificada por nome (ex.: `n_1000_6`)
2. Pode ser mapeada para metadados (`n`, `c`, `g`, `f`, `eps`, `s`)
3. É executada no `ACOKnapsack`
4. Gera uma linha de resultado no CSV final

Isto permite repetir experiências e comparar desempenho entre várias configurações de instância.

### 6.3 Como validar os resultados com valores ótimos

Para avaliar a qualidade do ACO, o resultado de cada execução pode ser comparado com valores de referência (ótimos) disponibilizados em `docs/inst_test/Optimal.pdf`.

Fluxo recomendado:
1. Executar `scripts/run_docs_instances.sh` para gerar o CSV de resultados do ACO.
2. Para cada instância (ex.: `n_1000_1`), extrair o `melhor_valor` do CSV.
3. Consultar o valor ótimo da mesma instância no ficheiro de referência.
4. Calcular o desvio relativo:
   `gap(%) = 100 * (valor_otimo - melhor_valor_aco) / valor_otimo`

Assim obténs uma métrica objetiva de qualidade por instância e uma visão agregada do desempenho do algoritmo.

### 6.4 Para que são usadas (objetivo)

As instâncias de teste são usadas para:
- **Avaliação de qualidade**: verificar o valor total que o ACO consegue atingir.
- **Avaliação de robustez**: observar estabilidade entre diferentes sementes e cenários.
- **Comparação entre parâmetros**: medir impacto de `alpha`, `beta`, `rho`, número de formigas e iterações.
- **Reprodutibilidade**: usar `seed` fixa no nome/metadados para repetir resultados.
- **Relatórios experimentais**: produzir CSV para análise estatística e gráficos.

## 7. Parâmetros Críticos e Ajuste

### Parâmetros e Valores Típicos

| Parâmetro | Valores típicos |
|---|---|
| Número de formigas `m` | 10 a 50 — geralmente `m = n` ou `m = 10–20` é suficiente |
| `α` (peso feromona) | 1.0 — raramente ajustado |
| `β` (peso heurística) | 2.0 a 5.0 — `β` elevado favorece gulodice |
| `ρ` (evaporação) | 0.1 a 0.5 — `ρ` baixo = memória longa; `ρ` alto = esquecimento rápido |
| `τ_min / τ_max` | `τ_max = 1/(ρ·z*)`; `τ_min = τ_max/(2n)` |
| Critério paragem | 500 a 2000 ciclos; reinicialização após 100 ciclos sem melhoria |

## 8. Vantagens e Limitações

### 8.1 Pontos Fortes

- Construção incremental — naturalmente gera soluções feasíveis para o KP
- Paralelismo real — `m` formigas trabalham independentemente
- Memória adaptativa distribuída — aprendizagem coletiva sem controlo centralizado
- Integração natural da heurística `η = pᵢ/wᵢ` — combina aprendizagem com conhecimento do domínio
- Escalável — eficaz para instâncias de grande dimensão

### 8.2 Limitações

- Convergência lenta no início — feromonas inicialmente uniformes dão pouca orientação
- Estagnação — se uma solução dominar cedo, as feromonas convergem e a diversidade colapsa
- Múltiplos parâmetros a ajustar — `α`, `β`, `ρ`, `m`, e limites de feromona
- Qualidade inferior ao AM para instâncias onde pesquisa local intensiva é crítica

## Referências

- Dorigo, M., Maniezzo, V., & Colorni, A. (1996). *Ant system: optimization by a colony of cooperating agents*. IEEE Trans. SMC-B 26(1):29–41. DOI: `10.1109/3477.484436`
- Stützle, T., & Hoos, H. H. (2000). *MAX–MIN Ant System*. Future Generation Computer Systems, 16(9):889–914. DOI: `10.1016/S0167-739X(99)00043-1`
- Kellerer, H., Pferschy, U., & Pisinger, D. (2004). *Knapsack Problems*. Springer.
