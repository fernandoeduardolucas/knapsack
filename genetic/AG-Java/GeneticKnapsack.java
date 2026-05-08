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
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Algoritmo Genético para o Problema da Mochila 0/1 (0/1 Knapsack Problem).
 *
 * <p>Port Java do ficheiro {@code genetic/AG-Python/ag_v3.py}. Mantém a mesma
 * estratégia: representação binária, algoritmo geracional, seleção por torneio,
 * cruzamento de dois pontos, mutação bit-flip, reparação greedy, elitismo e
 * paragem por número de gerações ou estagnação.</p>
 */
public final class GeneticKnapsack {
    private static final Map<String, Long> OPTIMAL_VALUES = createOptimalValues();
    private static final Pattern NATURAL_PARTS = Pattern.compile("(\\d+|\\D+)");

    private GeneticKnapsack() {
        // Classe utilitária; não deve ser instanciada.
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.US);
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

    private static Chromosome createRandomSolution(KnapsackInstance instance, Random random) {
        int[] genes = new int[instance.n];
        for (int i = 0; i < instance.n; i++) {
            genes[i] = random.nextBoolean() ? 1 : 0;
        }
        return repairSolution(genes, instance);
    }

    private static List<Chromosome> initializePopulation(KnapsackInstance instance, int populationSize, Random random) {
        List<Chromosome> population = new ArrayList<>(populationSize);
        for (int i = 0; i < populationSize; i++) {
            population.add(createRandomSolution(instance, random));
        }
        return population;
    }

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
        validateParameters(populationSize, generations, crossoverRate, mutationRate, eliteSize, tournamentSize, maxWithoutImprovement);

        Random random = new Random(seed);
        List<Chromosome> population = initializePopulation(instance, populationSize, random);
        Chromosome best = population.stream().max(Comparator.comparingLong(chromosome -> chromosome.value)).orElseThrow();
        long initialValue = best.value;

        int generationsWithoutImprovement = 0;
        int stopGeneration = 0;

        for (int generation = 1; generation <= generations; generation++) {
            stopGeneration = generation;

            List<Chromosome> sortedPopulation = new ArrayList<>(population);
            sortedPopulation.sort(Comparator.comparingLong((Chromosome chromosome) -> chromosome.value).reversed());

            List<Chromosome> newPopulation = new ArrayList<>(populationSize);
            for (int i = 0; i < eliteSize; i++) {
                newPopulation.add(sortedPopulation.get(i).copy());
            }

            while (newPopulation.size() < populationSize) {
                Chromosome parent1 = tournamentSelection(population, tournamentSize, random);
                Chromosome parent2 = tournamentSelection(population, tournamentSize, random);

                int[] childGenes1;
                int[] childGenes2;
                if (random.nextDouble() < crossoverRate) {
                    int[][] children = twoPointCrossover(parent1, parent2, random);
                    childGenes1 = children[0];
                    childGenes2 = children[1];
                } else {
                    childGenes1 = parent1.genes.clone();
                    childGenes2 = parent2.genes.clone();
                }

                childGenes1 = bitFlipMutation(childGenes1, mutationRate, random);
                childGenes2 = bitFlipMutation(childGenes2, mutationRate, random);

                newPopulation.add(repairSolution(childGenes1, instance));
                if (newPopulation.size() < populationSize) {
                    newPopulation.add(repairSolution(childGenes2, instance));
                }
            }

            population = newPopulation;
            Chromosome generationBest = population.stream().max(Comparator.comparingLong(chromosome -> chromosome.value)).orElseThrow();
            if (generationBest.value > best.value) {
                best = generationBest.copy();
                generationsWithoutImprovement = 0;
            } else {
                generationsWithoutImprovement++;
            }

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

            if (verbose && generation % 100 == 0) {
                System.out.printf(
                        "    Geração %4d | Melhor valor: %,d | Sem melhoria: %d%n",
                        generation,
                        best.value,
                        generationsWithoutImprovement
                );
            }
        }

        return new GeneticResult(best.genes, best.value, best.weight, stopGeneration, initialValue);
    }

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
            Double deviation = optimal == null ? null : ((optimal - result.value) / (double) optimal) * 100.0;
            ResultRow row = new ResultRow(
                    instance.name,
                    optimal,
                    result.initialValue,
                    result.value,
                    deviation,
                    result.stopGeneration,
                    elapsedSeconds
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

        writeCsv(outputCsv, results);
        System.out.println("\n  Resultados guardados em: " + outputCsv + "\n");
    }

    private static void writeCsv(Path outputCsv, List<ResultRow> results) throws IOException {
        Path parent = outputCsv.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputCsv, StandardCharsets.UTF_8)) {
            writer.write("instancia,solucao_otima,solucao_inicial,solucao_encontrada,desvio_percentual,geracao_paragem,tempo_execucao_s");
            writer.newLine();
            for (ResultRow row : results) {
                writer.write(String.join(",",
                        row.instance,
                        row.optimal == null ? "" : row.optimal.toString(),
                        Long.toString(row.initialValue),
                        Long.toString(row.foundValue),
                        row.deviationPercent == null ? "" : String.format(Locale.US, "%.6f", row.deviationPercent),
                        Integer.toString(row.stopGeneration),
                        String.format(Locale.US, "%.4f", row.elapsedSeconds)
                ));
                writer.newLine();
            }
        }
    }

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
            this.instanceDir = Paths.get(options.getOrDefault("--instances", "../AG-Python/instancias"));
            this.outputCsv = Paths.get(options.getOrDefault("--output", "ag_resultados_java.csv"));
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
            System.out.println("Uso: java GeneticKnapsack [opções]\n"
                    + "  --instances <pasta>     Pasta com instâncias (default: ../AG-Python/instancias)\n"
                    + "  --output <ficheiro>     CSV de saída (default: ag_resultados_java.csv)\n"
                    + "  --population <n>        Tamanho da população (default: 50)\n"
                    + "  --generations <n>       Número máximo de gerações (default: 500)\n"
                    + "  --crossover <p>         Taxa de cruzamento (default: 0.85)\n"
                    + "  --mutation <p>          Taxa de mutação por bit (default: 0.005)\n"
                    + "  --elite <n>             Número de elites preservados (default: 3)\n"
                    + "  --tournament <n>        Tamanho do torneio (default: 3)\n"
                    + "  --stagnation <n>        Gerações sem melhoria para parar (default: 100)\n"
                    + "  --seed <n>              Semente aleatória (default: 42)\n"
                    + "  --verbose               Mostra progresso por geração");
            System.exit(0);
        }
    }

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

    private static final class GeneticResult {
        private final int[] genes;
        private final long value;
        private final long weight;
        private final int stopGeneration;
        private final long initialValue;

        private GeneticResult(int[] genes, long value, long weight, int stopGeneration, long initialValue) {
            this.genes = genes.clone();
            this.value = value;
            this.weight = weight;
            this.stopGeneration = stopGeneration;
            this.initialValue = initialValue;
        }
    }

    private static final class ResultRow {
        private final String instance;
        private final Long optimal;
        private final long initialValue;
        private final long foundValue;
        private final Double deviationPercent;
        private final int stopGeneration;
        private final double elapsedSeconds;

        private ResultRow(
                String instance,
                Long optimal,
                long initialValue,
                long foundValue,
                Double deviationPercent,
                int stopGeneration,
                double elapsedSeconds
        ) {
            this.instance = instance;
            this.optimal = optimal;
            this.initialValue = initialValue;
            this.foundValue = foundValue;
            this.deviationPercent = deviationPercent;
            this.stopGeneration = stopGeneration;
            this.elapsedSeconds = elapsedSeconds;
        }
    }
}
