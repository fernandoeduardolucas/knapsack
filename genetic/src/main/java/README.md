# AG Java — Algoritmo Genético para a Mochila 0/1

Esta pasta contém uma conversão para Java do algoritmo em `genetic/AG-Python/ag_v3.py`.

## Funcionalidades implementadas

- Representação binária da solução (`int[]` com `0`/`1`).
- Algoritmo genético geracional.
- Seleção por torneio.
- Cruzamento de dois pontos.
- Mutação `bit-flip`.
- Reparação greedy de soluções inviáveis.
- Elitismo.
- Paragem por número máximo de gerações ou por estagnação.
- Execução em lote para uma pasta de instâncias.
- Exportação dos resultados para CSV.

## Como compilar

```bash
cd genetic/AG-Java
javac GeneticKnapsack.java
```

## Como executar

Por defeito, o programa procura as instâncias em `../AG-Python/instancias` e grava os resultados em `ag_resultados_java.csv`:

```bash
cd genetic/AG-Java
java GeneticKnapsack
```

Também pode parametrizar a execução:

```bash
java GeneticKnapsack \
  --instances ../AG-Python/instancias \
  --output ag_resultados_java.csv \
  --population 50 \
  --generations 500 \
  --crossover 0.85 \
  --mutation 0.005 \
  --elite 3 \
  --tournament 3 \
  --stagnation 100 \
  --seed 42
```

Use `--help` para ver todas as opções disponíveis.

## Formato das instâncias

O formato esperado é o mesmo do código Python:

1. primeira linha: número de itens (`n`);
2. próximas `n` linhas: `indice valor peso`;
3. última linha: capacidade máxima da mochila.

Os valores e pesos são lidos como `long`, porque as instâncias incluídas usam números superiores ao limite de `int`.
