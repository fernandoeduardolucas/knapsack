package org.metaheuristicas.knapsack.mmas.experiments;

import org.metaheuristicas.knapsack.mmas.core.MmasParameters;

import java.util.Locale;

public record MmasResult(
        String instancePath,
        MmasParameters parameters,
        long bestValue,
        long totalWeight,
        long elapsedMs
) {
    public String csvLine() {
        return String.format(
                Locale.US,
                "%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%d,%d,%d,%d,%d",
                instancePath,
                parameters.ants(),
                parameters.iterations(),
                parameters.alpha(),
                parameters.beta(),
                parameters.rho(),
                parameters.q(),
                parameters.stallLimit(),
                parameters.seed(),
                bestValue,
                totalWeight,
                elapsedMs
        );
    }
}
