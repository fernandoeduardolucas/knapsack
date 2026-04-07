package org.metaheuristicas.knapsack.mmas.io;

import org.metaheuristicas.knapsack.mmas.core.MmasParameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

public final class PropertiesExperimentLoader {
    private final Properties properties;

    public PropertiesExperimentLoader(Properties properties) {
        this.properties = properties;
    }

    public static PropertiesExperimentLoader from(Path path) throws IOException {
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(path)) {
            p.load(reader);
        }
        return new PropertiesExperimentLoader(p);
    }

    public List<String> instancePaths() throws IOException {
        String explicit = properties.getProperty("mmas.instances", "").trim();
        if (!explicit.isEmpty()) {
            List<String> result = new ArrayList<>();
            for (String token : explicit.split(",")) {
                String cleaned = token.trim();
                if (!cleaned.isEmpty()) result.add(cleaned);
            }
            return result;
        }

        String dir = properties.getProperty("mmas.instances.dir", "").trim();
        if (dir.isEmpty()) throw new IllegalArgumentException("set mmas.instances or mmas.instances.dir");

        try (Stream<Path> paths = Files.list(Path.of(dir))) {
            return paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(Path::toString)
                    .toList();
        }
    }

    public List<MmasParameters> parameterGrid() {
        List<Integer> ants = parseIntList("mmas.ants", 30);
        List<Integer> iters = parseIntList("mmas.iterations", 300);
        List<Double> alphas = parseDoubleList("mmas.alpha", 1.0);
        List<Double> betas = parseDoubleList("mmas.beta", 3.0);
        List<Double> rhos = parseDoubleList("mmas.rho", 0.2);
        List<Double> qs = parseDoubleList("mmas.q", 1.0);
        List<Integer> stalls = parseIntList("mmas.stall.limit", 80);
        List<Long> seeds = parseLongList("mmas.seed", 12345L);

        List<MmasParameters> grid = new ArrayList<>();
        for (int ant : ants) {
            for (int iter : iters) {
                for (double alpha : alphas) {
                    for (double beta : betas) {
                        for (double rho : rhos) {
                            for (double q : qs) {
                                for (int stall : stalls) {
                                    for (long seed : seeds) {
                                        grid.add(new MmasParameters(ant, iter, alpha, beta, rho, q, stall, seed));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return grid;
    }

    public int parallelism() {
        return Integer.parseInt(properties.getProperty("mmas.parallelism", String.valueOf(Runtime.getRuntime().availableProcessors())));
    }

    public Path outputPath() {
        return Path.of(properties.getProperty("mmas.output.csv", "results/mmas-grid-results.csv"));
    }

    private List<Integer> parseIntList(String key, int fallback) {
        String value = properties.getProperty(key, String.valueOf(fallback));
        List<Integer> parsed = new ArrayList<>();
        for (String token : value.split(",")) parsed.add(Integer.parseInt(token.trim()));
        return parsed;
    }

    private List<Long> parseLongList(String key, long fallback) {
        String value = properties.getProperty(key, String.valueOf(fallback));
        List<Long> parsed = new ArrayList<>();
        for (String token : value.split(",")) parsed.add(Long.parseLong(token.trim()));
        return parsed;
    }

    private List<Double> parseDoubleList(String key, double fallback) {
        String value = properties.getProperty(key, String.valueOf(fallback));
        List<Double> parsed = new ArrayList<>();
        for (String token : value.split(",")) parsed.add(Double.parseDouble(token.trim()));
        return parsed;
    }
}
