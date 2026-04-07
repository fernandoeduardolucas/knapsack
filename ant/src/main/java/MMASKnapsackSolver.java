import java.util.Arrays;
import java.util.Random;

/**
 * Solver do Problema da Mochila 0/1 com MAX-MIN Ant System (MMAS).
 *
 * Implementação alinhada com a formulação teórica pedida pelo utilizador:
 * 1) cada formiga percorre todos os itens;
 * 2) para cada item i decide incluir/excluir com uma probabilidade P(x_i = 1);
 * 3) a probabilidade combina feromona e heurística valor/peso;
 * 4) só a melhor solução reforça as feromonas;
 * 5) as feromonas são limitadas a [tauMin, tauMax];
 * 6) existe reinicialização quando há estagnação.
 */
public class MMASKnapsackSolver {

    /**
     * Resolve uma instância do problema usando os parâmetros do MMAS.
     */
    public MmasResult solve(KnapsackInstance instance, MmasParameters parameters) {
        long start = System.currentTimeMillis();
        Random random = new Random(parameters.getSeed());
        int n = instance.getItemCount();

        // Solução gulosa usada como estimativa inicial de z*.
        // Isto permite calcular tauMax e tauMin como na descrição teórica do MMAS.
        KnapsackSolution greedyEstimate = greedyInitialSolution(instance);
        double zEstimate = Math.max(1.0, greedyEstimate.getTotalValue());
        double rho = Math.max(1.0e-9, parameters.getRho());

        // Fórmulas pedidas:
        // tauMax = 1 / (rho * z*_estimate)
        // tauMin = tauMax / (2n)
        double tauMax = 1.0 / (rho * zEstimate);
        double tauMin = tauMax / (2.0 * Math.max(1, n));

        // Todas as feromonas começam em tauMax, como no MMAS clássico.
        double[] pheromone = new double[n];
        Arrays.fill(pheromone, tauMax);

        KnapsackSolution globalBest = greedyEstimate.copy();
        int bestIteration = 0;
        int iterationsWithoutImprovement = 0;

        for (int iteration = 1; iteration <= parameters.getIterations(); iteration++) {
            KnapsackSolution iterationBest = null;
            boolean improvedGlobal = false;

            for (int ant = 0; ant < parameters.getAnts(); ant++) {
                KnapsackSolution candidate = constructSolution(
                        instance,
                        pheromone,
                        tauMin,
                        tauMax,
                        parameters,
                        random
                );

                if (parameters.isLocalSearch()) {
                    candidate = greedyCompletion(candidate, instance);
                }

                if (isBetter(candidate, iterationBest)) {
                    iterationBest = candidate;
                }
                if (isBetter(candidate, globalBest)) {
                    globalBest = candidate;
                    bestIteration = iteration;
                    iterationsWithoutImprovement = 0;
                    improvedGlobal = true;
                }
            }

            KnapsackSolution reference = parameters.isUseGlobalBest() ? globalBest : iterationBest;

            evaporate(pheromone, rho);
            reinforce(pheromone, reference);
            clampPheromones(pheromone, tauMin, tauMax);

            if (!improvedGlobal) {
                iterationsWithoutImprovement++;
            }

            // Reinicialização periódica para evitar estagnação.
            if (parameters.getRestartAfter() > 0
                    && iterationsWithoutImprovement >= parameters.getRestartAfter()) {
                Arrays.fill(pheromone, tauMax);
                iterationsWithoutImprovement = 0;
            }
        }

        long end = System.currentTimeMillis();
        return new MmasResult(globalBest, end - start, bestIteration);
    }

    /**
     * Constrói uma solução inicial gulosa baseada no rácio valor/peso.
     *
     * Esta solução serve para:
     * - obter uma referência inicial de boa qualidade;
     * - estimar z* para calcular tauMax e tauMin.
     */
    private KnapsackSolution greedyInitialSolution(KnapsackInstance instance) {
        KnapsackSolution solution = new KnapsackSolution(instance.getItemCount());
        for (int itemIndex : instance.getGreedyOrder()) {
            Item item = instance.getItems().get(itemIndex);
            if (solution.getTotalWeight() + item.getWeight() <= instance.getCapacity()) {
                solution.add(itemIndex, item);
            }
        }
        return solution;
    }

    /**
     * Constrói uma solução percorrendo todos os itens.
     *
     * Para cada item i:
     * - se o item não couber na capacidade residual, é excluído automaticamente;
     * - caso contrário, calcula-se P(x_i = 1) a partir da feromona e da heurística;
     * - depois é feita uma decisão binária incluir/excluir.
     *
     * A ordem é baralhada em cada formiga para aumentar a diversidade.
     */
    private KnapsackSolution constructSolution(KnapsackInstance instance,
                                               double[] pheromone,
                                               double tauMin,
                                               double tauMax,
                                               MmasParameters parameters,
                                               Random random) {
        int n = instance.getItemCount();
        KnapsackSolution solution = new KnapsackSolution(n);
        int[] order = shuffledOrder(n, random);

        for (int position = 0; position < order.length; position++) {
            int itemIndex = order[position];
            Item item = instance.getItems().get(itemIndex);
            long remainingCapacity = solution.getRemainingCapacity(instance.getCapacity());

            // Se já não existir qualquer item restante que caiba, a construção termina.
            if (!hasFeasibleRemainingItem(instance, order, position, remainingCapacity)) {
                break;
            }

            // Regra de feasibilidade: se não couber, não pode ser escolhido.
            if (item.getWeight() > remainingCapacity) {
                continue;
            }

            double heuristic = Math.max(item.getHeuristic(), 1.0e-12);

            // Como as feromonas do MMAS real vivem em [tauMin, tauMax] e não necessariamente em [0,1],
            // normalizamos para [0,1].
            // Assim conseguimos aplicar a fórmula pedida pelo utilizador:
            // P(x_i = 1) = (tau_i^alpha * eta_i^beta) /
            //              (tau_i^alpha * eta_i^beta + (1 - tau_i)^alpha)
            double normalizedTau = normalizePheromone(pheromone[itemIndex], tauMin, tauMax);

            double includeScore = Math.pow(normalizedTau, parameters.getAlpha())
                    * Math.pow(heuristic, parameters.getBeta());
            double excludeScore = Math.pow(Math.max(1.0 - normalizedTau, 1.0e-12), parameters.getAlpha());
            double denominator = includeScore + excludeScore;
            double includeProbability = denominator <= 0.0 ? 0.5 : (includeScore / denominator);

            if (random.nextDouble() <= includeProbability) {
                solution.add(itemIndex, item);
            }
        }

        return solution;
    }

    /**
     * Verifica se existe pelo menos um item ainda não processado que seja feasível
     * para a capacidade residual atual.
     */
    private boolean hasFeasibleRemainingItem(KnapsackInstance instance,
                                             int[] order,
                                             int currentPosition,
                                             long remainingCapacity) {
        for (int position = currentPosition; position < order.length; position++) {
            Item candidate = instance.getItems().get(order[position]);
            if (candidate.getWeight() <= remainingCapacity) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pequena pesquisa local opcional.
     *
     * A partir da solução construída pela formiga, tenta preencher a capacidade residual
     * adicionando itens ainda não escolhidos por ordem gulosa valor/peso.
     */
    private KnapsackSolution greedyCompletion(KnapsackSolution base, KnapsackInstance instance) {
        KnapsackSolution improved = base.copy();
        long remaining = improved.getRemainingCapacity(instance.getCapacity());

        for (int itemIndex : instance.getGreedyOrder()) {
            if (improved.isSelected(itemIndex)) {
                continue;
            }
            Item item = instance.getItems().get(itemIndex);
            if (item.getWeight() <= remaining) {
                improved.add(itemIndex, item);
                remaining -= item.getWeight();
            }
        }

        return improved;
    }

    /**
     * Evaporação global das feromonas:
     * tau_i <- (1 - rho) * tau_i
     */
    private void evaporate(double[] pheromone, double rho) {
        for (int i = 0; i < pheromone.length; i++) {
            pheromone[i] = pheromone[i] * (1.0 - rho);
        }
    }

    /**
     * Reforço apenas da melhor solução (da iteração ou global, conforme configuração).
     *
     * Delta tau_i = 1 / z(best) para cada item presente na solução de referência.
     */
    private void reinforce(double[] pheromone, KnapsackSolution solution) {
        if (solution == null || solution.getTotalValue() <= 0L) {
            return;
        }

        double delta = 1.0 / solution.getTotalValue();
        boolean[] selected = solution.getSelectedArray();
        for (int i = 0; i < pheromone.length; i++) {
            if (selected[i]) {
                pheromone[i] += delta;
            }
        }
    }

    /**
     * Garante que todas as feromonas ficam dentro de [tauMin, tauMax].
     */
    private void clampPheromones(double[] pheromone, double tauMin, double tauMax) {
        for (int i = 0; i < pheromone.length; i++) {
            if (pheromone[i] < tauMin) {
                pheromone[i] = tauMin;
            } else if (pheromone[i] > tauMax) {
                pheromone[i] = tauMax;
            }
        }
    }

    /**
     * Normaliza uma feromona real para [0,1].
     *
     * Fazemos isto porque a fórmula binária de decisão usa explicitamente (1 - tau_i),
     * o que pressupõe um valor interpretável como preferência/probabilidade relativa.
     */
    private double normalizePheromone(double tau, double tauMin, double tauMax) {
        if (tauMax <= tauMin) {
            return 0.5;
        }
        double normalized = (tau - tauMin) / (tauMax - tauMin);
        // Evita probabilidades exatamente 0 ou 1 para manter algum nível de exploração.
        return Math.max(1.0e-6, Math.min(1.0 - 1.0e-6, normalized));
    }

    /**
     * Gera uma permutação aleatória dos índices dos itens.
     */
    private int[] shuffledOrder(int n, Random random) {
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        for (int i = n - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = order[i];
            order[i] = order[j];
            order[j] = tmp;
        }
        return order;
    }

    /**
     * Critério de comparação entre soluções.
     *
     * Prioridade:
     * 1) maior valor total
     * 2) menor peso total (desempate)
     * 3) menos itens escolhidos (novo desempate)
     */
    private boolean isBetter(KnapsackSolution left, KnapsackSolution right) {
        if (left == null) {
            return false;
        }
        if (right == null) {
            return true;
        }
        if (left.getTotalValue() != right.getTotalValue()) {
            return left.getTotalValue() > right.getTotalValue();
        }
        if (left.getTotalWeight() != right.getTotalWeight()) {
            return left.getTotalWeight() < right.getTotalWeight();
        }
        return left.getSelectedCount() < right.getSelectedCount();
    }
}
