package org.metaheuristicas.knapsack.mmas.core;

public record MmasParameters(
        int ants,
        int iterations,
        double alpha,
        double beta,
        double rho,
        double q,
        int stallLimit,
        long seed
) {
    public static MmasParameters defaults() {
        return new MmasParameters(30, 300, 1.0, 3.0, 0.2, 1.0, 80, System.nanoTime());
    }
}
