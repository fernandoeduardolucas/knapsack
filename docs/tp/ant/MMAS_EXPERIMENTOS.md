# Experiências MMAS (MAX-MIN Ant System)

Este projeto inclui um runner para varrer combinações de parâmetros MMAS e medir performance.

## Ficheiro de propriedades

Use `src/main/resources/mmas-experiments.properties`.

Cada propriedade aceita lista por vírgulas (nomes alinhados ao documento):
- `mmas.instancias` (opcional; lista explícita de ficheiros)
- `mmas.instancias.dir` (para correr automaticamente todas as instâncias da pasta)
- `mmas.numero.formigas` (`m`)
- `mmas.numero.ciclos`
- `mmas.peso.feromona` (`alpha`)
- `mmas.peso.heuristica` (`beta`)
- `mmas.taxa.evaporacao` (`rho`)
- `mmas.intensidade.deposito` (`q`)
- `mmas.ciclos.sem.melhoria` (estagnação/reinicialização)
- `mmas.semente`
- `mmas.saida.csv`

> Retrocompatibilidade: o runner também aceita os nomes antigos (`mmas.ants`,
> `mmas.iterations`, `mmas.alpha`, `mmas.beta`, `mmas.rho`, `mmas.q`,
> `mmas.stall.limit`, `mmas.seed`, `mmas.output.csv`).

## Como executar

Compilar o projeto:

```bash
mvn -DskipTests compile
```

Executar com o ficheiro de propriedades por omissão:

```bash
java -cp target/classes org.metaheuristicas.knapsack.common.knapsack.MMASRunner
```

No ficheiro default, está configurado `mmas.instancias.dir=docs/inst_test/instancias`,
ou seja, corre para todas as instâncias dessa pasta.

Ou com outro ficheiro de propriedades:

```bash
java -cp target/classes org.metaheuristicas.knapsack.common.knapsack.MMASRunner caminho/para/propriedades.properties
```

## Saída

É gerado um CSV com uma linha por combinação:

- `instance`
- `ants` (número de formigas `m`)
- `iterations` (número de ciclos)
- `alpha` (peso da feromona)
- `beta` (peso da heurística)
- `rho` (taxa de evaporação)
- `q` (intensidade de depósito)
- `stall` (ciclos sem melhoria)
- `seed`
- `best_value`
- `total_weight`
- `elapsed_ms`
