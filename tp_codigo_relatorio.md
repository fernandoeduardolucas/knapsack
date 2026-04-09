# TP — Relatório de Código (ACO/MMAS para Problema da Mochila 0/1)

## 1) Objetivo deste relatório

Este documento explica **como o algoritmo ACO/MMAS foi implementado no código** deste repositório, com base no referencial teórico fornecido:

- Otimização por Colónia de Formigas (ACO)
- Inteligência coletiva e estigmergia
- Aplicação ao Problema da Mochila 0/1
- Variante MAX–MIN Ant System (MMAS)

A explicação está orientada ao código-fonte real (classes, métodos e propriedades), mostrando o mapeamento entre teoria e implementação.

---

## 2) Arquitetura geral do módulo ACO

A implementação está organizada em três camadas principais:

1. **Runner de experiências (`MMASRunner`)**
   - Lê ficheiro `.properties` com a grelha de parâmetros.
   - Resolve instâncias de teste.
   - Executa múltiplas combinações em paralelo.
   - Gera CSV de resultados.

2. **Fachada do algoritmo (`ACOKnapsack`)**
   - Valida parâmetros de entrada.
   - Encaminha o processamento para o núcleo.

3. **Núcleo do algoritmo (`AcoCore`)**
   - Implementa o ciclo MMAS completo:
     - inicialização
     - construção probabilística das soluções
     - busca local 1-flip
     - evaporação
     - reforço da melhor solução
     - truncagem em `[tauMin, tauMax]`
     - reinicialização por estagnação

Complementarmente:

- `InstanciaLoader`: leitura das instâncias de mochila
- `Item`, `Instancia`, `Solucao`: modelos de dados

---

## 3) Mapeamento teoria → código

### 3.1 Representação de feromonas (Seção 2.1 do referencial)

No referencial, a feromona é representada por um vetor `τ = (τ1, …, τn)`, onde cada `τi` indica atratividade de incluir o item `i`.

No código:

- `double[] tau` em `AcoCore`
- um valor por item
- inicialização uniforme (`Arrays.fill(tau, 1.0)` em `inicializarEstruturas`)

Ou seja, a memória coletiva (estigmergia) é implementada de forma distribuída num vetor compartilhado entre todas as formigas da iteração.

---

### 3.2 Informação heurística η = valor/peso (Seção 2.2)

No referencial, cada item possui heurística local:

- `ηi = pi / wi`

No código:

- `double[] eta` em `AcoCore`
- cálculo em `inicializarEstruturas()` como `itens[i].valor / itens[i].peso`
- há normalização pela maior razão (`maiorRazao`) para estabilizar escala numérica

Essa normalização não altera a ordem de preferência relativa dos itens, mas reduz risco de distorções por magnitudes muito grandes.

---

### 3.3 Regra de decisão probabilística (Seção 2.2)

No referencial, a inclusão de item é decidida por probabilidade combinando feromona e heurística, controladas por `alpha` e `beta`.

No código (`construirSolucaoProbabilistica`):

1. `tau[indice]` é normalizado para faixa probabilística via `normalizarTauParaProbabilidade`.
2. Calcula-se:
   - `incluir = tau^alpha * eta^beta`
   - `excluir = (1 - tau)^alpha`
3. `probIncluir = incluir / (incluir + excluir)`
4. Sorteio com `rng.nextDouble()` decide inclusão/exclusão.

Isto corresponde à versão binária da regra probabilística descrita no documento.

---

### 3.4 Gestão de factibilidade (Seção 2.3)

No referencial, um item é excluído automaticamente se não couber na capacidade residual.

No código:

- durante a construção, antes da decisão probabilística:
  - `capacidadeResidual = capacidade - pesoAtual`
  - se `itens[indice].peso > capacidadeResidual`, então `continue` (item não é considerado para inclusão)

Assim, o algoritmo mantém soluções factíveis ao longo de toda a construção.

---

### 3.5 Atualização de feromonas (Seção 2.4)

#### Evaporação

Referencial:

- `τi <- (1 - ρ) * τi`

Código:

- método `evaporarFeromonio()`
- multiplica todos os `tau[i]` por `1 - rho`

#### Reforço (MMAS)

Referencial:

- reforço apenas da melhor solução (iteração/global)

Código:

- método `depositarFeromonio(Solucao melhor)`
- depósito `1 / valorTotal` em cada item selecionado
- no ciclo principal, o reforço é aplicado com `melhorGlobal`

Isso implementa estratégia de reforço elitista no estilo MMAS.

---

### 3.6 Limites de feromona [τmin, τmax] (MMAS)

Referencial:

- MMAS impõe limites para evitar estagnação prematura.

Código:

- `atualizarLimitesFeromonio(Solucao melhor)` calcula:
  - `tauMax = 1 / (rho * z)`
  - `tauMin = tauMax / (2n)`
- `limitarFeromonio()` trunca cada `tau[i]` no intervalo `[tauMin, tauMax]`

Adicionalmente, o código impõe salvaguardas numéricas:

- validação de finitude
- piso mínimo de `1e-6`
- teto de `0.999999` para manter compatibilidade com o termo `(1 - tau)` na decisão binária

---

### 3.7 Reinicialização por estagnação

Referencial:

- reinicializar quando há muitos ciclos sem melhoria.

Código:

- variável `semMelhoria` no laço principal
- se `semMelhoria >= limiteSemMelhoria`, executa `reiniciarFeromonio()` e zera contador
- `reiniciarFeromonio()` define todos os `tau` com `tauMax`

Isto restaura diversidade de busca e reduz risco de aprisionamento em ótimos locais.

---

### 3.8 Busca local 1-flip (Seção 4.2)

Referencial:

- recomenda aplicar busca local após construção para melhorar qualidade.

Código:

- método `melhorarComBuscaLocal1Flip(Solucao base)`
- estratégia de vizinhança: inverter seleção de 1 item por vez (`true↔false`)
- aceita mudança apenas se melhora valor e mantém factibilidade
- repete até não haver melhoria (`do...while`)

Na prática, isso torna o algoritmo um ACO memético (construção estocástica + refinamento local).

---

## 4) Ciclo principal do algoritmo no código

No `resolver()` de `AcoCore`, o fluxo é:

1. construir solução gulosa inicial (`construirSolucaoGulosaInicial`)
2. calcular `tauMin/tauMax`
3. reinicializar feromonas em `tauMax`
4. para cada iteração:
   - cada formiga constrói solução probabilística factível
   - aplicar busca local 1-flip
   - escolher melhor da iteração
   - atualizar melhor global
   - evaporar feromonas
   - reforçar com melhor global
   - limitar feromonas
   - se estagnou, reiniciar feromonas
5. devolver melhor solução global

Esse fluxo corresponde diretamente ao pseudocódigo MMAS descrito no material de referência.

---

## 5) Como o sistema lê propriedades (.properties)

A leitura de configuração é centralizada em `MMASRunner`.

### 5.1 Ficheiro padrão

Se nenhum argumento for passado ao `main`, o runner usa:

- `ant/src/main/resources/mmas-experiments.properties`

Caso contrário, usa o caminho fornecido via CLI (`args[0]`).

---

### 5.2 Carregamento das propriedades

Método `carregarProperties(Path path)`:

- valida existência do ficheiro
- usa `java.util.Properties`
- carrega com `properties.load(reader)`

Se não existir, lança erro explícito com o caminho.

---

### 5.3 Estratégia de chaves: nomes novos + legados

O código aceita nomes alinhados ao relatório (PT) e nomes legacy (EN), usando fallback em `readPropertyFirst(...)`.

Exemplos:

- Número de formigas:
  - novo: `mmas.numero.formigas`
  - legado: `mmas.ants`
- Iterações:
  - novo: `mmas.numero.ciclos`
  - legado: `mmas.iterations`
- Alpha:
  - novo: `mmas.peso.feromona`
  - legado: `mmas.alpha`
- Beta:
  - novo: `mmas.peso.heuristica`
  - legado: `mmas.beta`
- Rho:
  - novo: `mmas.taxa.evaporacao`
  - legado: `mmas.rho`
- Q:
  - novo: `mmas.intensidade.deposito`
  - legado: `mmas.q`
- Estagnação:
  - novo: `mmas.ciclos.sem.melhoria`
  - legado: `mmas.stall.limit`
- Semente:
  - novo: `mmas.semente`
  - legado: `mmas.seed`
- Paralelismo:
  - novo: `mmas.paralelismo`
  - legado: `mmas.parallelism`
- Saída CSV:
  - novo: `mmas.saida.csv`
  - legado: `mmas.output.csv`

Isso facilita evolução da nomenclatura sem quebrar compatibilidade com configurações antigas.

---

### 5.4 Parsing de listas

Vários parâmetros podem receber listas separadas por vírgula, para varrimento de grelha (grid search):

- `parseIntList`
- `parseDoubleList`
- `parseLongList`
- `parseStringList`

Exemplo no ficheiro atual:

- `mmas.numero.formigas=2,3,5`
- `mmas.peso.heuristica=2.0,3.0,4.0`

O runner cruza todas as combinações possíveis com todas as instâncias selecionadas.

---

### 5.5 Resolução de instâncias

Método `resolverInstancias(Properties p)`:

1. se `mmas.instancias` estiver definido, usa lista explícita de ficheiros
2. caso contrário, lê `mmas.instancias.dir`
3. lista ficheiros regulares da pasta, ordenados por nome
4. erro se diretoria não existir ou estiver vazia

Isto permite dois modos:

- execução controlada em poucas instâncias
- execução em lote sobre diretório inteiro

---

## 6) Como as instâncias da mochila são lidas

`InstanciaLoader.carregar(Path path)` espera o seguinte formato:

1. primeira linha: número de itens `n`
2. próximas `n` linhas: dados do item, com pelo menos 3 colunas
   - coluna `[1]`: lucro/valor
   - coluna `[2]`: peso
3. última linha: capacidade da mochila

Validações importantes:

- instância vazia
- `n <= 0`
- falta de linhas
- formato inválido
- capacidade ausente

Cria `Item[]` e devolve `Instancia(itens, capacidade)`.

---

## 7) Execução paralela e saída de resultados

O `MMASRunner` cria uma tarefa por combinação de:

- instância
- m (formigas)
- iterações
- alpha
- beta
- rho
- q
- limite sem melhoria
- seed

As tarefas rodam com `ExecutorService` + `ExecutorCompletionService`, permitindo coletar resultados conforme terminam.

Saída:

- CSV com colunas:
  - `instance,ants,iterations,alpha,beta,rho,q,stall,seed,best_value,total_weight,elapsed_ms`

Este desenho suporta estudos experimentais e comparação de sensibilidade de parâmetros.

---

## 8) Notas de aderência ao referencial

A implementação está **fortemente alinhada** ao documento-base:

- ACO construtivo para mochila 0/1
- heurística `valor/peso`
- MMAS com evaporação + reforço elitista
- limites de feromona
- reinicialização por estagnação
- busca local 1-flip integrada

Diferenças/pragmatismos de implementação:

1. `q` é lido na configuração e propagado no construtor, mas o depósito atual usa `1/valor` diretamente (isto mantém consistência com a ideia de reforço inversamente proporcional ao custo, mas não escala por `q`).
2. A ordem dos itens é embaralhada por formiga antes de decidir inclusão, adicionando diversidade estocástica adicional.
3. Há proteção numérica explícita para manter probabilidades estáveis (evita divisão por zero e extremos exatos).

---

## 9) Conclusão

O código implementa um **MMAS funcional e experimentalmente configurável** para o problema da mochila 0/1, mantendo boa correspondência com os conceitos de ACO e estigmergia descritos no documento teórico.

Do ponto de vista de engenharia, destaca-se:

- separação clara entre execução experimental e núcleo do algoritmo
- configuração flexível via `.properties`
- paralelização para varrimento de parâmetros
- validações de entrada e estabilidade numérica

Como próximos passos evolutivos, faria sentido:

- integrar `q` diretamente na fórmula de depósito
- comparar reforço `best-iteration` vs `best-global`
- adicionar métricas de estagnação/diversidade no CSV para análise mais fina do comportamento dinâmico das feromonas
