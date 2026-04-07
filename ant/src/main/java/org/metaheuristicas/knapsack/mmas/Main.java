package org.metaheuristicas.knapsack.mmas;

import org.metaheuristicas.knapsack.mmas.core.MMASKnapsackSolver;
import org.metaheuristicas.knapsack.mmas.core.MmasParameters;
import org.metaheuristicas.knapsack.mmas.io.InstanceLoader;
import org.metaheuristicas.knapsack.mmas.model.KnapsackInstance;
import org.metaheuristicas.knapsack.mmas.model.KnapsackSolution;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Usage: Main <instance-file>");
        }

        KnapsackInstance instance = InstanceLoader.load(Path.of(args[0]));
        MMASKnapsackSolver solver = new MMASKnapsackSolver();
        KnapsackSolution solution = solver.solve(instance, MmasParameters.defaults());

        System.out.println("Best value: " + solution.totalValue());
        System.out.println("Total weight: " + solution.totalWeight());
    }
}
