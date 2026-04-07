package org.metaheuristicas.knapsack.mmas.model;

import java.nio.file.Path;

public record InstanceMetadata(Path path, String name, int itemCount, long capacity) {
}
