package org.metaheuristicas.knapsack.mmas.experiments;

import org.metaheuristicas.knapsack.mmas.core.MMASKnapsackSolver;
import org.metaheuristicas.knapsack.mmas.core.MmasParameters;
import org.metaheuristicas.knapsack.mmas.model.KnapsackInstance;
import org.metaheuristicas.knapsack.mmas.model.KnapsackSolution;

import java.time.Duration;
import java.time.Instant;

public record ExperimentRun(String instancePath, KnapsackInstance instance, MmasParameters parameters) {

    public MmasResult execute() {
        Instant start = Instant.now();
        MMASKnapsackSolver solver = new MMASKnapsackSolver();
        KnapsackSolution solution = solver.solve(instance, parameters);
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();
        return new MmasResult(instancePath, parameters, solution.totalValue(), solution.totalWeight(), elapsedMs);
    }
}
