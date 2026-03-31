import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AntColonyKnapsack {

    static class Item {
        int weight;
        int value;

        Item(int weight, int value) {
            this.weight = weight;
            this.value = value;
        }

        double heuristic() {
            return (double) value / weight;
        }
    }

    static class Solution {
        boolean[] selected;
        int totalWeight;
        int totalValue;

        Solution(int n) {
            selected = new boolean[n];
            totalWeight = 0;
            totalValue = 0;
        }

        Solution copy() {
            Solution s = new Solution(selected.length);
            System.arraycopy(this.selected, 0, s.selected, 0, selected.length);
            s.totalWeight = this.totalWeight;
            s.totalValue = this.totalValue;
            return s;
        }
    }

    private final Item[] items;
    private final int capacity;
    private final int numAnts;
    private final int maxIterations;
    private final double alpha;      // importância da feromona
    private final double beta;       // importância da heurística
    private final double evaporation;
    private final double q;          // quantidade de depósito de feromona
    private final double[] pheromone;
    private final Random random;

    public AntColonyKnapsack(Item[] items, int capacity, int numAnts, int maxIterations,
                             double alpha, double beta, double evaporation, double q) {
        this.items = items;
        this.capacity = capacity;
        this.numAnts = numAnts;
        this.maxIterations = maxIterations;
        this.alpha = alpha;
        this.beta = beta;
        this.evaporation = evaporation;
        this.q = q;
        this.pheromone = new double[items.length];
        this.random = new Random();

        for (int i = 0; i < pheromone.length; i++) {
            pheromone[i] = 1.0;
        }
    }

    public Solution solve() {
        Solution globalBest = new Solution(items.length);

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            List<Solution> antSolutions = new ArrayList<>();
            Solution iterationBest = new Solution(items.length);

            for (int ant = 0; ant < numAnts; ant++) {
                Solution solution = constructSolution();
                antSolutions.add(solution);

                if (solution.totalValue > iterationBest.totalValue) {
                    iterationBest = solution.copy();
                }

                if (solution.totalValue > globalBest.totalValue) {
                    globalBest = solution.copy();
                }
            }

            evaporatePheromones();
            updatePheromones(antSolutions, globalBest);

            System.out.printf("Iteração %d -> Melhor valor da iteração: %d | Melhor global: %d%n",
                    iteration + 1, iterationBest.totalValue, globalBest.totalValue);
        }

        return globalBest;
    }

    private Solution constructSolution() {
        Solution solution = new Solution(items.length);
        boolean[] visited = new boolean[items.length];

        while (true) {
            List<Integer> feasibleItems = new ArrayList<>();

            for (int i = 0; i < items.length; i++) {
                if (!visited[i] && solution.totalWeight + items[i].weight <= capacity) {
                    feasibleItems.add(i);
                }
            }

            if (feasibleItems.isEmpty()) {
                break;
            }

            int chosen = selectNextItem(feasibleItems);
            visited[chosen] = true;

            solution.selected[chosen] = true;
            solution.totalWeight += items[chosen].weight;
            solution.totalValue += items[chosen].value;
        }

        return solution;
    }

    private int selectNextItem(List<Integer> feasibleItems) {
        double sum = 0.0;
        double[] probabilities = new double[feasibleItems.size()];

        for (int i = 0; i < feasibleItems.size(); i++) {
            int itemIndex = feasibleItems.get(i);
            double tau = Math.pow(pheromone[itemIndex], alpha);
            double eta = Math.pow(items[itemIndex].heuristic(), beta);
            probabilities[i] = tau * eta;
            sum += probabilities[i];
        }

        if (sum == 0.0) {
            return feasibleItems.get(random.nextInt(feasibleItems.size()));
        }

        double r = random.nextDouble() * sum;
        double cumulative = 0.0;

        for (int i = 0; i < feasibleItems.size(); i++) {
            cumulative += probabilities[i];
            if (r <= cumulative) {
                return feasibleItems.get(i);
            }
        }

        return feasibleItems.get(feasibleItems.size() - 1);
    }

    private void evaporatePheromones() {
        for (int i = 0; i < pheromone.length; i++) {
            pheromone[i] *= (1.0 - evaporation);

            if (pheromone[i] < 0.0001) {
                pheromone[i] = 0.0001;
            }
        }
    }

    private void updatePheromones(List<Solution> antSolutions, Solution globalBest) {
        // depósito por todas as formigas
        for (Solution solution : antSolutions) {
            if (solution.totalValue > 0) {
                double deposit = q * solution.totalValue;

                for (int i = 0; i < items.length; i++) {
                    if (solution.selected[i]) {
                        pheromone[i] += deposit / 1000.0;
                    }
                }
            }
        }

        // reforço extra para a melhor solução global
        if (globalBest.totalValue > 0) {
            double bonus = q * globalBest.totalValue / 500.0;

            for (int i = 0; i < items.length; i++) {
                if (globalBest.selected[i]) {
                    pheromone[i] += bonus;
                }
            }
        }
    }

    public static void main(String[] args) {
        Item[] items = {
                new Item(12, 24),
                new Item(7, 13),
                new Item(11, 23),
                new Item(8, 15),
                new Item(9, 16),
                new Item(6, 12),
                new Item(7, 14),
                new Item(3, 8),
                new Item(5, 9)
        };

        int capacity = 35;

        AntColonyKnapsack aco = new AntColonyKnapsack(
                items,
                capacity,
                20,     // número de formigas
                100,    // número de iterações
                1.0,    // alpha
                2.0,    // beta
                0.1,    // evaporação
                1.0     // Q
        );

        Solution best = aco.solve();

        System.out.println("\n=== Melhor solução encontrada ===");
        System.out.println("Valor total: " + best.totalValue);
        System.out.println("Peso total: " + best.totalWeight);
        System.out.print("Itens selecionados: ");

        for (int i = 0; i < best.selected.length; i++) {
            if (best.selected[i]) {
                System.out.print(i + " ");
            }
        }
        System.out.println();
    }
}