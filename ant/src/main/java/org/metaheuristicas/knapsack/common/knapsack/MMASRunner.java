package org.metaheuristicas.knapsack.common.knapsack;

import org.metaheuristicas.knapsack.common.knapsack.model.ACOKnapsack;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
public final class MMASRunner {

    private MMASRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path propertiesPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("ant/src/main/resources/mmas-experiments.properties");

        Properties p = carregarProperties(propertiesPath);

        List<String> instancias = resolverInstancias(p);

        // Nomes alinhados ao documento (com fallback para nomes legacy).
        List<Integer> ants = parseIntList(p, 30, "mmas.numero.formigas", "mmas.ants");
        List<Integer> iters = parseIntList(p, 300, "mmas.numero.ciclos", "mmas.iterations");
        List<Double> alphas = parseDoubleList(p, 1.0, "mmas.peso.feromona", "mmas.alpha");
        List<Double> betas = parseDoubleList(p, 3.0, "mmas.peso.heuristica", "mmas.beta");
        List<Double> rhos = parseDoubleList(p, 0.2, "mmas.taxa.evaporacao", "mmas.rho");
        List<Double> qs = parseDoubleList(p, 1.0, "mmas.intensidade.deposito", "mmas.q");
        List<Integer> stalls = parseIntList(p, 80, "mmas.ciclos.sem.melhoria", "mmas.stall.limit");
        List<Long> seeds = parseLongList(p, 12345L, "mmas.semente", "mmas.seed");
        int paralelismo = Integer.parseInt(
                readPropertyFirst(
                        p,
                        String.valueOf(Runtime.getRuntime().availableProcessors()),
                        "mmas.paralelismo",
                        "mmas.parallelism"
                )
        );
        if (paralelismo <= 0) {
            throw new IllegalArgumentException("mmas.parallelism deve ser > 0");
        }

        Path output = Path.of(readPropertyFirst(p, "results/ant/mmas-grid-results.csv", "mmas.saida.csv", "mmas.output.csv"));
        Path outputParent = output.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }
        Path detailedOutput = Path.of(readPropertyFirst(
                p,
                "results/ant/mmas-detailed-results.csv",
                "mmas.saida.relatorio",
                "mmas.output.report"
        ));
        Path detailedOutputParent = detailedOutput.getParent();
        if (detailedOutputParent != null) {
            Files.createDirectories(detailedOutputParent);
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

        Map<String, ExperimentResult> melhorPorInstancia = new HashMap<>();
        try (BufferedWriter writer = Files.newBufferedWriter(output)) {
            writer.write("instance,ants,iterations,alpha,beta,rho,q,stall,seed,best_value,total_weight,elapsed_ms");
            writer.newLine();

            executarExperimentosParalelos(tasks, paralelismo, writer, totalRuns, melhorPorInstancia);
        }

        escreverRelatorioDetalhado(detailedOutput, melhorPorInstancia);
        System.out.println("Experiências concluídas. CSV: " + output);
        System.out.println("Relatório detalhado: " + detailedOutput);
    }

    private static void executarExperimentosParalelos(
            List<ExperimentTask> tasks,
            int paralelismo,
            BufferedWriter writer,
            int totalRuns,
            Map<String, ExperimentResult> melhorPorInstancia
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
                atualizarMelhorPorInstancia(melhorPorInstancia, resultado);

                System.out.printf(
                        "[%d/%d] %s | m=%d ciclos=%d alpha=%.2f beta=%.2f rho=%.2f q=%.2f sem_melhoria=%d seed=%d => valor=%d, %d ms%n",
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

    private static void atualizarMelhorPorInstancia(
            Map<String, ExperimentResult> melhorPorInstancia,
            ExperimentResult candidato
    ) {
        ExperimentResult atual = melhorPorInstancia.get(candidato.instanciaPath());
        if (atual == null || candidato.isBetterThan(atual)) {
            melhorPorInstancia.put(candidato.instanciaPath(), candidato);
        }
    }

    private static void escreverRelatorioDetalhado(
            Path detailedOutput,
            Map<String, ExperimentResult> melhorPorInstancia
    ) throws IOException {
        List<ExperimentResult> resultadosOrdenados = melhorPorInstancia
                .values()
                .stream()
                .sorted(Comparator.comparing(ExperimentResult::instanciaPath))
                .toList();

        try (BufferedWriter writer = Files.newBufferedWriter(detailedOutput)) {
            writer.write("file,capacity,items,best_value,total_weight,selected_item_indices");
            writer.newLine();

            for (ExperimentResult resultado : resultadosOrdenados) {
                writer.write(String.format(
                        Locale.US,
                        "%s,%d,%d,%d,%d,%s",
                        toCsvField(Path.of(resultado.instanciaPath()).toAbsolutePath().toString()),
                        resultado.capacidade(),
                        resultado.totalItens(),
                        resultado.valorTotal(),
                        resultado.pesoTotal(),
                        toCsvField(resultado.indicesEscolhidos())
                ));
                writer.newLine();
            }
        }
    }

    private static String toCsvField(String value) {
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
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
        List<String> instancias = parseStringList(p, "mmas.instancias", "mmas.instances");
        if (!instancias.isEmpty()) {
            return instancias;
        }

        String dir = readPropertyFirst(p, "", "mmas.instancias.dir", "mmas.instances.dir").trim();
        if (dir.isEmpty()) {
            throw new IllegalArgumentException("Defina mmas.instancias (ou mmas.instances) ou mmas.instancias.dir (ou mmas.instances.dir)");
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

    private static String readPropertyFirst(Properties p, String fallback, String... keys) {
        for (String key : keys) {
            String value = p.getProperty(key);
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return fallback;
    }

    private static List<String> parseStringList(Properties p, String... keys) {
        String value = readPropertyFirst(p, "", keys).trim();
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

    private static List<Integer> parseIntList(Properties p, int fallback, String... keys) {
        String value = readPropertyFirst(p, String.valueOf(fallback), keys);
        List<Integer> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Integer.parseInt(token.trim()));
        }
        return parsed;
    }

    private static List<Long> parseLongList(Properties p, long fallback, String... keys) {
        String value = readPropertyFirst(p, String.valueOf(fallback), keys);
        List<Long> parsed = new ArrayList<>();
        for (String token : value.split(",")) {
            parsed.add(Long.parseLong(token.trim()));
        }
        return parsed;
    }

    private static List<Double> parseDoubleList(Properties p, double fallback, String... keys) {
        String value = readPropertyFirst(p, String.valueOf(fallback), keys);
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
                    instancia.capacidade,
                    instancia.itens.length,
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
                    toIndicesString(melhor.escolhidos),
                    elapsedMs
            );
        }

        private static String toIndicesString(boolean[] escolhidos) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < escolhidos.length; i++) {
                if (escolhidos[i]) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(i);
                }
            }
            return sb.toString();
        }
    }

    private record ExperimentResult(
            String instanciaPath,
            long capacidade,
            int totalItens,
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
            String indicesEscolhidos,
            long elapsedMs
    ) {
        boolean isBetterThan(ExperimentResult other) {
            if (valorTotal != other.valorTotal) {
                return valorTotal > other.valorTotal;
            }
            if (pesoTotal != other.pesoTotal) {
                return pesoTotal < other.pesoTotal;
            }
            return elapsedMs < other.elapsedMs;
        }

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
