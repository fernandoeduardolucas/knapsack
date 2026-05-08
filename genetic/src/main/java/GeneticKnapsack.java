
/**
 * Implementação de um Algoritmo Genético para a resolução do Problema da Mochila 0/1.
 *
 * Este programa realiza a leitura de instâncias de teste, executa o processo evolutivo
 * (seleção, cruzamento e mutação) e exporta os resultados consolidados para um ficheiro CSV.
 * A estrutura foi desenhada para permitir a análise de métricas como a solução inicial,
 * a solução final encontrada, o desvio em relação ao ótimo conhecido e o tempo de execução.
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Classe principal que contém a lógica do Algoritmo Genético para o Problema da Mochila 0/1.
 * Implementa uma estratégia geracional com representação binária e operadores genéticos standard.
 */
public final class GeneticKnapsack {
    private static final Map<String, Long> OPTIMAL_VALUES = createOptimalValues();
    private static final Pattern NATURAL_PARTS = Pattern.compile("(\\d+|\\D+)");

    private GeneticKnapsack() {
        // Classe utilitária; não deve ser instanciada.
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);

        if (shouldRunExperimentsFromProperties(args)) {
            Path propertiesPath = resolvePropertiesPath(args);
            executeExperimentGrid(propertiesPath);
            return;
        }

        Config config = Config.fromArgs(args);

        executeAllInstances(
                config.instanceDir,
                config.outputCsv,
                config.populationSize,
                config.generations,
                config.crossoverRate,
                config.mutationRate,
                config.eliteSize,
                config.tournamentSize,
                config.maxWithoutImprovement,
                config.seed,
                config.verbose
        );
    }

    /**
     * Este método lê uma instância do problema da mochila a partir do ficheiro especificado.
     * O ficheiro contém primeiro o número de itens n, seguido de n linhas com o índice, valor e peso de cada item.
     * Por fim, lê a capacidade total da mochila na última linha.
     *
     * @param path caminho para o ficheiro da instância a testar
     * @return devolve um objeto {@code KnapsackInstance} com todos os dados carregados
     * @throws IOException se ocorrer um erro durante a leitura do ficheiro
     */
    private static KnapsackInstance readInstance(Path path) throws IOException {
        List<String> lines;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            lines = reader.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
        }

        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Ficheiro vazio: " + path);
        }

        int n = Integer.parseInt(lines.get(0));
        if (lines.size() < n + 2) {
            throw new IllegalArgumentException("Instância incompleta: " + path);
        }

        long[] values = new long[n];
        long[] weights = new long[n];
        for (int i = 0; i < n; i++) {
            String line = lines.get(i + 1);
            String[] parts = line.split("\\s+");
            if (parts.length != 3) {
                throw new IllegalArgumentException(
                        "Linha inválida em '" + path + "': '" + line + "'. Esperado: 'indice valor peso'."
                );
            }
            values[i] = Long.parseLong(parts[1]);
            weights[i] = Long.parseLong(parts[2]);
        }

        long capacity = Long.parseLong(lines.get(n + 1));
        return new KnapsackInstance(path.getFileName().toString(), n, capacity, values, weights);
    }

    /**
     * Este método repara uma solução para garantir que o peso total não excede a capacidade da mochila.
     * Primeiro, remove itens com o pior rácio valor/peso (usando a ordem greedy) até o peso ficar dentro do limite.
     * Em seguida, tenta adicionar novamente itens com bom rácio que ainda caibam, para maximizar o valor.
     *
     * @param genes   o vetor binário que representa a solução a ser reparada
     * @param instance a instância do problema com pesos, valores e capacidade
     * @return devolve um novo {@code Chromosome} contendo a solução reparada e válida
     */
    private static Chromosome repairSolution(int[] genes, KnapsackInstance instance) {
        int[] solution = genes.clone();
        long currentWeight = 0L;
        long currentValue = 0L;

        for (int i = 0; i < instance.n; i++) {
            if (solution[i] == 1) {
                currentWeight += instance.weights[i];
                currentValue += instance.values[i];
            }
        }

        if (currentWeight > instance.capacity) {
            for (int position = instance.greedyOrder.length - 1; position >= 0; position--) {
                int item = instance.greedyOrder[position];
                if (solution[item] == 1) {
                    solution[item] = 0;
                    currentWeight -= instance.weights[item];
                    currentValue -= instance.values[item];
                    if (currentWeight <= instance.capacity) {
                        break;
                    }
                }
            }
        }

        for (int item : instance.greedyOrder) {
            if (solution[item] == 0 && currentWeight + instance.weights[item] <= instance.capacity) {
                solution[item] = 1;
                currentWeight += instance.weights[item];
                currentValue += instance.values[item];
            }
        }

        return new Chromosome(solution, currentValue, currentWeight);
    }

    /**
     * Esta função gera uma solução aleatória inicial e aplica a reparação greedy.
     * Cada item tem uma probabilidade de ser incluído na mochila. No final, a solução
     * é validada e ajustada pelo método de reparação para garantir que respeita a capacidade.
     *
     * @param instance a instância do problema
     * @param random   o gerador de números aleatórios
     * @return devolve um {@code Chromosome} com uma solução válida gerada aleatoriamente
     */
    private static Chromosome createRandomSolution(KnapsackInstance instance, Random random) {
        int[] genes = new int[instance.n];
        for (int i = 0; i < instance.n; i++) {
            genes[i] = random.nextBoolean() ? 1 : 0;
        }
        return repairSolution(genes, instance);
    }

    /**
     * Inicializa a população do algoritmo genético com soluções aleatórias.
     * Cria um número determinado de indivíduos ({@code populationSize}), garantindo que
     * todos passam pelo processo de reparação.
     *
     * @param instance       a instância do problema
     * @param populationSize o tamanho da população a gerar
     * @param random         o gerador de números aleatórios
     * @return devolve uma lista contendo a população inicial
     */
    private static List<Chromosome> initializePopulation(KnapsackInstance instance, int populationSize, Random random) {
        List<Chromosome> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            population.add(createRandomSolution(instance, random));
        }
        return population;
    }

    /**
     * Realiza a seleção de um indivíduo através do método do torneio.
     * Escolhe aleatoriamente um conjunto de candidatos e devolve o melhor entre eles
     * para ser utilizado como progenitor no processo de cruzamento.
     *
     * @param population     a lista de indivíduos da geração atual
     * @param tournamentSize o número de candidatos a participar no torneio
     * @param random         o gerador de números aleatórios
     * @return devolve o {@code Chromosome} vencedor do torneio
     */
    private static Chromosome tournamentSelection(List<Chromosome> population, int tournamentSize, Random random) {
        if (tournamentSize > population.size()) {
            throw new IllegalArgumentException("tournamentSize não pode ser maior que populationSize");
        }

        List<Integer> indexes = new ArrayList<>(population.size());
        for (int i = 0; i < population.size(); i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes, random);

        Chromosome best = population.get(indexes.get(0));
        for (int i = 1; i < tournamentSize; i++) {
            Chromosome candidate = population.get(indexes.get(i));
            if (candidate.value > best.value) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Realiza o cruzamento de dois pontos entre dois progenitores.
     * Se o número de genes for inferior a 3, são devolvidas cópias dos progenitores.
     * Caso contrário, são selecionados dois pontos aleatórios para a troca de segmentos genéticos.
     *
     * @param parent1 o primeiro progenitor
     * @param parent2 o segundo progenitor
     * @param random  o gerador de números aleatórios
     * @return devolve uma matriz com dois vetores de genes resultantes do cruzamento
     */
    private static int[][] twoPointCrossover(Chromosome parent1, Chromosome parent2, Random random) {
        int n = parent1.genes.length;
        if (n < 3) {
            return new int[][]{parent1.genes.clone(), parent2.genes.clone()};
        }

        int p1 = 1 + random.nextInt(n - 1);
        int p2 = 1 + random.nextInt(n - 1);
        while (p2 == p1) {
            p2 = 1 + random.nextInt(n - 1);
        }
        if (p1 > p2) {
            int temp = p1;
            p1 = p2;
            p2 = temp;
        }

        int[] child1 = parent1.genes.clone();
        int[] child2 = parent2.genes.clone();
        for (int i = p1; i < p2; i++) {
            child1[i] = parent2.genes[i];
            child2[i] = parent1.genes[i];
        }
        return new int[][]{child1, child2};
    }

    /**
     * Aplica a mutação bit-flip, invertendo cada gene com base na probabilidade {@code mutationRate}.
     * Este processo introduz diversidade genética na população.
     *
     * @param genes         o vetor de genes a sofrer mutação
     * @param mutationRate  a taxa de mutação (valor entre 0 e 1)
     * @param random        o gerador de números aleatórios
     * @return devolve o vetor de genes após o processo de mutação
     */
    private static int[] bitFlipMutation(int[] genes, double mutationRate, Random random) {
        int[] mutated = genes.clone();
        for (int i = 0; i < mutated.length; i++) {
            if (random.nextDouble() < mutationRate) {
                mutated[i] = 1 - mutated[i];
            }
        }
        return mutated;
    }

    private static GeneticResult geneticAlgorithm(
            KnapsackInstance instance,
            int populationSize,
            int generations,
            double crossoverRate,
            double mutationRate,
            int eliteSize,
            int tournamentSize,
            int maxWithoutImprovement,
            long seed,
            boolean verbose
    ) {
        // Valida se os parâmetros de configuração fazem sentido
        validateParameters(populationSize, generations, crossoverRate, mutationRate, eliteSize, tournamentSize, maxWithoutImprovement);

        // Inicializa o gerador de números aleatórios com a semente para garantir resultados consistentes/reprodutíveis
        Random random = new Random(seed);

        // Passo 1. Inicialização: Gera a população inicial aleatoriamente, garantindo viabilidade (reparação greedy)
        List<Chromosome> population = initializePopulation(instance, populationSize, random);

        // Encontra o melhor indivíduo da população inicial
        Chromosome best = population.stream().max(Comparator.comparingLong(chromosome -> chromosome.value)).orElseThrow();
        // Guarda o valor da solução inicial para ser reportado na tabela de resultados final
        long initialValue = best.value;

        // Lista para registar a evolução do melhor valor ao longo das gerações
        List<GenerationRecord> history = new ArrayList<>();
        history.add(new GenerationRecord(0, initialValue));

        int generationsWithoutImprovement = 0;
        int stopGeneration = 0;

        // Passo 2. Loop Geracional: Evolui a população geração a geração
        for (int generation = 1; generation <= generations; generation++) {
            stopGeneration = generation;

            // Ordena a população atual de forma decrescente pelo valor (melhores indivíduos no início da lista)
            List<Chromosome> sortedPopulation = new ArrayList<>(population);
            sortedPopulation.sort(Comparator.comparingLong((Chromosome chromosome) -> chromosome.value).reversed());

            // Prepara a lista para albergar a nova geração
            List<Chromosome> newPopulation = new ArrayList<>(populationSize);

            // Passo 2.a) Elitismo: Copia diretamente os 'eliteSize' melhores indivíduos para a próxima geração intactos
            for (int i = 0; i < eliteSize; i++) {
                newPopulation.add(sortedPopulation.get(i).copy());
            }

            // Passo 2.b) Preenche o resto da população através de Operadores Genéticos (Seleção, Cruzamento, Mutação)
            while (newPopulation.size() < populationSize) {
                // Seleção por Torneio: Seleção de dois progenitores com base na aptidão (fitness)
                Chromosome parent1 = tournamentSelection(population, tournamentSize, random);
                Chromosome parent2 = tournamentSelection(population, tournamentSize, random);

                int[] childGenes1;
                int[] childGenes2;

                // Cruzamento (Crossover) de 2 pontos: Combinação do material genético dos progenitores
                if (random.nextDouble() < crossoverRate) {
                    int[][] children = twoPointCrossover(parent1, parent2, random);
                    childGenes1 = children[0];
                    childGenes2 = children[1];
                } else {
                    // Na ausência de cruzamento, os descendentes são cópias dos progenitores
                    childGenes1 = parent1.genes.clone();
                    childGenes2 = parent2.genes.clone();
                }

                // Mutação (Bit-flip): Inverte o valor de alguns bits de forma aleatória segundo a 'mutationRate'
                childGenes1 = bitFlipMutation(childGenes1, mutationRate, random);
                childGenes2 = bitFlipMutation(childGenes2, mutationRate, random);

                // Reparação e Adição: Verifica se o peso excede a capacidade e repara a solução usando a heurística greedy
                newPopulation.add(repairSolution(childGenes1, instance));
                if (newPopulation.size() < populationSize) {
                    newPopulation.add(repairSolution(childGenes2, instance));
                }
            }

            // Passo 2.c) A nova população substitui por completo a geração antiga
            population = newPopulation;

            // Avalia o melhor indivíduo da geração atual
            Chromosome generationBest = population.stream().max(Comparator.comparingLong(chromosome -> chromosome.value)).orElseThrow();

            // Passo 2.d) Verifica se a solução global foi melhorada nesta geração
            if (generationBest.value > best.value) {
                best = generationBest.copy();
                generationsWithoutImprovement = 0; // Se melhorou, reinicia o contador de estagnação
            } else {
                generationsWithoutImprovement++; // Se não, incrementa a estagnação
            }

            // Regista o melhor valor desta geração para o histórico académico
            history.add(new GenerationRecord(generation, best.value));

            // Critério de Paragem 1: Estagnação. O algoritmo finaliza a execução se não houver melhoria
            if (generationsWithoutImprovement >= maxWithoutImprovement) {
                if (verbose) {
                    System.out.printf(
                            "    [Paragem antecipada] Geração %d — sem melhoria há %d gerações.%n",
                            generation,
                            maxWithoutImprovement
                    );
                }
                break;
            }

            // Imprime progresso no ecrã a cada 100 gerações (se ativada a opção verbose)
            if (verbose && generation % 100 == 0) {
                System.out.printf(
                        "    Geração %4d | Melhor valor: %,d | Sem melhoria: %d%n",
                        generation,
                        best.value,
                        generationsWithoutImprovement
                );
            }
        } // Critério de Paragem 2: Número máximo de gerações atingido

        // Retorna o resultado final consolidado para processamento posterior, incluindo o histórico
        return new GeneticResult(best.genes, best.value, best.weight, stopGeneration, initialValue, history);
    }

    /**
     * Processa sequencialmente todas as instâncias localizadas no diretório especificado.
     * Para cada instância, o algoritmo genético é executado e os resultados são registados.
     * No final, os dados compilados são exportados para um ficheiro CSV.
     *
     * @param instanceDir diretório que contém os ficheiros das instâncias
     * @param outputCsv   caminho do ficheiro CSV para gravação dos resultados
     * @param populationSize o tamanho da população
     * @param generations o número máximo de gerações
     * @param crossoverRate a taxa de cruzamento
     * @param mutationRate a taxa de mutação
     * @param eliteSize   o número de indivíduos preservados por elitismo
     * @param tournamentSize o tamanho do torneio para a seleção
     * @param maxWithoutImprovement limite de gerações sem melhoria para paragem antecipada
     * @param seed a semente aleatória para garantir a reprodutibilidade dos testes
     * @param verbose indica se deve ser exibido o progresso detalhado durante a execução
     * @throws IOException se ocorrer um erro no acesso aos ficheiros
     */
    private static void executeAllInstances(
            Path instanceDir,
            Path outputCsv,
            int populationSize,
            int generations,
            double crossoverRate,
            double mutationRate,
            int eliteSize,
            int tournamentSize,
            int maxWithoutImprovement,
            long seed,
            boolean verbose
    ) throws IOException {
        if (!Files.isDirectory(instanceDir)) {
            throw new IOException("A pasta de instâncias '" + instanceDir + "' não existe.");
        }

        List<Path> files;
        try (var stream = Files.list(instanceDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), GeneticKnapsack::naturalCompare))
                    .collect(Collectors.toList());
        }

        if (files.isEmpty()) {
            System.out.println("Não foram encontrados ficheiros de instâncias na pasta.");
            return;
        }

        String separator = "=".repeat(115);
        System.out.println(separator);
        System.out.printf(
                "  Parâmetros do AG: pop=%d, gerações=%d, pC=%.4f, pM=%.4f, elite=%d, torneio=%d, estagnação=%d, seed=%d%n",
                populationSize,
                generations,
                crossoverRate,
                mutationRate,
                eliteSize,
                tournamentSize,
                maxWithoutImprovement,
                seed
        );
        System.out.println(separator);
        System.out.printf(
                "%-14s %18s %15s %22s %10s %6s %10s%n",
                "Instância",
                "Sol. Ótima (SO)",
                "Sol. Inicial",
                "Sol. Encontrada (SE)",
                "% Desvio",
                "Ger.",
                "Tempo (s)"
        );
        System.out.println(separator);

        List<ResultRow> results = new ArrayList<>();
        for (Path file : files) {
            KnapsackInstance instance = readInstance(file);
            System.out.println("  A processar: " + instance.name + "...");

            Instant start = Instant.now();
            GeneticResult result = geneticAlgorithm(
                    instance,
                    populationSize,
                    generations,
                    crossoverRate,
                    mutationRate,
                    eliteSize,
                    tournamentSize,
                    maxWithoutImprovement,
                    seed,
                    verbose
            );
            double elapsedSeconds = Duration.between(start, Instant.now()).toNanos() / 1_000_000_000.0;

            Long optimal = OPTIMAL_VALUES.get(instance.name);
            Long difference = optimal == null ? null : optimal - result.value;
            Double deviation = optimal == null ? null : (difference / (double) optimal) * 100.0;

            // Criamos a linha de resultado com todos os dados técnicos e académicos
            ResultRow row = new ResultRow(
                    instance.name,
                    instance.n,
                    instance.capacity,
                    optimal,
                    result.initialValue,
                    result.value,
                    difference,
                    deviation,
                    result.weight,
                    result.weight <= instance.capacity, // Viabilidade
                    result.stopGeneration,
                    generations,
                    populationSize,
                    crossoverRate,
                    mutationRate,
                    eliteSize,
                    tournamentSize,
                    maxWithoutImprovement,
                    seed,
                    elapsedSeconds,
                    result.history
            );
            results.add(row);

            System.out.printf(
                    "  %-12s %18s %,15d %,22d %10s %6d %10.4f%n",
                    instance.name,
                    optimal == null ? "N/A" : String.format("%,d", optimal),
                    result.initialValue,
                    result.value,
                    deviation == null ? "N/A" : String.format("%.6f%%", deviation),
                    result.stopGeneration,
                    elapsedSeconds
            );
        }
        System.out.println(separator);

        // Caminhos para os novos relatórios solicitados
        Path outputDir = outputCsv.toAbsolutePath().getParent();
        if (outputDir == null) {
            outputDir = Paths.get(".");
        }
        Path gridPath = outputDir.resolve("ga-grid-results.csv");
        Path detailedPath = outputDir.resolve("ga-detailed-results.csv");
        Path mdPath = outputDir.resolve("ga-relatorio.md");

        // Escrita dos ficheiros solicitados
        appendGridCsv(gridPath, results);
        updateDetailedCsv(detailedPath, results);
        generateMarkdownReport(mdPath, detailedPath);

        System.out.println("\n  Relatórios académicos atualizados/gerados:");
        System.out.println("  1. Grid:      " + gridPath);
        System.out.println("  2. Detalhado: " + detailedPath);
        System.out.println("  3. Relatório: " + mdPath + "\n");
    }

    /**
     * Decide se a execução deve usar o ficheiro de propriedades do AG.
     *
     * <p>Sem argumentos, o comportamento fica alinhado com os runners de MMAS/TS:
     * se existir {@code genetic/src/main/resources/genetic-experiments.properties},
     * é executada a grelha definida nesse ficheiro. Também é possível indicar
     * explicitamente o ficheiro com {@code --config caminho.properties} ou passando
     * diretamente um único argumento terminado em {@code .properties}.</p>
     */
    private static boolean shouldRunExperimentsFromProperties(String[] args) {
        if (args.length == 0) {
            return Files.exists(defaultPropertiesPath());
        }
        if (args.length == 1) {
            return args[0].endsWith(".properties");
        }
        return "--config".equals(args[0]);
    }

    private static Path resolvePropertiesPath(String[] args) {
        if (args.length == 0) {
            return defaultPropertiesPath();
        }
        if ("--config".equals(args[0])) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Falta o caminho do ficheiro após --config");
            }
            return Paths.get(args[1]);
        }
        return Paths.get(args[0]);
    }

    private static Path defaultPropertiesPath() {
        return Config.resolveDefaultPath("genetic", "src", "main", "resources", "genetic-experiments.properties");
    }

    /**
     * Executa a grelha de experiências definida no ficheiro
     * {@code genetic-experiments.properties}.
     */
    private static void executeExperimentGrid(Path propertiesPath) throws IOException, InterruptedException {
        Properties properties = loadProperties(propertiesPath);

        List<Path> instances = resolveConfiguredInstances(properties);
        List<Integer> populations = parseIntList(properties, "genetic.population", 50);
        List<Integer> generationsList = parseIntList(properties, "genetic.generations", 500);
        List<Double> crossoverRates = parseDoubleList(properties, "genetic.crossover", 0.85);
        List<Double> mutationRates = parseDoubleList(properties, "genetic.mutation", 0.005);
        List<Integer> eliteSizes = parseIntList(properties, "genetic.elite", 3);
        List<Integer> tournamentSizes = parseIntList(properties, "genetic.tournament", 3);
        List<Integer> stagnationLimits = parseIntList(properties, "genetic.stagnation", 100);
        List<Long> seeds = parseLongList(properties, "genetic.seed", 42L);
        int parallelism = Integer.parseInt(properties.getProperty("genetic.parallelism", "1").trim());
        if (parallelism <= 0) {
            throw new IllegalArgumentException("genetic.parallelism deve ser > 0");
        }

        Path outputCsv = Paths.get(properties.getProperty("genetic.output.csv", "results/genetic/ag_resultados.csv").trim());
        Path outputDir = outputCsv.toAbsolutePath().getParent();
        if (outputDir == null) {
            outputDir = Paths.get(".");
        }
        Files.createDirectories(outputDir);
        Path gridPath = outputDir.resolve("ga-grid-results.csv");
        Path detailedPath = outputDir.resolve("ga-detailed-results.csv");
        Path mdPath = outputDir.resolve("ga-relatorio.md");

        int totalRuns = instances.size()
                * populations.size()
                * generationsList.size()
                * crossoverRates.size()
                * mutationRates.size()
                * eliteSizes.size()
                * tournamentSizes.size()
                * stagnationLimits.size()
                * seeds.size();

        System.out.printf(
                "Iniciando experiências AG (%d execuções, paralelismo=%d, properties=%s)...%n",
                totalRuns,
                parallelism,
                propertiesPath
        );

        List<GeneticExperimentTask> tasks = new ArrayList<>(totalRuns);
        for (Path instancePath : instances) {
            KnapsackInstance instance = readInstance(instancePath);
            for (int population : populations) {
                for (int generations : generationsList) {
                    for (double crossover : crossoverRates) {
                        for (double mutation : mutationRates) {
                            for (int elite : eliteSizes) {
                                for (int tournament : tournamentSizes) {
                                    for (int stagnation : stagnationLimits) {
                                        for (long seed : seeds) {
                                            tasks.add(new GeneticExperimentTask(
                                                    instance,
                                                    population,
                                                    generations,
                                                    crossover,
                                                    mutation,
                                                    elite,
                                                    tournament,
                                                    stagnation,
                                                    seed
                                            ));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        List<ResultRow> results = executeExperimentTasks(tasks, parallelism, totalRuns);
        appendGridCsv(gridPath, results);
        updateDetailedCsv(detailedPath, results);
        generateMarkdownReport(mdPath, detailedPath);

        System.out.println("Experiências AG concluídas.");
        System.out.println("  Grid:      " + gridPath);
        System.out.println("  Detalhado: " + detailedPath);
        System.out.println("  Relatório: " + mdPath);
    }

    private static List<ResultRow> executeExperimentTasks(
            List<GeneticExperimentTask> tasks,
            int parallelism,
            int totalRuns
    ) throws InterruptedException {
        List<ResultRow> results = new ArrayList<>(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CompletionService<ResultRow> completion = new ExecutorCompletionService<>(executor);
        try {
            for (GeneticExperimentTask task : tasks) {
                completion.submit(task::execute);
            }

            for (int completed = 1; completed <= tasks.size(); completed++) {
                ResultRow row;
                try {
                    row = completion.take().get();
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Falha ao executar configuração do Algoritmo Genético", e.getCause());
                }
                results.add(row);

                System.out.printf(
                        Locale.US,
                        "[%d/%d] %s | pop=%d gen=%d cross=%.4f mut=%.4f elite=%d torneio=%d stag=%d seed=%d => valor=%d, %.4f s%n",
                        completed,
                        totalRuns,
                        row.instance,
                        row.populationSize,
                        row.maxGenerations,
                        row.crossoverRate,
                        row.mutationRate,
                        row.eliteSize,
                        row.tournamentSize,
                        row.maxWithoutImprovement,
                        row.seed,
                        row.foundValue,
                        row.elapsedSeconds
                );
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Properties loadProperties(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Ficheiro de propriedades não encontrado: " + path);
        }

        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static List<Path> resolveConfiguredInstances(Properties properties) throws IOException {
        List<String> explicitInstances = parseStringList(properties, "genetic.instances");
        if (!explicitInstances.isEmpty()) {
            return explicitInstances.stream().map(Paths::get).collect(Collectors.toList());
        }

        String instancesDir = properties.getProperty("genetic.instances.dir", "").trim();
        if (instancesDir.isEmpty()) {
            throw new IllegalArgumentException("Defina genetic.instances ou genetic.instances.dir");
        }

        Path base = Paths.get(instancesDir);
        if (!Files.isDirectory(base)) {
            throw new IllegalArgumentException("Diretoria de instâncias não existe: " + base);
        }

        try (Stream<Path> paths = Files.list(base)) {
            List<Path> instances = paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), GeneticKnapsack::naturalCompare))
                    .collect(Collectors.toList());
            if (instances.isEmpty()) {
                throw new IllegalArgumentException("Nenhuma instância encontrada em: " + base);
            }
            return instances;
        }
    }

    private static List<String> parseStringList(Properties properties, String key) {
        String value = properties.getProperty(key, "").trim();
        List<String> parsed = new ArrayList<>();
        if (value.isEmpty()) {
            return parsed;
        }
        for (String token : value.split(",")) {
            String cleaned = token.trim();
            if (!cleaned.isEmpty()) {
                parsed.add(cleaned);
            }
        }
        return parsed;
    }

    private static List<Integer> parseIntList(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key, String.valueOf(fallback)).trim();
        List<Integer> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Integer.parseInt(token.trim()));
        }
        return parsed;
    }

    private static List<Long> parseLongList(Properties properties, String key, long fallback) {
        String value = properties.getProperty(key, String.valueOf(fallback)).trim();
        List<Long> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Long.parseLong(token.trim()));
        }
        return parsed;
    }

    private static List<Double> parseDoubleList(Properties properties, String key, double fallback) {
        String value = properties.getProperty(key, String.valueOf(fallback)).trim();
        List<Double> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Double.parseDouble(token.trim()));
        }
        return parsed;
    }

    private record GeneticExperimentTask(
            KnapsackInstance instance,
            int populationSize,
            int generations,
            double crossoverRate,
            double mutationRate,
            int eliteSize,
            int tournamentSize,
            int maxWithoutImprovement,
            long seed
    ) {
        private ResultRow execute() {
            Instant start = Instant.now();
            GeneticResult result = geneticAlgorithm(
                    instance,
                    populationSize,
                    generations,
                    crossoverRate,
                    mutationRate,
                    eliteSize,
                    tournamentSize,
                    maxWithoutImprovement,
                    seed,
                    false
            );
            double elapsedSeconds = Duration.between(start, Instant.now()).toNanos() / 1_000_000_000.0;

            Long optimal = OPTIMAL_VALUES.get(instance.name);
            Long difference = optimal == null ? null : optimal - result.value;
            Double deviation = optimal == null ? null : (difference / (double) optimal) * 100.0;

            return new ResultRow(
                    instance.name,
                    instance.n,
                    instance.capacity,
                    optimal,
                    result.initialValue,
                    result.value,
                    difference,
                    deviation,
                    result.weight,
                    result.weight <= instance.capacity,
                    result.stopGeneration,
                    generations,
                    populationSize,
                    crossoverRate,
                    mutationRate,
                    eliteSize,
                    tournamentSize,
                    maxWithoutImprovement,
                    seed,
                    elapsedSeconds,
                    result.history
            );
        }
    }

    /**
     * Adiciona os resultados da execução atual ao ficheiro da grelha de testes (grid).
     */
    private static void appendGridCsv(Path path, List<ResultRow> results) throws IOException {
        ensureDirectoryExists(path);
        boolean exists = Files.exists(path) && Files.size(path) > 0;

        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND)) {
            if (!exists) {
                writer.write("instance,population_size,generations,crossover_rate,mutation_rate,elite_size,tournament_size,stagnation,seed,initial_value,best_value,total_weight,optimal_value,comparison,difference,gap_percent,stop_generation,elapsed_s");
                writer.newLine();
            }
            for (ResultRow row : results) {
                String comparison = "IGUAL";
                if (row.optimal != null) {
                    if (row.foundValue > row.optimal) comparison = "ACIMA"; // Maximizar: se for maior, está acima (incomum)
                    else if (row.foundValue < row.optimal) comparison = "ABAIXO";
                }

                writer.write(String.join(",",
                        row.instance,
                        Integer.toString(row.populationSize),
                        Integer.toString(row.maxGenerations),
                        String.format(Locale.US, "%.4f", row.crossoverRate),
                        String.format(Locale.US, "%.4f", row.mutationRate),
                        Integer.toString(row.eliteSize),
                        Integer.toString(row.tournamentSize),
                        Integer.toString(row.maxWithoutImprovement),
                        Long.toString(row.seed),
                        Long.toString(row.initialValue),
                        Long.toString(row.foundValue),
                        Long.toString(row.foundWeight),
                        row.optimal == null ? "" : row.optimal.toString(),
                        comparison,
                        row.differenceToOptimal == null ? "" : row.differenceToOptimal.toString(),
                        row.deviationPercent == null ? "" : String.format(Locale.US, "%.6f", row.deviationPercent),
                        Integer.toString(row.stopGeneration),
                        String.format(Locale.US, "%.4f", row.elapsedSeconds)
                ));
                writer.newLine();
            }
        }
    }

    /**
     * Atualiza o ficheiro detalhado, mantendo apenas a melhor configuração por instância.
     */
    private static void updateDetailedCsv(Path path, List<ResultRow> currentResults) throws IOException {
        ensureDirectoryExists(path);
        Map<String, String> bestLines = new LinkedHashMap<>();
        Map<String, Long> bestValues = new HashMap<>();

        // Lê os resultados existentes, se houver
        if (Files.exists(path)) {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                String instance = parts[0];
                long bValue = Long.parseLong(parts[2]);
                bestLines.put(instance, line);
                bestValues.put(instance, bValue);
            }
        }

        // Compara com os resultados da execução atual
        for (ResultRow row : currentResults) {
            long currentBest = bestValues.getOrDefault(row.instance, -1L);
            if (row.foundValue > currentBest) {
                String configStr = String.format(Locale.US, "pop=%d gen=%d cRate=%.2f mRate=%.3f elite=%d tourn=%d stag=%d seed=%d",
                        row.populationSize, row.maxGenerations, row.crossoverRate, row.mutationRate, row.eliteSize, row.tournamentSize, row.maxWithoutImprovement, row.seed);

                String newLine = String.join(",",
                        row.instance,
                        row.optimal == null ? "" : row.optimal.toString(),
                        Long.toString(row.foundValue),
                        row.deviationPercent == null ? "" : String.format(Locale.US, "%.6f", row.deviationPercent),
                        Long.toString(row.foundWeight),
                        configStr,
                        Integer.toString(row.stopGeneration),
                        String.format(Locale.US, "%.4f", row.elapsedSeconds)
                );
                bestLines.put(row.instance, newLine);
                bestValues.put(row.instance, row.foundValue);
            }
        }

        // Escreve as melhores soluções guardadas
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("instance,optimal_value,best_value,gap_percent,total_weight,best_configuration,stop_generation,elapsed_time");
            writer.newLine();
            for (String line : bestLines.values()) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    /**
     * Gera automaticamente um relatório em Markdown focado na análise dos resultados obtidos.
     */
    private static void generateMarkdownReport(Path mdPath, Path detailedPath) throws IOException {
        ensureDirectoryExists(mdPath);

        List<String> resultsTable = new ArrayList<>();
        double avgGap = 0.0;
        int countGaps = 0;
        int countOptimalHit = 0;

        if (Files.exists(detailedPath)) {
            List<String> lines = Files.readAllLines(detailedPath, StandardCharsets.UTF_8);
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).trim().isEmpty()) continue;
                String[] parts = lines.get(i).split(",");
                String inst = parts[0];
                String opt = parts[1];
                String best = parts[2];
                String gapStr = parts[3];
                String conf = parts[5];
                String tempo = parts[7];

                resultsTable.add(String.format("| %s | %s | %s | %s%% | %s | %s |", inst, opt, best, gapStr, tempo, conf));

                if (!gapStr.isEmpty()) {
                    double gap = Double.parseDouble(gapStr);
                    avgGap += gap;
                    countGaps++;
                    if (gap <= 0.0001) countOptimalHit++;
                }
            }
        }
        if (countGaps > 0) avgGap /= countGaps;

        try (BufferedWriter writer = Files.newBufferedWriter(mdPath, StandardCharsets.UTF_8)) {
            writer.write("# Relatório: Algoritmo Genético - Problema da Mochila 0/1\n\n");

            writer.write("## 1. Contexto e Abordagem\n");
            writer.write("Neste trabalho, implementou-se um **Algoritmo Genético (AG)** para resolver o Problema da Mochila 0/1. ");
            writer.write("A estrutura foca-se na otimização da escolha de itens, sujeita a uma restrição de capacidade total.\n\n");
            writer.write("**Características do AG:**\n");
            writer.write("- **Representação:** Binária (1 se o item está na mochila, 0 caso contrário)\n");
            writer.write("- **População e Evolução:** Estratégia geracional estrita com substituição total\n");
            writer.write("- **Seleção:** Torneio (garante pressão seletiva ajustável)\n");
            writer.write("- **Cruzamento (Crossover):** 2 pontos (maior preservação de blocos genéticos face a 1 ponto)\n");
            writer.write("- **Mutação:** Bit-flip (exploração do espaço de procura)\n");
            writer.write("- **Reparação:** Heurística Greedy (garante viabilidade retirando os itens de menor rácio valor/peso e adicionando os melhores)\n");
            writer.write("- **Elitismo:** Preservação dos melhores indivíduos para evitar perda da melhor solução\n\n");

            writer.write("## 2. Resultados Consolidados (Melhores Configurações)\n\n");
            writer.write("A tabela abaixo resume as melhores soluções encontradas para cada instância, após análise da grelha de testes.\n\n");
            writer.write("| Instância | Ótimo | Melhor Valor (AG) | GAP (%) | Tempo (s) | Melhor Configuração (AG) |\n");
            writer.write("|-----------|-------|-------------------|---------|-----------|--------------------------|\n");
            for (String row : resultsTable) {
                writer.write(row + "\n");
            }
            writer.write("\n");

            writer.write("## 3. Análise e Discussão\n\n");
            writer.write(String.format("- **Desvio Médio Geral (GAP):** %.4f%%\n", avgGap));
            writer.write(String.format("- **Ótimos Alcançados (ou quase ótimos):** %d de %d instâncias analisadas.\n\n", countOptimalHit, countGaps));
            writer.write("### Impacto dos Parâmetros\n");
            writer.write("- **Tamanho da População & Gerações:** Populações maiores aumentam a diversidade inicial e previnem a convergência prematura, mas têm um custo computacional linearmente superior.\n");
            writer.write("- **Pressão de Seleção (Torneio vs Elitismo):** Torneios maiores forçam a convergência rápida. O elitismo atuou como uma rede de segurança vital, impedindo que mutações destrutivas afetassem a melhor solução já encontrada.\n");
            writer.write("- **Reparação Greedy:** A reparação não só garante a viabilidade das soluções, como injeta inteligência heurística no processo evolutivo, acelerando drasticamente a aproximação aos valores ótimos.\n\n");

            writer.write("### Conclusões Relevantes\n");
            writer.write("O Algoritmo Genético mostrou ser altamente competitivo, especialmente quando a fase de exploração (crossover e mutação) é equilibrada por uma heurística de reparação local eficiente. Como trabalho futuro, seria interessante incorporar parâmetros auto-adaptáveis ou testar operadores de cruzamento uniforme para avaliar o impacto na quebra de simetria nas instâncias mais densas.\n");
        }
    }

    /**
     * Utilitário para garantir que a pasta de destino existe.
     */
    private static void ensureDirectoryExists(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Valida a integridade dos parâmetros de configuração do algoritmo.
     * Garante que os valores (população, gerações, taxas e elitismo) estão dentro
     * dos intervalos aceitáveis para o funcionamento correto do processo evolutivo.
     *
     * @throws IllegalArgumentException se algum parâmetro apresentar um valor inválido
     */
    private static void validateParameters(
            int populationSize,
            int generations,
            double crossoverRate,
            double mutationRate,
            int eliteSize,
            int tournamentSize,
            int maxWithoutImprovement
    ) {
        if (populationSize <= 0) {
            throw new IllegalArgumentException("populationSize deve ser positivo");
        }
        if (generations <= 0) {
            throw new IllegalArgumentException("generations deve ser positivo");
        }
        if (crossoverRate < 0.0 || crossoverRate > 1.0) {
            throw new IllegalArgumentException("crossoverRate deve estar entre 0 e 1");
        }
        if (mutationRate < 0.0 || mutationRate > 1.0) {
            throw new IllegalArgumentException("mutationRate deve estar entre 0 e 1");
        }
        if (eliteSize < 0 || eliteSize > populationSize) {
            throw new IllegalArgumentException("eliteSize deve estar entre 0 e populationSize");
        }
        if (tournamentSize <= 0 || tournamentSize > populationSize) {
            throw new IllegalArgumentException("tournamentSize deve estar entre 1 e populationSize");
        }
        if (maxWithoutImprovement <= 0) {
            throw new IllegalArgumentException("maxWithoutImprovement deve ser positivo");
        }
    }

    /**
     * Realiza a comparação natural de strings (alfanumérica).
     * É essencial para ordenar corretamente ficheiros como "n_1000_2" e "n_1000_10".
     *
     * @param left  a primeira string para comparação
     * @param right a segunda string para comparação
     * @return devolve um valor negativo, zero ou positivo consoante a ordem
     */
    private static int naturalCompare(String left, String right) {
        Matcher leftMatcher = NATURAL_PARTS.matcher(left);
        Matcher rightMatcher = NATURAL_PARTS.matcher(right);

        while (leftMatcher.find() && rightMatcher.find()) {
            String leftPart = leftMatcher.group();
            String rightPart = rightMatcher.group();
            int comparison;
            if (Character.isDigit(leftPart.charAt(0)) && Character.isDigit(rightPart.charAt(0))) {
                comparison = Long.compare(Long.parseLong(leftPart), Long.parseLong(rightPart));
            } else {
                comparison = leftPart.compareToIgnoreCase(rightPart);
            }
            if (comparison != 0) {
                return comparison;
            }
        }
        return left.compareToIgnoreCase(right);
    }

    /**
     * Inicializa um mapa com os valores ótimos conhecidos (SO) para cada instância.
     * Estes valores, retirados da documentação do problema, servem de base para
     * calcular o desvio percentual das soluções encontradas pelo algoritmo.
     *
     * @return devolve um mapa com a correspondência entre instância e valor ótimo
     */
    private static Map<String, Long> createOptimalValues() {
        Map<String, Long> optimalValues = new HashMap<>();
        optimalValues.put("n_1000_1", 9_999_946_233L);
        optimalValues.put("n_1000_2", 9_999_964_987L);
        optimalValues.put("n_1000_3", 9_999_229_281L);
        optimalValues.put("n_1000_4", 9_999_239_905L);
        optimalValues.put("n_1000_5", 9_999_251_796L);
        optimalValues.put("n_1000_6", 9_996_100_344L);
        optimalValues.put("n_1000_7", 9_996_105_266L);
        optimalValues.put("n_1000_8", 9_996_111_502L);
        optimalValues.put("n_1000_9", 9_980_488_131L);
        optimalValues.put("n_1000_10", 9_980_507_700L);
        return optimalValues;
    }

    /**
     * Encapsula as definições e parâmetros de configuração do algoritmo.
     * Permite a gestão centralizada de opções como diretórios de instâncias,
     * ficheiros de saída e hiperparâmetros do processo genético.
     */
    private static final class Config {
        private final Path instanceDir;
        private final Path outputCsv;
        private final int populationSize;
        private final int generations;
        private final double crossoverRate;
        private final double mutationRate;
        private final int eliteSize;
        private final int tournamentSize;
        private final int maxWithoutImprovement;
        private final long seed;
        private final boolean verbose;

        private Config(Map<String, String> options) {
            Path defaultInst = resolveDefaultPath("docs", "inst_test", "instancias");
            Path defaultOut = resolveDefaultPath("results", "genetic", "ag_resultados.csv");

            this.instanceDir = Paths.get(options.getOrDefault("--instances", defaultInst.toString()));
            this.outputCsv = Paths.get(options.getOrDefault("--output", defaultOut.toString()));
            this.populationSize = Integer.parseInt(options.getOrDefault("--population", "50"));
            this.generations = Integer.parseInt(options.getOrDefault("--generations", "500"));
            this.crossoverRate = Double.parseDouble(options.getOrDefault("--crossover", "0.85"));
            this.mutationRate = Double.parseDouble(options.getOrDefault("--mutation", "0.005"));
            this.eliteSize = Integer.parseInt(options.getOrDefault("--elite", "3"));
            this.tournamentSize = Integer.parseInt(options.getOrDefault("--tournament", "3"));
            this.maxWithoutImprovement = Integer.parseInt(options.getOrDefault("--stagnation", "100"));
            this.seed = Long.parseLong(options.getOrDefault("--seed", "42"));
            this.verbose = Boolean.parseBoolean(options.getOrDefault("--verbose", "false"));
        }

        /**
         * Resolve caminhos padrão de forma portável, sem depender de um caminho absoluto
         * específico da máquina do autor. Primeiro tenta localizar a raiz do repositório
         * a partir do diretório de execução; se não conseguir, mantém o caminho relativo
         * para continuar compatível com execuções a partir da raiz do projeto.
         */
        private static Path resolveDefaultPath(String first, String... more) {
            Path relativePath = Paths.get(first, more);
            Path repositoryRoot = findRepositoryRoot(relativePath);
            if (repositoryRoot != null) {
                return repositoryRoot.resolve(relativePath);
            }
            return relativePath;
        }

        private static Path findRepositoryRoot(Path relativePath) {
            List<Path> searchRoots = new ArrayList<>();
            searchRoots.add(Paths.get(System.getProperty("user.dir")));

            Path codeSourcePath = getCodeSourcePath();
            if (codeSourcePath != null) {
                searchRoots.add(codeSourcePath);
            }

            for (Path searchRoot : searchRoots) {
                Path current = searchRoot.toAbsolutePath().normalize();
                while (current != null) {
                    if (Files.exists(current.resolve(relativePath))) {
                        return current;
                    }
                    current = current.getParent();
                }
            }
            return null;
        }

        private static Path getCodeSourcePath() {
            try {
                return Paths.get(GeneticKnapsack.class
                        .getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI());
            } catch (Exception ignored) {
                return null;
            }
        }

        private static Config fromArgs(String[] args) {
            Map<String, String> options = new LinkedHashMap<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    printHelpAndExit();
                }
                if (!arg.startsWith("--")) {
                    throw new IllegalArgumentException("Argumento inválido: " + arg);
                }
                if ("--verbose".equals(arg)) {
                    options.put(arg, "true");
                    continue;
                }
                if (i + 1 >= args.length) {
                    throw new IllegalArgumentException("Falta valor para " + arg);
                }
                options.put(arg, args[++i]);
            }
            return new Config(options);
        }

        private static void printHelpAndExit() {

            System.exit(0);
        }
    }

    /**
     * Define a estrutura de uma instância do problema da mochila.
     * Armazena a capacidade total, os valores e pesos dos itens, além de
     * calcular a ordem de prioridade (greedy) para processos de reparação.
     */
    private static final class KnapsackInstance {
        private final String name;
        private final int n;
        private final long capacity;
        private final long[] values;
        private final long[] weights;
        private final int[] greedyOrder;

        private KnapsackInstance(String name, int n, long capacity, long[] values, long[] weights) {
            this.name = Objects.requireNonNull(name);
            this.n = n;
            this.capacity = capacity;
            this.values = values.clone();
            this.weights = weights.clone();
            this.greedyOrder = calculateGreedyOrder(n, values, weights);
        }

        private static int[] calculateGreedyOrder(int n, long[] values, long[] weights) {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) {
                order[i] = i;
            }
            Arrays.sort(order, (left, right) -> Double.compare(ratio(values, weights, right), ratio(values, weights, left)));
            int[] primitiveOrder = new int[n];
            for (int i = 0; i < n; i++) {
                primitiveOrder[i] = order[i];
            }
            return primitiveOrder;
        }

        private static double ratio(long[] values, long[] weights, int index) {
            if (weights[index] == 0L) {
                return Double.POSITIVE_INFINITY;
            }
            return values[index] / (double) weights[index];
        }
    }

    /**
     * Representa um indivíduo (solução potencial) na população do algoritmo genético.
     * Cada cromossoma armazena o seu vetor de genes, o valor total e o peso acumulado.
     */
    private static final class Chromosome {
        private final int[] genes;
        private final long value;
        private final long weight;

        private Chromosome(int[] genes, long value, long weight) {
            this.genes = genes.clone();
            this.value = value;
            this.weight = weight;
        }

        private Chromosome copy() {
            return new Chromosome(genes, value, weight);
        }
    }

    /**
     * Representa o registo do melhor valor encontrado numa determinada geração.
     * Utilizado para gerar o gráfico de evolução do algoritmo.
     */
    private static final class GenerationRecord {
        private final int generation;
        private final long bestValue;

        private GenerationRecord(int generation, long bestValue) {
            this.generation = generation;
            this.bestValue = bestValue;
        }
    }

    /**
     * Estrutura de dados que armazena o resultado final de uma execução.
     * Consolida informações sobre a melhor solução encontrada, a geração em que
     * ocorreu a paragem e o desempenho inicial para fins de comparação.
     */
    private static final class GeneticResult {
        private final int[] genes;
        private final long value;
        private final long weight;
        private final int stopGeneration;
        private final long initialValue;
        private final List<GenerationRecord> history;

        private GeneticResult(int[] genes, long value, long weight, int stopGeneration, long initialValue, List<GenerationRecord> history) {
            this.genes = genes.clone();
            this.value = value;
            this.weight = weight;
            this.stopGeneration = stopGeneration;
            this.initialValue = initialValue;
            this.history = history;
        }
    }

    /**
     * Representa uma linha de dados formatada para exportação em CSV.
     * Agrega todas as métricas obrigatórias exigidas para o relatório final do trabalho,
     * incluindo parâmetros de configuração e detalhes da instância.
     */
    private static final class ResultRow {
        private final String instance;
        private final int nItems;
        private final long capacity;
        private final Long optimal;
        private final long initialValue;
        private final long foundValue;
        private final Long differenceToOptimal;
        private final Double deviationPercent;
        private final long foundWeight;
        private final boolean feasible;
        private final int stopGeneration;
        private final int maxGenerations;
        private final int populationSize;
        private final double crossoverRate;
        private final double mutationRate;
        private final int eliteSize;
        private final int tournamentSize;
        private final int maxWithoutImprovement;
        private final long seed;
        private final double elapsedSeconds;
        private final List<GenerationRecord> history;

        private ResultRow(
                String instance,
                int nItems,
                long capacity,
                Long optimal,
                long initialValue,
                long foundValue,
                Long differenceToOptimal,
                Double deviationPercent,
                long foundWeight,
                boolean feasible,
                int stopGeneration,
                int maxGenerations,
                int populationSize,
                double crossoverRate,
                double mutationRate,
                int eliteSize,
                int tournamentSize,
                int maxWithoutImprovement,
                long seed,
                double elapsedSeconds,
                List<GenerationRecord> history
        ) {
            this.instance = instance;
            this.nItems = nItems;
            this.capacity = capacity;
            this.optimal = optimal;
            this.initialValue = initialValue;
            this.foundValue = foundValue;
            this.differenceToOptimal = differenceToOptimal;
            this.deviationPercent = deviationPercent;
            this.foundWeight = foundWeight;
            this.feasible = feasible;
            this.stopGeneration = stopGeneration;
            this.maxGenerations = maxGenerations;
            this.populationSize = populationSize;
            this.crossoverRate = crossoverRate;
            this.mutationRate = mutationRate;
            this.eliteSize = eliteSize;
            this.tournamentSize = tournamentSize;
            this.maxWithoutImprovement = maxWithoutImprovement;
            this.seed = seed;
            this.elapsedSeconds = elapsedSeconds;
            this.history = history;
        }
    }
}
