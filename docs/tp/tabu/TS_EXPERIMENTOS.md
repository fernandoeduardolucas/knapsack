# Experiências Tabu Search (TS)

Este projeto inclui um runner para varrer combinações de parâmetros Tabu Search e medir performance.

## Ficheiro de propriedades

Use `src/main/resources/ts-experiments.properties`.

Cada propriedade aceita lista por vírgulas:
- `ts.instances` (opcional; lista explícita de ficheiros)
- `ts.instances.dir` (para correr automaticamente todas as instâncias da pasta)
- `ts.iterations`
- `ts.tenure.flip`
- `ts.tenure.swap`
- `ts.stall.limit`
- `ts.diversify.strength`
- `ts.seed`
- `ts.output.csv`

## Como executar

Compilar o projeto:

```bash
mvn -DskipTests compile
```

Executar com o ficheiro de propriedades por omissão:

```bash
java -cp target/classes org.metaheuristicas.knapsack.experiments.TSExperimentRunner
```

No ficheiro default, está configurado `ts.instances.dir=docs/inst_test/instancias`,
ou seja, corre para todas as instâncias dessa pasta.

Ou com outro ficheiro de propriedades:

```bash
java -cp target/classes org.metaheuristicas.knapsack.experiments.TSExperimentRunner caminho/para/propriedades.properties
```

## Execução individual (CLI)

Para correr o Tabu Search numa única instância:

```bash
java -cp target/classes org.metaheuristicas.knapsack.TSKnapsack docs/inst_test/instancias/n_1000_1 --iters 3000 --tenure-flip 15 --tenure-swap 10 --stall 100 --diversify 0.2 --seed 42
```

## Saída

É gerado um CSV com uma linha por combinação:

- `instance`
- `iterations`
- `tenure_flip`
- `tenure_swap`
- `stall`
- `diversify`
- `seed`
- `best_value`
- `total_weight`
- `elapsed_ms`
