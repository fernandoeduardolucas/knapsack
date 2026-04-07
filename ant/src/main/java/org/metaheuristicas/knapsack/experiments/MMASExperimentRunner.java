package org.metaheuristicas.knapsack.experiments;

import org.metaheuristicas.knapsack.mmas.core.MmasParameters;
import org.metaheuristicas.knapsack.mmas.experiments.ExperimentRun;
import org.metaheuristicas.knapsack.mmas.experiments.MmasResult;
import org.metaheuristicas.knapsack.mmas.io.CsvWriter;
import org.metaheuristicas.knapsack.mmas.io.InstanceLoader;
import org.metaheuristicas.knapsack.mmas.io.PropertiesExperimentLoader;
import org.metaheuristicas.knapsack.mmas.model.KnapsackInstance;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MMASExperimentRunner {
    private MMASExperimentRunner() {
    }

    public static void main(String[] args) throws Exception {
        Path propertiesPath = args.length > 0
                ? Path.of(args[0])
                : Path.of("ant/src/main/resources/mmas-experiments.properties");

        PropertiesExperimentLoader loader = PropertiesExperimentLoader.from(propertiesPath);

        List<ExperimentRun> tasks = new ArrayList<>();
        for (String instancePath : loader.instancePaths()) {
            KnapsackInstance instance = InstanceLoader.load(Path.of(instancePath));
            for (MmasParameters parameters : loader.parameterGrid()) {
                tasks.add(new ExperimentRun(instancePath, instance, parameters));
            }
        }

        int parallelism = loader.parallelism();
        System.out.printf("Iniciando experiências MMAS (%d execuções, paralelismo=%d)...%n", tasks.size(), parallelism);

        try (CsvWriter csvWriter = new CsvWriter(loader.outputPath())) {
            runInParallel(tasks, parallelism, csvWriter);
        }
    }

    private static void runInParallel(List<ExperimentRun> tasks, int parallelism, CsvWriter csvWriter)
            throws InterruptedException, java.io.IOException {
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        CompletionService<MmasResult> completion = new ExecutorCompletionService<>(executor);

        try {
            for (ExperimentRun task : tasks) {
                completion.submit(task::execute);
            }

            for (int done = 1; done <= tasks.size(); done++) {
                MmasResult result;
                try {
                    result = completion.take().get();
                } catch (ExecutionException e) {
                    throw new IllegalStateException("Failed MMAS configuration", e.getCause());
                }

                csvWriter.append(result);
                System.out.printf("[%d/%d] %s => value=%d (%d ms)%n",
                        done,
                        tasks.size(),
                        result.instancePath(),
                        result.bestValue(),
                        result.elapsedMs());
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
