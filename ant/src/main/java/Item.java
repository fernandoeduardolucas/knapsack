/**
 * Representa um item da mochila.
 */
public class Item {
    private final int index;
    private final long value;
    private final long weight;
    private double heuristic;

    public Item(int index, long value, long weight) {
        this.index = index;
        this.value = value;
        this.weight = weight;
        this.heuristic = 0.0;
    }

    public int getIndex() {
        return index;
    }

    public long getValue() {
        return value;
    }

    public long getWeight() {
        return weight;
    }

    public double getHeuristic() {
        return heuristic;
    }

    public void setHeuristic(double heuristic) {
        this.heuristic = heuristic;
    }
}
