import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ponto de entrada da aplicação.
 *
 * Fluxo principal:
 * 1) lê o ficheiro .properties;
 * 2) expande todas as combinações de parâmetros;
 * 3) carrega cada instância da mochila;
 * 4) corre o solver MMAS;
 * 5) grava os resultados num ficheiro CSV.
 */
public class Main {
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);

        Path propertiesPath;
        if (args.length == 0) {
            propertiesPath = Path.of("ant/src/main/resources/test.properties");
            System.out.println("Sem parâmetros: a usar ficheiro por omissão " + propertiesPath);
        } else {
            propertiesPath = Path.of(args[0]);
        }
        try {
            PropertiesExperimentLoader loader = new PropertiesExperimentLoader(propertiesPath);
            Path instancesDir = loader.getInstancesDir();
            List<ExperimentRun> runs = loader.expandRuns();
            if (runs.isEmpty()) {
                System.err.println("Nenhuma instância encontrada no ficheiro de propriedades.");
                System.exit(2);
            }

            MMASKnapsackSolver solver = new MMASKnapsackSolver();
            List<String> csvLines = new ArrayList<>();
            csvLines.add(String.join(",",
                    "alias",
                    "descriptor",
                    "source_file",
                    "n_file",
                    "capacity_file",
                    "n_metadata",
                    "capacity_metadata",
                    "g",
                    "f",
                    "epsilon",
                    "seed_metadata",
                    "ants",
                    "iterations",
                    "alpha",
                    "beta",
                    "rho",
                    "use_global_best",
                    "local_search",
                    "restart_after",
                    "seed_run",
                    "best_value",
                    "best_weight",
                    "selected_items",
                    "utilization",
                    "best_iteration",
                    "time_ms",
                    "selected_indexes"
            ));

            for (ExperimentRun run : runs) {
                KnapsackInstance instance = InstanceLoader.load(instancesDir, run.getAlias(), run.getDescriptor());
                MmasResult result = solver.solve(instance, run.getParameters());
                KnapsackSolution best = result.getBestSolution();
                InstanceMetadata metadata = instance.getMetadata();
                double utilization = instance.getCapacity() == 0 ? 0.0
                        : (100.0 * best.getTotalWeight() / (double) instance.getCapacity());

                System.out.println("Instância " + instance.getAlias() + " | " + run.getParameters());
                System.out.println("  Melhor valor    : " + best.getTotalValue());
                System.out.println("  Melhor peso     : " + best.getTotalWeight() + " / " + instance.getCapacity());
                System.out.println("  Itens escolhidos: " + best.getSelectedCount());
                System.out.println("  Melhor iteração : " + result.getBestIteration());
                System.out.println("  Tempo (ms)      : " + result.getExecutionTimeMs());
                System.out.println();

                csvLines.add(String.join(",",
                        CsvWriter.escape(instance.getAlias()),
                        CsvWriter.escape(metadata.getDescriptor()),
                        CsvWriter.escape(instance.getSourceFile()),
                        Integer.toString(instance.getItemCount()),
                        Long.toString(instance.getCapacity()),
                        metadata.getN() == null ? "" : metadata.getN().toString(),
                        metadata.getCapacity() == null ? "" : metadata.getCapacity().toString(),
                        CsvWriter.escape(metadata.getG()),
                        CsvWriter.escape(metadata.getF()),
                        CsvWriter.escape(metadata.getEpsilon()),
                        metadata.getSeed() == null ? "" : metadata.getSeed().toString(),
                        Integer.toString(run.getParameters().getAnts()),
                        Integer.toString(run.getParameters().getIterations()),
                        Double.toString(run.getParameters().getAlpha()),
                        Double.toString(run.getParameters().getBeta()),
                        Double.toString(run.getParameters().getRho()),
                        Boolean.toString(run.getParameters().isUseGlobalBest()),
                        Boolean.toString(run.getParameters().isLocalSearch()),
                        Integer.toString(run.getParameters().getRestartAfter()),
                        Long.toString(run.getParameters().getSeed()),
                        Long.toString(best.getTotalValue()),
                        Long.toString(best.getTotalWeight()),
                        Integer.toString(best.getSelectedCount()),
                        String.format(Locale.US, "%.4f", utilization),
                        Integer.toString(result.getBestIteration()),
                        Long.toString(result.getExecutionTimeMs()),
                        CsvWriter.escape(best.getSelectedIndexes().toString())
                ));
            }

            Path outputPath = propertiesPath.getParent() == null
                    ? Path.of(loader.getOutputCsv())
                    : propertiesPath.getParent().resolve(loader.getOutputCsv()).normalize();
            CsvWriter.write(outputPath, csvLines);
            System.out.println("Resultados CSV gravados em: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Erro de I/O: " + e.getMessage());
            System.exit(3);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(4);
        }
    }
}
