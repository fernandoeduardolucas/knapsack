import java.util.ArrayList;
import java.util.List;

/**
 * Representa uma solução candidata da mochila.
 *
 * selected[i] = true significa que o item i foi incluído.
 */
public class KnapsackSolution {
    private final boolean[] selected;
    private long totalValue;
    private long totalWeight;
    private int selectedCount;

    public KnapsackSolution(int itemCount) {
        this.selected = new boolean[itemCount];
        this.totalValue = 0L;
        this.totalWeight = 0L;
        this.selectedCount = 0;
    }

    /**
     * Cria uma cópia independente da solução.
     */
    public KnapsackSolution copy() {
        KnapsackSolution clone = new KnapsackSolution(selected.length);
        System.arraycopy(this.selected, 0, clone.selected, 0, this.selected.length);
        clone.totalValue = this.totalValue;
        clone.totalWeight = this.totalWeight;
        clone.selectedCount = this.selectedCount;
        return clone;
    }

    public boolean isSelected(int index) {
        return selected[index];
    }

    /**
     * Adiciona um item à solução, atualizando valor, peso e contagem.
     */
    public void add(int index, Item item) {
        if (!selected[index]) {
            selected[index] = true;
            totalValue += item.getValue();
            totalWeight += item.getWeight();
            selectedCount++;
        }
    }

    public long getTotalValue() {
        return totalValue;
    }

    public long getTotalWeight() {
        return totalWeight;
    }

    public int getSelectedCount() {
        return selectedCount;
    }

    public boolean[] getSelectedArray() {
        return selected;
    }

    /**
     * Devolve a capacidade ainda livre na mochila.
     */
    public long getRemainingCapacity(long capacity) {
        return capacity - totalWeight;
    }

    /**
     * Devolve os índices dos itens escolhidos, útil para logs e CSV.
     */
    public List<Integer> getSelectedIndexes() {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                indexes.add(i);
            }
        }
        return indexes;
    }
}
