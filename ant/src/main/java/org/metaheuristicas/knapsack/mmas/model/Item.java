package org.metaheuristicas.knapsack.mmas.model;

public record Item(long weight, long value) {
    public Item {
        if (weight <= 0) throw new IllegalArgumentException("weight must be > 0");
        if (value < 0) throw new IllegalArgumentException("value must be >= 0");
    }
}
