package org.metaheuristicas.knapsack.mmas.io;

import org.metaheuristicas.knapsack.mmas.model.InstanceMetadata;
import org.metaheuristicas.knapsack.mmas.model.Item;
import org.metaheuristicas.knapsack.mmas.model.KnapsackInstance;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class InstanceLoader {
    private InstanceLoader() {
    }

    public static KnapsackInstance load(Path path) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String firstLine = br.readLine();
            if (firstLine == null) throw new IllegalArgumentException("empty instance: " + path);

            int n = Integer.parseInt(firstLine.trim());
            List<Item> items = new ArrayList<>(n);

            for (int i = 0; i < n; i++) {
                String line = br.readLine();
                if (line == null) throw new IllegalArgumentException("invalid item lines: " + path);
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 3) throw new IllegalArgumentException("invalid item line: " + line);
                long value = Long.parseLong(parts[1]);
                long weight = Long.parseLong(parts[2]);
                items.add(new Item(weight, value));
            }

            String capacityLine = br.readLine();
            if (capacityLine == null) throw new IllegalArgumentException("missing capacity: " + path);

            return new KnapsackInstance(items, Long.parseLong(capacityLine.trim()));
        }
    }

    public static InstanceMetadata metadata(Path path) throws IOException {
        KnapsackInstance instance = load(path);
        return new InstanceMetadata(path, path.getFileName().toString(), instance.items().size(), instance.capacity());
    }
}
