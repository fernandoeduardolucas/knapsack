package org.metaheuristicas.knapsack.mmas.model;

public record KnapsackSolution(boolean[] selected, long totalValue, long totalWeight) {
}
