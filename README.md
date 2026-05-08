# Knapsack — execução das 3 metaheurísticas em Java

Este projeto contém três implementações Java para o problema da mochila 0/1:

- **Ant** — MAX-MIN Ant System (MMAS), em `ant/src/main/java`.
- **Genetic** — Algoritmo Genético (AG), em `genetic/src/main/java`.
- **Tabu** — Tabu Search (TS), em `tabu/src/main/java`.

Os comandos abaixo devem ser executados a partir da raiz do repositório.

## Pré-requisitos

- Java 21 ou superior.
- Maven 3.9 ou superior para compilar/executar os módulos **Ant** e **Tabu** através do `pom.xml`.

Pode confirmar as versões com:

```bash
java -version
mvn -version
```

## Instâncias de teste

As instâncias incluídas no repositório estão em:

```text
docs/inst_test/instancias
```

## 1. Ant — MAX-MIN Ant System (MMAS)

### Executar a grelha de experiências configurada

O runner lê, por omissão, o ficheiro `ant/src/main/resources/mmas-experiments.properties` e grava os resultados em `results/ant/`.

```bash
mvn compile exec:java \
  -Dexec.mainClass=org.metaheuristicas.knapsack.common.knapsack.MMASRunner
```

Este comando não passa argumentos ao programa; o ficheiro `.properties` por omissão é usado automaticamente.

### Ficheiros de saída principais

- `results/ant/mmas-grid-results.csv`
- `results/ant/mmas-detailed-results.csv`
- `results/ant/mmas-initial-solutions.csv`

## 2. Genetic — Algoritmo Genético (AG)

O ficheiro `GeneticKnapsack.java` está fora da configuração principal do Maven, por isso pode ser compilado diretamente com `javac`.

### Compilar

```bash
javac genetic/src/main/java/GeneticKnapsack.java
```

### Executar a grelha de experiências configurada

Sem argumentos, o programa usa `genetic/src/main/resources/genetic-experiments.properties` e grava os resultados em `results/genetic/`.

```bash
java -cp genetic/src/main/java GeneticKnapsack
```

Este comando não passa argumentos ao programa; o ficheiro `.properties` por omissão é usado automaticamente.

### Ficheiros de saída principais

- `results/genetic/ga-grid-results.csv`
- `results/genetic/ga-detailed-results.csv`
- `results/genetic/ga-relatorio.md`

## 3. Tabu — Tabu Search (TS)

### Executar a grelha de experiências configurada

O runner lê, por omissão, o ficheiro `tabu/src/main/resources/ts-experiments.properties` e grava os resultados em `results/tabu/`.

```bash
mvn compile exec:java \
  -Dexec.mainClass=org.metaheuristicas.knapsack.experiments.TSExperimentRunner
```

Este comando não passa argumentos ao programa; o ficheiro `.properties` por omissão é usado automaticamente.

### Ficheiro de saída principal

- `results/tabu/ts-grid-results.csv`

## Alterar parâmetros

Para alterar as experiências em lote, edite os ficheiros de propriedades:

- Ant/MMAS: `ant/src/main/resources/mmas-experiments.properties`
- Genetic/AG: `genetic/src/main/resources/genetic-experiments.properties`
- Tabu/TS: `tabu/src/main/resources/ts-experiments.properties`

Cada propriedade aceita uma lista separada por vírgulas. O runner executa o produto cartesiano das listas configuradas, por isso aumentar muitas listas ao mesmo tempo pode gerar muitas execuções. Assim, não é necessário passar argumentos na linha de comandos para escolher instâncias ou parâmetros; basta alterar o ficheiro `.properties` correspondente.

## Limpeza de ficheiros compilados manualmente

Se compilou o algoritmo genético com `javac`, pode remover os `.class` gerados com:

```bash
find genetic/src/main/java -name '*.class' -delete
```
