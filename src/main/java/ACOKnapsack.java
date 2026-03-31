import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * ACO para o Problema da Mochila 0/1.
 *
 * Uso CLI:
 *   java ACOKnapsack <arquivo-instancia>
 *      [--ants N] [--iters N]
 *      [--alpha A] [--beta B]
 *      [--rho R] [--q Q] [--elite E]
 *      [--seed S]
 *
 * Formato da instância (dataset do repositório):
 * - 1ª linha: n
 * - próximas n linhas: id lucro peso
 * - última linha: capacidade c
 */
public class ACOKnapsack {

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

    public static class Instancia {
        public final Item[] itens;
        public final int capacidade;

        public Instancia(Item[] itens, int capacidade) {
            this.itens = itens;
            this.capacidade = capacidade;
        }
    }

    private final Item[] itens;
    private final int capacidade;

    private final int numFormigas;
    private final int iteracoes;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final double q;
    private final double fatorElite;

    private final Random rng;
    private double[] tau;
    private double[] eta;

    public ACOKnapsack(Item[] itens,
                       int capacidade,
                       int numFormigas,
                       int iteracoes,
                       double alpha,
                       double beta,
                       double rho,
                       double q,
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
        this.q = q;
        this.fatorElite = fatorElite;
        this.rng = new Random(seed);
        inicializarEstruturas();
    }

    public ACOKnapsack(Item[] itens, int capacidade) {
        this(itens, capacidade, 30, 200, 1.0, 3.0, 0.30, 0.01, 2.0, System.nanoTime());
    }

    private void inicializarEstruturas() {
        int n = itens.length;
        tau = new double[n];
        eta = new double[n];
        Arrays.fill(tau, 1.0);

        double maxRatio = 0.0;
        for (Item it : itens) {
            maxRatio = Math.max(maxRatio, (double) it.valor / it.peso);
        }
        if (maxRatio == 0.0) maxRatio = 1.0;

        for (int i = 0; i < n; i++) {
            double ratio = (double) itens[i].valor / itens[i].peso;
            eta[i] = ratio / maxRatio;
        }
    }

    public Solucao resolver() {
        Solucao melhorGlobal = null;

        for (int t = 0; t < iteracoes; t++) {
            List<Solucao> solucoes = new ArrayList<>(numFormigas);

            for (int k = 0; k < numFormigas; k++) {
                Solucao s = construirSolucao();
                solucoes.add(s);
                if (melhorGlobal == null || s.valorTotal > melhorGlobal.valorTotal) {
                    melhorGlobal = s;
                }
            }

            evaporarFeromonas();

            for (Solucao s : solucoes) {
                depositarFeromonas(s, 1.0);
            }

            if (melhorGlobal != null) {
                depositarFeromonas(melhorGlobal, fatorElite);
            }

            limitarFeromonas(1e-6, 1e6);
        }

        return melhorGlobal;
    }

    private Solucao construirSolucao() {
        int n = itens.length;
        boolean[] escolhidos = new boolean[n];
        int peso = 0;
        int valor = 0;

        boolean progresso;
        do {
            progresso = false;

            List<Integer> candidatos = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!escolhidos[i] && (peso + itens[i].peso) <= capacidade) {
                    candidatos.add(i);
                }
            }

            if (candidatos.isEmpty()) break;

            double[] atr = new double[candidatos.size()];
            double soma = 0.0;
            for (int idx = 0; idx < candidatos.size(); idx++) {
                int i = candidatos.get(idx);
                double a = Math.pow(tau[i], alpha) * Math.pow(eta[i], beta);
                if (a < 1e-12) a = 1e-12;
                atr[idx] = a;
                soma += a;
            }

            if (soma <= 0.0) break;

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
                escolhidoIdx = candidatos.get(candidatos.size() - 1);
            }

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
        double deposito = q * s.valorTotal * multiplicador;
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

    public static Instancia carregarInstancia(Path path) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String primeira = br.readLine();
            if (primeira == null) {
                throw new IllegalArgumentException("Instância vazia: " + path);
            }

            int n = Integer.parseInt(primeira.trim());
            if (n <= 0) {
                throw new IllegalArgumentException("Número de itens inválido em " + path + ": " + n);
            }

            Item[] itens = new Item[n];
            for (int i = 0; i < n; i++) {
                String linha = br.readLine();
                if (linha == null) {
                    throw new IllegalArgumentException("Faltam linhas de itens em " + path + " (esperado: " + n + ")");
                }

                String[] partes = linha.trim().split("\\s+");
                if (partes.length < 3) {
                    throw new IllegalArgumentException("Linha de item inválida em " + path + ": \"" + linha + "\"");
                }

                int lucro = Integer.parseInt(partes[1]);
                int peso = Integer.parseInt(partes[2]);
                itens[i] = new Item(peso, lucro);
            }

            String capacidadeLinha = br.readLine();
            if (capacidadeLinha == null) {
                throw new IllegalArgumentException("Linha de capacidade ausente em " + path);
            }
            int capacidade = Integer.parseInt(capacidadeLinha.trim());
            return new Instancia(itens, capacidade);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: java ACOKnapsack <arquivo-instancia> [--ants N] [--iters N] [--alpha A] [--beta B] [--rho R] [--q Q] [--elite E] [--seed S]");
            System.exit(1);
        }

        Path instanciaPath = Path.of(args[0]);
        int ants = 30;
        int iters = 200;
        double alpha = 1.0;
        double beta = 3.0;
        double rho = 0.30;
        double q = 0.01;
        double elite = 2.0;
        long seed = System.nanoTime();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--ants" -> ants = Integer.parseInt(args[++i]);
                case "--iters" -> iters = Integer.parseInt(args[++i]);
                case "--alpha" -> alpha = Double.parseDouble(args[++i]);
                case "--beta" -> beta = Double.parseDouble(args[++i]);
                case "--rho" -> rho = Double.parseDouble(args[++i]);
                case "--q" -> q = Double.parseDouble(args[++i]);
                case "--elite" -> elite = Double.parseDouble(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                default -> throw new IllegalArgumentException("Parâmetro desconhecido: " + arg);
            }
        }

        Instancia instancia = carregarInstancia(instanciaPath);

        ACOKnapsack aco = new ACOKnapsack(
                instancia.itens,
                instancia.capacidade,
                ants,
                iters,
                alpha,
                beta,
                rho,
                q,
                elite,
                seed
        );

        Solucao melhor = aco.resolver();

        System.out.println("Arquivo: " + instanciaPath);
        System.out.println("Capacidade: " + instancia.capacidade);
        System.out.println("Itens: " + instancia.itens.length);
        System.out.println("Melhor valor: " + melhor.valorTotal);
        System.out.println("Peso total: " + melhor.pesoTotal);
        System.out.print("Itens escolhidos (índices): ");
        for (int i = 0; i < instancia.itens.length; i++) {
            if (melhor.escolhidos[i]) System.out.print(i + " ");
        }
        System.out.println();
    }
}
