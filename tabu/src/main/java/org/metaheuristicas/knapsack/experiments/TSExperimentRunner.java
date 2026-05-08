package org.metaheuristicas.knapsack.experiments;

import org.metaheuristicas.knapsack.TSKnapsack;
import org.metaheuristicas.knapsack.common.knapsack.model.Instancia;
import org.metaheuristicas.knapsack.common.knapsack.model.Solucao;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorCompletionService;
import java.util.stream.Stream;

/**
 * Runner de experiências do Tabu Search para o problema da mochila 0/1.
 *
 * <p>Lê um ficheiro de propriedades (por omissão, {@code src/main/resources/ts-experiments.properties})
 * e executa uma grelha de combinações de parâmetros para permitir análise de performance.
 */
public final class TSExperimentRunner {

    private TSExperimentRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path propertiesPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("tabu/src/main/resources/ts-experiments.properties");

        Properties p = carregarProperties(propertiesPath);

        List<String> instancias = resolverInstancias(p);

        List<Integer> itersList = parseIntList(p, "ts.iterations", 3000);
        List<Integer> tenureFlips = parseIntList(p, "ts.tenure.flip", 15);
        List<Integer> tenureSwaps = parseIntList(p, "ts.tenure.swap", 10);
        List<Integer> stalls = parseIntList(p, "ts.stall.limit", 100);
        List<Double> diversifies = parseDoubleList(p, "ts.diversify.strength", 0.2);
        List<Long> seeds = parseLongList(p, "ts.seed", 12345L);
        int paralelismo = Integer.parseInt(
                p.getProperty("ts.parallelism", String.valueOf(Runtime.getRuntime().availableProcessors()))
        );
        if (paralelismo <= 0) {
            throw new IllegalArgumentException("ts.parallelism deve ser > 0");
        }

        Path output = Path.of(p.getProperty("ts.output.csv", "results/ts-grid-results.csv"));
        Path outputParent = output.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        int totalRuns = instancias.size() * itersList.size() * tenureFlips.size() * tenureSwaps.size()
                * stalls.size() * diversifies.size() * seeds.size();

        System.out.printf(
                "Iniciando experiências Tabu Search (%d execuções, paralelismo=%d)...%n",
                totalRuns,
                paralelismo
        );

        List<ExperimentTask> tasks = new ArrayList<>(totalRuns);
        for (String instanciaPath : instancias) {
            Instancia instancia = TSKnapsack.carregarInstancia(Path.of(instanciaPath));

            for (int iters : itersList) {
                for (int tenureFlip : tenureFlips) {
                    for (int tenureSwap : tenureSwaps) {
                        for (int stall : stalls) {
                            for (double diversify : diversifies) {
                                for (long seed : seeds) {
                                    tasks.add(new ExperimentTask(
                                            instanciaPath,
                                            instancia,
                                            iters,
                                            tenureFlip,
                                            tenureSwap,
                                            stall,
                                            diversify,
                                            seed
                                    ));
                                }
                            }
                        }
                    }
                }
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("instance,iterations,tenure_flip,tenure_swap,stall,diversify,seed,best_value,total_weight,elapsed_ms");
            writer.newLine();

            executarExperimentosParalelos(tasks, paralelismo, writer, totalRuns);
        }

        System.out.println("Experiências concluídas. CSV: " + output);
    }

    private static void executarExperimentosParalelos(
            List<ExperimentTask> tasks,
            int paralelismo,
            BufferedWriter writer,
            int totalRuns
    ) throws IOException, InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(paralelismo);
        CompletionService<ExperimentResult> completion = new ExecutorCompletionService<>(executor);
        try {
            for (ExperimentTask task : tasks) {
                completion.submit(task::execute);
            }

            for (int concluido = 1; concluido <= tasks.size(); concluido++) {
                ExperimentResult resultado;
                try {
                    resultado = completion.take().get();
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Falha ao executar configuração Tabu Search", e.getCause());
                }

                writer.write(resultado.csvLine());
                writer.newLine();

                System.out.printf(
                        "[%d/%d] %s | it=%d tFlip=%d tSwap=%d stall=%d div=%.2f seed=%d => valor=%d, %d ms%n",
                        concluido,
                        totalRuns,
                        resultado.instanciaPath(),
                        resultado.iters(),
                        resultado.tenureFlip(),
                        resultado.tenureSwap(),
                        resultado.stall(),
                        resultado.diversify(),
                        resultado.seed(),
                        resultado.valorTotal(),
                        resultado.elapsedMs()
                );
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Properties carregarProperties(Path path) throws IOException {
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Ficheiro de propriedades não encontrado: " + path);
        }

        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }
        return properties;
    }

    private static List<String> resolverInstancias(Properties p) throws IOException {
        List<String> instancias = parseStringList(p, "ts.instances");
        if (!instancias.isEmpty()) {
            return instancias;
        }

        String dir = p.getProperty("ts.instances.dir", "").trim();
        if (dir.isEmpty()) {
            throw new IllegalArgumentException("Defina ts.instances ou ts.instances.dir");
        }

        Path base = Path.of(dir);
        if (!Files.isDirectory(base)) {
            throw new IllegalArgumentException("Diretoria de instâncias não existe: " + base);
        }

        List<String> ficheiros = new ArrayList<>();
        try (Stream<Path> paths = Files.list(base)) {
            paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(path -> ficheiros.add(path.toString()));
        }

        if (ficheiros.isEmpty()) {
            throw new IllegalArgumentException("Nenhuma instância encontrada em: " + base);
        }

        return ficheiros;
    }

    private static List<String> parseStringList(Properties p, String key) {
        String value = p.getProperty(key, "").trim();
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

    private static List<Integer> parseIntList(Properties p, String key, int fallback) {
        String value = p.getProperty(key, String.valueOf(fallback));
        List<Integer> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Integer.parseInt(token.trim()));
        }
        return parsed;
    }

    private static List<Long> parseLongList(Properties p, String key, long fallback) {
        String value = p.getProperty(key, String.valueOf(fallback));
        List<Long> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Long.parseLong(token.trim()));
        }
        return parsed;
    }

    private static List<Double> parseDoubleList(Properties p, String key, double fallback) {
        String value = p.getProperty(key, String.valueOf(fallback));
        List<Double> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Double.parseDouble(token.trim()));
        }
        return parsed;
    }

    private record ExperimentTask(
            String instanciaPath,
            Instancia instancia,
            int iters,
            int tenureFlip,
            int tenureSwap,
            int stall,
            double diversify,
            long seed
    ) {
        ExperimentResult execute() {
            Instant inicio = Instant.now();

            TSKnapsack solver = new TSKnapsack(
                    instancia.itens,
                    instancia.capacidade,
                    iters,
                    tenureFlip,
                    tenureSwap,
                    stall,
                    diversify,
                    seed
            );

            Solucao melhor = solver.resolver();
            long elapsedMs = Duration.between(inicio, Instant.now()).toMillis();
            return new ExperimentResult(
                    instanciaPath,
                    iters,
                    tenureFlip,
                    tenureSwap,
                    stall,
                    diversify,
                    seed,
                    melhor.valorTotal,
                    melhor.pesoTotal,
                    elapsedMs
            );
        }
    }

    private record ExperimentResult(
            String instanciaPath,
            int iters,
            int tenureFlip,
            int tenureSwap,
            int stall,
            double diversify,
            long seed,
            long valorTotal,
            long pesoTotal,
            long elapsedMs
    ) {
        String csvLine() {
            return String.format(
                    Locale.US,
                    "%s,%d,%d,%d,%d,%.4f,%d,%d,%d,%d",
                    instanciaPath,
                    iters,
                    tenureFlip,
                    tenureSwap,
                    stall,
                    diversify,
                    seed,
                    valorTotal,
                    pesoTotal,
                    elapsedMs
            );
        }
    }
}
