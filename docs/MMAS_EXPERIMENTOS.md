# Experiências MMAS (MAX-MIN Ant System)

Este projeto inclui um runner para varrer combinações de parâmetros MMAS e medir performance.

## Ficheiro de propriedades

Use `src/main/resources/mmas-experiments.properties`.

Cada propriedade aceita lista por vírgulas:
- `mmas.instances` (opcional; lista explícita de ficheiros)
- `mmas.instances.dir` (para correr automaticamente todas as instâncias da pasta)
- `mmas.ants`
- `mmas.iterations`
- `mmas.alpha`
- `mmas.beta`
- `mmas.rho`
- `mmas.q`
- `mmas.stall.limit`
- `mmas.seed`
- `mmas.output.csv`

## Como executar

Compilar o projeto:

```bash
mvn -DskipTests compile
```

Executar com o ficheiro de propriedades por omissão:

```bash
java -cp target/classes org.metaheuristicas.knapsack.experiments.MMASExperimentRunner
```

No ficheiro default, está configurado `mmas.instances.dir=docs/inst_test/instancias`,
ou seja, corre para todas as instâncias dessa pasta.

Ou com outro ficheiro de propriedades:

```bash
java -cp target/classes org.metaheuristicas.knapsack.experiments.MMASExperimentRunner caminho/para/propriedades.properties
```

## Saída

É gerado um CSV com uma linha por combinação:

- `instance`
- `ants`
- `iterations`
- `alpha`
- `beta`
- `rho`
- `q`
- `stall`
- `seed`
- `best_value`
- `total_weight`
- `elapsed_ms`
