import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsável por ler um ficheiro de instância do KP.
 *
 * Formato esperado:
 * linha 1: número de itens n
 * linhas 2..n+1: id valor peso
 * última linha: capacidade da mochila
 */
public class InstanceLoader {
    public static KnapsackInstance load(Path instancesDir, String alias, String descriptor) throws IOException {
        Path filePath = resolveInstancePath(instancesDir, alias, descriptor);
        List<String> lines = Files.readAllLines(filePath);
        if (lines.isEmpty()) {
            throw new IOException("Ficheiro de instância vazio: " + filePath);
        }

        int n = Integer.parseInt(lines.get(0).trim());
        if (lines.size() < n + 2) {
            throw new IOException("Formato inválido da instância: esperado " + n + " itens + capacidade final em " + filePath);
        }

        List<Item> items = new ArrayList<>(n);
        double maxRatio = 0.0;
        for (int i = 1; i <= n; i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            int index;
            long value;
            long weight;

            // Aceita tanto "id valor peso" como "valor peso".
            if (parts.length >= 3) {
                index = Integer.parseInt(parts[0]);
                value = Long.parseLong(parts[1]);
                weight = Long.parseLong(parts[2]);
            } else if (parts.length == 2) {
                index = i - 1;
                value = Long.parseLong(parts[0]);
                weight = Long.parseLong(parts[1]);
            } else {
                throw new IOException("Linha inválida na instância: '" + line + "'");
            }

            Item item = new Item(index, value, weight);

            // Heurística eta_i = valor/peso.
            // Depois normalizamos pelo melhor rácio para ficar aproximadamente em [0,1].
            double ratio = weight == 0 ? value : (double) value / (double) weight;
            item.setHeuristic(ratio);
            maxRatio = Math.max(maxRatio, ratio);
            items.add(item);
        }

        if (maxRatio > 0.0) {
            for (Item item : items) {
                item.setHeuristic(item.getHeuristic() / maxRatio);
            }
        }

        long capacity = Long.parseLong(lines.get(n + 1).trim());
        InstanceMetadata metadata = InstanceMetadata.parse(alias, descriptor);
        return new KnapsackInstance(alias, filePath.getFileName().toString(), capacity, items, metadata);
    }

    private static Path resolveInstancePath(Path instancesDir, String alias, String descriptor) throws IOException {
        Path aliasPath = instancesDir.resolve(alias);
        if (Files.exists(aliasPath)) {
            return aliasPath;
        }

        if (descriptor != null && !descriptor.isBlank()) {
            Path descriptorPath = instancesDir.resolve(descriptor.trim());
            if (Files.exists(descriptorPath)) {
                return descriptorPath;
            }
        }

        throw new IOException("Não foi possível encontrar o ficheiro da instância para o alias '"
                + alias + "' em " + instancesDir);
    }
}
