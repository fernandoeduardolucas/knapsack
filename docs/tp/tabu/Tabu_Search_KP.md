# Tabu Search (TS)
## Meta-heurística de Pesquisa Local com Memória
### Problema da Mochila 0/1 | Meta-heurísticas

## 1. O que é o Tabu Search?

O **Tabu Search** (**TS — Pesquisa Tabu**) é uma meta-heurística de pesquisa local proposta por **Fred Glover** em 1986 e formalizada em 1989/1997. Ao contrário de pesquisas locais simples que ficam presas em ótimos locais, o TS usa **memória adaptativa** para guiar a pesquisa e evitar ciclos, permitindo aceitar movimentos de piora para escapar de regiões subótimas do espaço de soluções.

A ideia central é manter uma **lista tabu** — uma memória de curto prazo que proíbe temporariamente movimentos recentes, forçando a pesquisa a explorar novas regiões. Esta proibição é flexibilizada pelo **critério de aspiração**: um movimento tabu é aceite se produzir uma solução melhor que qualquer solução já encontrada.

Para o **Problema da Mochila 0/1**, o TS explora iterativamente a vizinhança da solução corrente através de movimentos de flip (incluir/excluir um item) e swap (trocar um item dentro por um fora), mantendo a memória dos movimentos proibidos.

### Ficha Técnica

| Campo | Descrição |
|---|---|
| Paradigma | Pesquisa local com memória adaptativa |
| Proposto por | Fred Glover (1986, 1989, 1997) |
| Tipo de pesquisa | Iterativa, baseada em vizinhança, determinística com memória |
| Memória | Estruturada — lista tabu (curto prazo) + frequência (longo prazo) |
| Soluções mantidas | 1 solução corrente + 1 melhor global |
| Referência-chave | Glover, F. & Laguna, M. (1997). Tabu Search. Kluwer Academic |

## 2. Conceitos Fundamentais

### 2.1 Vizinhança e Movimentos

Para o KP binário com `n` itens e solução `x = (x₁, x₂, ..., xₙ) ∈ {0,1}ⁿ`:

**Movimento 1-flip**: Inverter o estado de um item `i`:
- Se `xᵢ = 0` → `xᵢ = 1` (adicionar item, se cabe na mochila)
- Se `xᵢ = 1` → `xᵢ = 0` (remover item)

**Movimento swap**: Trocar um item `i` (dentro) por um item `j` (fora):
- `xᵢ = 1 → 0` e `xⱼ = 0 → 1` (se o peso resultante respeita a capacidade)

A vizinhança híbrida `N(x) = N_flip(x) ∪ N_swap(x)` permite uma exploração mais rica do espaço de soluções.

### 2.2 Lista Tabu (Memória de Curto Prazo)

A lista tabu armazena atributos dos movimentos recentes e proíbe-os durante um período chamado **tenure** (mandato):

`tabu[i] = t + tenure` (o item `i` fica proibido de ser alterado até à iteração `t + tenure`)

Tenures separados para flip e swap permitem controlo fino:
- `tenure_flip ≈ √n` (tipicamente 7–20)
- `tenure_swap ≈ √n / 1.5` (tipicamente 5–15)

### 2.3 Critério de Aspiração

Um movimento tabu é aceite se a solução resultante superar a melhor solução global conhecida:

`Aspiração(mov) ⟺ z(x') > z(x*_best)`

Isto evita que a lista tabu bloqueie caminhos para soluções excelentes.

### 2.4 Diversificação por Frequência (Memória de Longo Prazo)

Um vetor `freq[i]` rastreia quantas vezes cada item foi alterado. Quando o algoritmo estagna, penaliza-se movimentos sobre itens frequentemente manipulados:

`delta_efetivo(i) = delta(i) − λ · freq[i]`

Onde `λ` é a força de diversificação. Isto força a pesquisa para regiões menos exploradas.

### 2.5 Reinicialização Adaptativa

Após `L` iterações sem melhoria global:
1. Parte da melhor solução global
2. Remove aleatoriamente uma fração dos itens (perturbação)
3. Reconstrói com heurística gulosa perturbada
4. Limpa as listas tabu

## 3. Funcionamento Detalhado

### 3.1 Ciclo Principal do Tabu Search para KP

**Pseudocódigo — TS para o KP**

1. **Inicialização**: Construir solução gulosa `x*` (ordenada por `pᵢ/wᵢ`); `x_current = x*`
2. **Para cada iteração** `t = 1, ..., T`:
   1. **Gerar vizinhança**: Avaliar todos os movimentos flip e swap
   2. **Filtrar**: Excluir movimentos infeasíveis e movimentos tabu (exceto aspiração)
   3. **Selecionar**: Escolher o melhor movimento admissível `mov*`
   4. **Aplicar**: `x_current ← x_current ⊕ mov*`
   5. **Atualizar tabu**: Marcar atributos de `mov*` como tabu por `tenure` iterações
   6. **Atualizar frequência**: `freq[i]++` para itens alterados
   7. **Atualizar melhor**: Se `z(x_current) > z(x*)`, atualizar `x* = x_current`
   8. **Diversificar**: Se estagnação, reinicializar com perturbação
3. **Devolver** `x*`

### 3.2 Gestão da Feasibilidade

A feasibilidade é garantida na geração da vizinhança:
- **Flip add**: Só considerado se `w_current + wᵢ ≤ C`
- **Swap**: Só considerado se `w_current - wᵢ + wⱼ ≤ C`
- **Flip remove**: Sempre feasível (reduz peso)

## 4. Parâmetros Críticos e Ajuste

### Parâmetros e Valores Típicos

| Parâmetro | Valores típicos |
|---|---|
| Iterações `T` | 1000 a 5000 — depende da dimensão da instância |
| Tenure flip | 7 a 20 — `≈ √n`; valores maiores = mais diversificação |
| Tenure swap | 5 a 15 — tipicamente menor que tenure flip |
| Limite estagnação `L` | 50 a 200 — iterações sem melhoria antes de diversificar |
| Força diversificação `λ` | 0.1 a 0.3 — fração de perturbação na reinicialização |

### Interações entre Parâmetros

- **Tenure alto + stall baixo**: Exploração agressiva, boa para instâncias grandes
- **Tenure baixo + stall alto**: Intensificação forte, boa para instâncias pequenas/médias
- **Diversificação forte**: Previne estagnação mas pode perder boas regiões

## 5. Vantagens e Limitações

### 5.1 Pontos Fortes

- Determinístico (dado seed) — resultados reprodutíveis
- Eficiente em tempo — avaliação incremental O(1) por movimento
- Memória estruturada — combina intensificação e diversificação
- Escalável — vizinhança avaliada em O(n²) com swap, O(n) sem swap
- Simples de implementar e ajustar
- Especialmente eficaz quando a solução gulosa já está próxima do ótimo

### 5.2 Limitações

- Sensível ao tamanho do tenure — demasiado curto = ciclos; demasiado longo = exploração excessiva
- Vizinhança O(n²) pode ser cara para instâncias muito grandes (swap)
- Não tem mecanismo de aprendizagem coletiva (ao contrário do ACO)
- Qualidade depende fortemente da solução inicial

## Referências

- Glover, F. (1986). *Future paths for integer programming and links to artificial intelligence*. Computers & Operations Research, 13(5):533–549.
- Glover, F. (1989). *Tabu search — Part I*. ORSA Journal on Computing, 1(3):190–206.
- Glover, F., & Laguna, M. (1997). *Tabu Search*. Kluwer Academic Publishers.
- Kellerer, H., Pferschy, U., & Pisinger, D. (2004). *Knapsack Problems*. Springer.
