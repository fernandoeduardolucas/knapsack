package org.metaheuristicas.knapsack.mmas.core;

import org.metaheuristicas.knapsack.ACOKnapsack;
import org.metaheuristicas.knapsack.common.knapsack.model.Solucao;
import org.metaheuristicas.knapsack.mmas.model.KnapsackInstance;
import org.metaheuristicas.knapsack.mmas.model.KnapsackSolution;

public final class MMASKnapsackSolver {

    public KnapsackSolution solve(KnapsackInstance instance, MmasParameters parameters) {
        org.metaheuristicas.knapsack.common.knapsack.model.Item[] legacyItems = instance.items().stream()
                .map(item -> new org.metaheuristicas.knapsack.common.knapsack.model.Item(item.weight(), item.value()))
                .toArray(org.metaheuristicas.knapsack.common.knapsack.model.Item[]::new);

        ACOKnapsack solver = new ACOKnapsack(
                legacyItems,
                instance.capacity(),
                parameters.ants(),
                parameters.iterations(),
                parameters.alpha(),
                parameters.beta(),
                parameters.rho(),
                parameters.q(),
                parameters.stallLimit(),
                parameters.seed()
        );

        Solucao solution = solver.resolver();
        return new KnapsackSolution(solution.escolhidos, solution.valorTotal, solution.pesoTotal);
    }
}
