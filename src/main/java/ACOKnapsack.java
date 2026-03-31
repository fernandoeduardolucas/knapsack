import java.util.*;

/**
 * ACO para o Problema da Mochila 0/1.
 * - Constrói soluções viáveis (não excede a capacidade).
 * - Probabilidades guiadas por feromonas (tau) e heurística (valor/peso).
 * - Evaporação + depósito por iteração + reforço elitista da melhor solução global.
 */
public class ACOKnapsack {

    /** Item da mochila */
    public static class Item {
        public final int peso;
        public final int valor;

        public Item(int peso, int valor) {
            if (peso <= 0) throw new IllegalArgumentException("Peso deve ser > 0");
            if (valor < 0) throw new IllegalArgumentException("Valor não pode ser negativo");
            this.peso = peso;
            this.valor = valor;
        }
    }

    /** Solução construída por uma formiga */
    public static class Solucao {
        public final boolean[] escolhidos;
        public final int valorTotal;
        public final int pesoTotal;

        public Solucao(boolean[] escolhidos, int valorTotal, int pesoTotal) {
            this.escolhidos = escolhidos;
            this.valorTotal = valorTotal;
            this.pesoTotal = pesoTotal;
        }
    }

    private final Item[] itens;
    private final int capacidade;

    // Parâmetros ACO
    private final int numFormigas;
    private final int iteracoes;
    private final double alpha;      // peso da feromona
    private final double beta;       // peso da heurística
    private final double rho;        // taxa de evaporação (0..1)
    private final double Q;          // intensidade de depósito
    private final double fatorElite; // reforço da melhor global

    private final Random rng;

    // Feromonas e heurísticas
    private double[] tau;  // feromona por item
    private double[] eta;  // heurística valor/peso normalizada

    /**
     * Construtor com parâmetros detalhados.
     */
    public ACOKnapsack(Item[] itens,
                       int capacidade,
                       int numFormigas,
                       int iteracoes,
                       double alpha,
                       double beta,
                       double rho,
                       double Q,
                       double fatorElite,
                       long seed) {
        if (capacidade <= 0) throw new IllegalArgumentException("Capacidade deve ser > 0");
        if (itens == null || itens.length == 0) throw new IllegalArgumentException("Lista de itens vazia");
        this.itens = itens;
        this.capacidade = capacidade;
        this.numFormigas = numFormigas;
        this.iteracoes = iteracoes;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.Q = Q;
        this.fatorElite = fatorElite;
        this.rng = new Random(seed);

        inicializarEstruturas();
    }

    /**
     * Construtor com parâmetros sugeridos (bons valores de partida).
     */
    public ACOKnapsack(Item[] itens, int capacidade) {
        this(itens,
                capacidade,
                30,     // numFormigas
                200,    // iteracoes
                1.0,    // alpha
                3.0,    // beta
                0.30,   // rho
                0.01,   // Q
                2.0,    // fatorElite
                System.nanoTime()); // seed
    }

    private void inicializarEstruturas() {
        int n = itens.length;
        tau = new double[n];
        eta = new double[n];

        // feromona inicial
        Arrays.fill(tau, 1.0);

        // heurística: valor/peso normalizado para evitar bias extremo
        double maxRatio = 0.0;
        for (Item it : itens) {
            maxRatio = Math.max(maxRatio, (double) it.valor / it.peso);
        }
        if (maxRatio == 0.0) maxRatio = 1.0;

        for (int i = 0; i < n; i++) {
            double ratio = (double) itens[i].valor / itens[i].peso;
            eta[i] = ratio / maxRatio; // fica em (0, 1]
        }
    }

    /**
     * Executa o ACO e devolve a melhor solução encontrada.
     */
    public Solucao resolver() {
        Solucao melhorGlobal = null;

        for (int t = 0; t < iteracoes; t++) {
            List<Solucao> solucoes = new ArrayList<>(numFormigas);

            // 1) Cada formiga constrói uma solução viável
            for (int k = 0; k < numFormigas; k++) {
                Solucao s = construirSolucao();
                solucoes.add(s);
                if (melhorGlobal == null || s.valorTotal > melhorGlobal.valorTotal) {
                    melhorGlobal = s;
                }
            }

            // 2) Atualização de feromonas
            evaporarFeromonas();

            // Depósito por iteração (todas as formigas)
            for (Solucao s : solucoes) {
                depositarFeromonas(s, 1.0);
            }

            // Reforço elitista (melhor global até agora)
            if (melhorGlobal != null) {
                depositarFeromonas(melhorGlobal, fatorElite);
            }

            // (Opcional) Limitar feromonas para evitar explosão numérica
            limitarFeromonas(1e-6, 1e6);
        }

        return melhorGlobal;
    }

    private Solucao construirSolucao() {
        int n = itens.length;
        boolean[] escolhidos = new boolean[n];
        int peso = 0;
        int valor = 0;

        // Conjunto de itens ainda elegíveis (não escolhidos e que cabem)
        boolean progresso;
        do {
            progresso = false;

            // Preparar lista de candidatos viáveis
            List<Integer> candidatos = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!escolhidos[i] && (peso + itens[i].peso) <= capacidade) {
                    candidatos.add(i);
                }
            }

            if (candidatos.isEmpty()) break;

            // Calcular atratividades
            double[] atr = new double[candidatos.size()];
            double soma = 0.0;
            for (int idx = 0; idx < candidatos.size(); idx++) {
                int i = candidatos.get(idx);
                double a = Math.pow(tau[i], alpha) * Math.pow(eta[i], beta);
                // Proteção contra zero absoluto
                if (a < 1e-12) a = 1e-12;
                atr[idx] = a;
                soma += a;
            }

            if (soma <= 0.0) break;

            // Seleção por roleta
            double r = rng.nextDouble() * soma;
            int escolhidoIdx = -1;
            double acum = 0.0;
            for (int idx = 0; idx < candidatos.size(); idx++) {
                acum += atr[idx];
                if (acum >= r) {
                    escolhidoIdx = candidatos.get(idx);
                    break;
                }
            }
            if (escolhidoIdx == -1) {
                // fallback (raro): escolher o último candidato
                escolhidoIdx = candidatos.get(candidatos.size() - 1);
            }

            // Incluir item escolhido (sempre cabe pela filtragem)
            escolhidos[escolhidoIdx] = true;
            peso += itens[escolhidoIdx].peso;
            valor += itens[escolhidoIdx].valor;
            progresso = true;

        } while (progresso && peso < capacidade);

        return new Solucao(escolhidos, valor, peso);
    }

    private void evaporarFeromonas() {
        double fator = Math.max(0.0, 1.0 - rho);
        for (int i = 0; i < tau.length; i++) {
            tau[i] *= fator;
        }
    }

    private void depositarFeromonas(Solucao s, double multiplicador) {
        // Depósito proporcional ao valor da solução
        double deposito = Q * s.valorTotal * multiplicador;
        if (deposito <= 0) return;
        for (int i = 0; i < s.escolhidos.length; i++) {
            if (s.escolhidos[i]) {
                tau[i] += deposito;
            }
        }
    }

    private void limitarFeromonas(double tauMin, double tauMax) {
        for (int i = 0; i < tau.length; i++) {
            if (tau[i] < tauMin) tau[i] = tauMin;
            if (tau[i] > tauMax) tau[i] = tauMax;
        }
    }

    // ------------------- Exemplo de uso -------------------

    public static void main(String[] args) {
        // Exemplo de itens (peso, valor)
        Item[] itens = new Item[]{
                new Item(12, 24),
                new Item(7, 13),
                new Item(11, 23),
                new Item(8, 15),
                new Item(9, 16),
                new Item(6, 12),
                new Item(5, 9),
                new Item(14, 28),
                new Item(3, 6),
                new Item(2, 4)
        };

        int capacidade = 35;

        ACOKnapsack aco = new ACOKnapsack(
                itens,
                capacidade,
                30,     // numFormigas
                200,    // iteracoes
                1.0,    // alpha (peso feromona)
                3.0,    // beta  (peso heurística)
                0.30,   // rho   (evaporação)
                0.01,   // Q     (intensidade de depósito)
                2.0,    // fatorElite (reforço melhor global)
                12345L  // seed reprodutível
        );

        Solucao melhor = aco.resolver();

        System.out.println("=== Melhor solução encontrada (ACO) ===");
        System.out.println("Valor total: " + melhor.valorTotal);
        System.out.println("Peso total: " + melhor.pesoTotal);
        System.out.print("Itens escolhidos (índices): ");
        for (int i = 0; i < itens.length; i++) {
            if (melhor.escolhidos[i]) System.out.print(i + " ");
        }
        System.out.println();
    }
}