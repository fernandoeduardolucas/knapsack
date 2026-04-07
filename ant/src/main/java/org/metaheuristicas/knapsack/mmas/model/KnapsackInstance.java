package org.metaheuristicas.knapsack.mmas.model;

import java.util.List;

public record KnapsackInstance(List<Item> items, long capacity) {
    public KnapsackInstance {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("items cannot be empty");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
    }
}
