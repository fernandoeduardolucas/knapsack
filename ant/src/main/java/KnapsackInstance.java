import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Estrutura imutável que representa uma instância do problema da mochila.
 */
public class KnapsackInstance {
    private final String alias;
    private final String sourceFile;
    private final long capacity;
    private final List<Item> items;
    private final InstanceMetadata metadata;
    private final int[] greedyOrder;

    public KnapsackInstance(String alias, String sourceFile, long capacity,
                            List<Item> items, InstanceMetadata metadata) {
        this.alias = alias;
        this.sourceFile = sourceFile;
        this.capacity = capacity;
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.metadata = metadata;
        this.greedyOrder = buildGreedyOrder(items);
    }

    /**
     * Pré-calcula a ordem gulosa por valor/peso para construir soluções iniciais
     * e para a pesquisa local opcional.
     */
    private int[] buildGreedyOrder(List<Item> items) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            order.add(i);
        }
        order.sort((a, b) -> {
            Item ia = items.get(a);
            Item ib = items.get(b);
            int cmp = Double.compare(ib.getHeuristic(), ia.getHeuristic());
            if (cmp != 0) {
                return cmp;
            }
            cmp = Long.compare(ib.getValue(), ia.getValue());
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(ia.getWeight(), ib.getWeight());
        });

        int[] result = new int[order.size()];
        for (int i = 0; i < order.size(); i++) {
            result[i] = order.get(i);
        }
        return result;
    }

    public String getAlias() {
        return alias;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public long getCapacity() {
        return capacity;
    }

    public List<Item> getItems() {
        return items;
    }

    public InstanceMetadata getMetadata() {
        return metadata;
    }

    public int getItemCount() {
        return items.size();
    }

    public int[] getGreedyOrder() {
        return greedyOrder;
    }
}
