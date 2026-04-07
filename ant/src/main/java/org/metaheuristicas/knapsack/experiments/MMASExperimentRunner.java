package org.metaheuristicas.knapsack.experiments;

import org.metaheuristicas.knapsack.ACOKnapsack;
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
 * Runner de experiências do MAX-MIN Ant System (MMAS) para o problema da mochila 0/1.
 *
 * <p>Lê um ficheiro de propriedades (por omissão, {@code src/main/resources/mmas-experiments.properties})
 * e executa uma grelha de combinações de parâmetros para permitir análise de performance.
 */
public final class MMASExperimentRunner {

    private MMASExperimentRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path propertiesPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("ant/src/main/resources/mmas-experiments.properties");

        Properties p = carregarProperties(propertiesPath);

        List<String> instancias = resolverInstancias(p);

        List<Integer> ants = parseIntList(p, "mmas.ants", 30);
        List<Integer> iters = parseIntList(p, "mmas.iterations", 300);
        List<Double> alphas = parseDoubleList(p, "mmas.alpha", 1.0);
        List<Double> betas = parseDoubleList(p, "mmas.beta", 3.0);
        List<Double> rhos = parseDoubleList(p, "mmas.rho", 0.2);
        List<Double> qs = parseDoubleList(p, "mmas.q", 1.0);
        List<Integer> stalls = parseIntList(p, "mmas.stall.limit", 80);
        List<Long> seeds = parseLongList(p, "mmas.seed", 12345L);
        int paralelismo = Integer.parseInt(
                p.getProperty("mmas.parallelism", String.valueOf(Runtime.getRuntime().availableProcessors()))
        );
        if (paralelismo <= 0) {
            throw new IllegalArgumentException("mmas.parallelism deve ser > 0");
        }

        Path output = Path.of(p.getProperty("mmas.output.csv", "results/mmas-grid-results.csv"));
        Path outputParent = output.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        int totalRuns = instancias.size() * ants.size() * iters.size() * alphas.size() * betas.size()
                * rhos.size() * qs.size() * stalls.size() * seeds.size();

        System.out.printf(
                "Iniciando experiências MMAS (%d execuções, paralelismo=%d)...%n",
                totalRuns,
                paralelismo
        );

        List<ExperimentTask> tasks = new ArrayList<>(totalRuns);
        for (String instanciaPath : instancias) {
            Instancia instancia = ACOKnapsack.carregarInstancia(Path.of(instanciaPath));

            for (int ant : ants) {
                for (int iter : iters) {
                    for (double alpha : alphas) {
                        for (double beta : betas) {
                            for (double rho : rhos) {
                                for (double q : qs) {
                                    for (int stall : stalls) {
                                        for (long seed : seeds) {
                                            tasks.add(new ExperimentTask(
                                                    instanciaPath,
                                                    instancia,
                                                    ant,
                                                    iter,
                                                    alpha,
                                                    beta,
                                                    rho,
                                                    q,
                                                    stall,
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

        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("instance,ants,iterations,alpha,beta,rho,q,stall,seed,best_value,total_weight,elapsed_ms");
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
                    throw new IllegalStateException("Falha ao executar configuração MMAS", e.getCause());
                }

                writer.write(resultado.csvLine());
                writer.newLine();

                System.out.printf(
                        "[%d/%d] %s | ants=%d it=%d a=%.2f b=%.2f rho=%.2f q=%.2f stall=%d seed=%d => valor=%d, %d ms%n",
                        concluido,
                        totalRuns,
                        resultado.instanciaPath(),
                        resultado.ant(),
                        resultado.iter(),
                        resultado.alpha(),
                        resultado.beta(),
                        resultado.rho(),
                        resultado.q(),
                        resultado.stall(),
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
        List<String> instancias = parseStringList(p, "mmas.instances");
        if (!instancias.isEmpty()) {
            return instancias;
        }

        String dir = p.getProperty("mmas.instances.dir", "").trim();
        if (dir.isEmpty()) {
            throw new IllegalArgumentException("Defina mmas.instances ou mmas.instances.dir");
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
            int ant,
            int iter,
            double alpha,
            double beta,
            double rho,
            double q,
            int stall,
            long seed
    ) {
        ExperimentResult execute() {
            Instant inicio = Instant.now();

            ACOKnapsack solver = new ACOKnapsack(
                    instancia.itens,
                    instancia.capacidade,
                    ant,
                    iter,
                    alpha,
                    beta,
                    rho,
                    q,
                    stall,
                    seed
            );

            Solucao melhor = solver.resolver();
            long elapsedMs = Duration.between(inicio, Instant.now()).toMillis();
            return new ExperimentResult(
                    instanciaPath,
                    ant,
                    iter,
                    alpha,
                    beta,
                    rho,
                    q,
                    stall,
                    seed,
                    melhor.valorTotal,
                    melhor.pesoTotal,
                    elapsedMs
            );
        }
    }

    private record ExperimentResult(
            String instanciaPath,
            int ant,
            int iter,
            double alpha,
            double beta,
            double rho,
            double q,
            int stall,
            long seed,
            long valorTotal,
            long pesoTotal,
            long elapsedMs
    ) {
        String csvLine() {
            return String.format(
                    Locale.US,
                    "%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%d,%d",
                    instanciaPath,
                    ant,
                    iter,
                    alpha,
                    beta,
                    rho,
                    q,
                    stall,
                    seed,
                    valorTotal,
                    pesoTotal,
                    elapsedMs
            );
        }
    }
}
