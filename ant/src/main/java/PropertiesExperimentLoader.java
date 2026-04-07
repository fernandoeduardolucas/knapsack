import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Lê o ficheiro .properties e transforma as listas de parâmetros
 * em várias execuções (produto cartesiano).
 */
public class PropertiesExperimentLoader {
    private final Properties properties = new Properties();
    private final Path propertiesPath;

    public PropertiesExperimentLoader(Path propertiesPath) throws IOException {
        this.propertiesPath = propertiesPath;
        try (InputStream inputStream = Files.newInputStream(propertiesPath)) {
            properties.load(inputStream);
        }
    }

    /**
     * Diretório onde estão os ficheiros das instâncias.
     */
    public Path getInstancesDir() {
        String value = properties.getProperty("mmas.instances.dir", ".").trim();
        Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path;
        }
        Path parent = propertiesPath.getParent();
        return parent == null ? path : parent.resolve(path).normalize();
    }

    public String getOutputCsv() {
        return properties.getProperty("mmas.output.csv", "results.csv").trim();
    }

    /**
     * Expande as combinações de parâmetros para criar todas as execuções.
     */
    public List<ExperimentRun> expandRuns() {
        Map<String, String> instances = loadInstances();
        List<Integer> ants = parseIntegers("mmas.ants", List.of(20));
        List<Integer> iterations = parseIntegers("mmas.iterations", List.of(100));
        List<Double> alpha = parseDoubles("mmas.alpha", List.of(1.0));
        List<Double> beta = parseDoubles("mmas.beta", List.of(2.0));
        List<Double> rho = parseDoubles("mmas.rho", List.of(0.2));
        List<Boolean> useGlobalBest = parseBooleans("mmas.useGlobalBest", List.of(Boolean.TRUE));
        List<Boolean> localSearch = parseBooleans("mmas.localSearch", List.of(Boolean.TRUE));
        List<Integer> restartAfter = parseIntegers("mmas.restartAfter", List.of(50));
        List<Long> seeds = parseLongs("mmas.seed", List.of(12345L));

        List<ExperimentRun> runs = new ArrayList<>();
        for (Map.Entry<String, String> entry : instances.entrySet()) {
            for (int antCount : ants) {
                for (int iterationCount : iterations) {
                    for (double alphaValue : alpha) {
                        for (double betaValue : beta) {
                            for (double rhoValue : rho) {
                                for (boolean globalBestValue : useGlobalBest) {
                                    for (boolean localSearchValue : localSearch) {
                                        for (int restartValue : restartAfter) {
                                            for (long seedValue : seeds) {
                                                MmasParameters parameters = new MmasParameters(
                                                        antCount,
                                                        iterationCount,
                                                        alphaValue,
                                                        betaValue,
                                                        rhoValue,
                                                        globalBestValue,
                                                        localSearchValue,
                                                        restartValue,
                                                        seedValue
                                                );
                                                runs.add(new ExperimentRun(entry.getKey(), entry.getValue(), parameters));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return runs;
    }

    /**
     * Carrega os aliases das instâncias.
     *
     * Prioridade:
     * 1) lista explícita em mmas.instances
     * 2) chaves instance.xxx
     * 3) chaves do tipo n_1000_1=n_1000_c_...
     */
    private Map<String, String> loadInstances() {
        Map<String, String> instances = new LinkedHashMap<>();

        String explicitList = properties.getProperty("mmas.instances", "").trim();
        if (!explicitList.isBlank()) {
            for (String alias : explicitList.split(",")) {
                String cleanAlias = alias.trim();
                if (cleanAlias.isEmpty()) {
                    continue;
                }
                String descriptor = properties.getProperty("instance." + cleanAlias,
                        properties.getProperty(cleanAlias, cleanAlias));
                instances.put(cleanAlias, descriptor.trim());
            }
            return instances;
        }

        Set<String> keys = new TreeSet<>(properties.stringPropertyNames());
        for (String key : keys) {
            if (key.startsWith("instance.")) {
                String alias = key.substring("instance.".length());
                instances.put(alias, properties.getProperty(key).trim());
            } else if (key.startsWith("n_") && properties.getProperty(key).contains("_c_")) {
                instances.put(key.trim(), properties.getProperty(key).trim());
            }
        }

        return instances;
    }

    private List<Integer> parseIntegers(String key, List<Integer> defaults) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaults;
        }
        List<Integer> result = new ArrayList<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .forEach(result::add);
        return result.isEmpty() ? defaults : result;
    }

    private List<Long> parseLongs(String key, List<Long> defaults) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaults;
        }
        List<Long> result = new ArrayList<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .forEach(result::add);
        return result.isEmpty() ? defaults : result;
    }

    private List<Double> parseDoubles(String key, List<Double> defaults) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaults;
        }
        List<Double> result = new ArrayList<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Double::parseDouble)
                .forEach(result::add);
        return result.isEmpty() ? defaults : result;
    }

    private List<Boolean> parseBooleans(String key, List<Boolean> defaults) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return defaults;
        }
        List<Boolean> result = new ArrayList<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Boolean::parseBoolean)
                .forEach(result::add);
        return result.isEmpty() ? defaults : result;
    }
}
