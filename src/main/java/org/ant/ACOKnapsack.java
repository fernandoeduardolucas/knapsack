package org.ant;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * ACO (MMAS simplificado) para o Problema da Mochila 0/1.
 */
public class ACOKnapsack {

    public static class Item {
        public final long peso;
        public final long valor;

        public Item(long peso, long valor) {
            if (peso <= 0) throw new IllegalArgumentException("Peso deve ser > 0");
            if (valor < 0) throw new IllegalArgumentException("Valor não pode ser negativo");
            this.peso = peso;
            this.valor = valor;
        }
    }

    public static class Solucao {
        public final boolean[] escolhidos;
        public final long valorTotal;
        public final long pesoTotal;

        public Solucao(boolean[] escolhidos, long valorTotal, long pesoTotal) {
            this.escolhidos = escolhidos;
            this.valorTotal = valorTotal;
            this.pesoTotal = pesoTotal;
        }
    }

    public static class Instancia {
        public final Item[] itens;
        public final long capacidade;

        public Instancia(Item[] itens, long capacidade) {
            this.itens = itens;
            this.capacidade = capacidade;
        }
    }

    private final Item[] itens;
    private final long capacidade;

    private final int numFormigas;
    private final int iteracoes;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final double q;
    private final int limiteSemMelhoria;

    private final Random rng;
    private double[] tau;
    private double[] eta;

    public ACOKnapsack(
            Item[] itens,
            long capacidade,
            int numFormigas,
            int iteracoes,
            double alpha,
            double beta,
            double rho,
            double q,
            int limiteSemMelhoria,
            long seed
    ) {
        if (capacidade <= 0) throw new IllegalArgumentException("Capacidade deve ser > 0");
        if (itens == null || itens.length == 0) throw new IllegalArgumentException("Lista de itens vazia");
        if (numFormigas <= 0 || iteracoes <= 0) throw new IllegalArgumentException("Parâmetros de execução inválidos");
        this.itens = itens;
        this.capacidade = capacidade;
        this.numFormigas = numFormigas;
        this.iteracoes = iteracoes;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.q = q;
        this.limiteSemMelhoria = limiteSemMelhoria;
        this.rng = new Random(seed);
        inicializarEstruturas();
    }

    public ACOKnapsack(Item[] itens, long capacidade) {
        this(itens, capacidade, 30, 300, 1.0, 3.0, 0.2, 1.0, 80, System.nanoTime());
    }

    private void inicializarEstruturas() {
        int n = itens.length;
        tau = new double[n];
        eta = new double[n];

        double maxRatio = 0.0;
        for (Item it : itens) {
            maxRatio = Math.max(maxRatio, (double) it.valor / it.peso);
        }
        if (maxRatio == 0.0) maxRatio = 1.0;

        for (int i = 0; i < n; i++) {
            double ratio = (double) itens[i].valor / itens[i].peso;
            eta[i] = ratio / maxRatio;
        }

        Arrays.fill(tau, 1.0);
    }

    public Solucao resolver() {
        Solucao melhorGlobal = buscaGulosaInicial();
        atualizarLimitesFeromona(melhorGlobal);

        int semMelhoria = 0;
        for (int t = 0; t < iteracoes; t++) {
            Solucao melhorIteracao = null;

            for (int k = 0; k < numFormigas; k++) {
                Solucao s = construirSolucao();
                s = buscaLocal1Flip(s);
                if (melhorIteracao == null || s.valorTotal > melhorIteracao.valorTotal) {
                    melhorIteracao = s;
                }
            }

            if (melhorIteracao != null && melhorIteracao.valorTotal > melhorGlobal.valorTotal) {
                melhorGlobal = melhorIteracao;
                semMelhoria = 0;
                atualizarLimitesFeromona(melhorGlobal);
            } else {
                semMelhoria++;
            }

            evaporarFeromonas();
            if (melhorGlobal != null) {
                depositarFeromonas(melhorGlobal);
            }
            limitarFeromonas();

            if (semMelhoria >= limiteSemMelhoria) {
                reinicializarFeromonas();
                semMelhoria = 0;
            }
        }

        return melhorGlobal;
    }

    private Solucao buscaGulosaInicial() {
        Integer[] idx = new Integer[itens.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare((double) itens[b].valor / itens[b].peso, (double) itens[a].valor / itens[a].peso));

        boolean[] esc = new boolean[itens.length];
        long peso = 0;
        long valor = 0;
        for (int i : idx) {
            if (peso + itens[i].peso <= capacidade) {
                esc[i] = true;
                peso += itens[i].peso;
                valor += itens[i].valor;
            }
        }

        return new Solucao(esc, valor, peso);
    }

    private Solucao construirSolucao() {
        int n = itens.length;
        boolean[] escolhidos = new boolean[n];
        long peso = 0;
        long valor = 0;

        List<Integer> naoVisitados = new ArrayList<>(n);
        for (int i = 0; i < n; i++) naoVisitados.add(i);

        while (!naoVisitados.isEmpty()) {
            List<Integer> candidatos = new ArrayList<>();
            for (int i : naoVisitados) {
                if (peso + itens[i].peso <= capacidade) {
                    candidatos.add(i);
                }
            }
            if (candidatos.isEmpty()) break;

            int escolhido = selecionarProximo(candidatos);
            escolhidos[escolhido] = true;
            peso += itens[escolhido].peso;
            valor += itens[escolhido].valor;
            naoVisitados.remove((Integer) escolhido);
        }

        return new Solucao(escolhidos, valor, peso);
    }

    private int selecionarProximo(List<Integer> candidatos) {
        double[] probabilidades = new double[candidatos.size()];
        double soma = 0.0;

        for (int i = 0; i < candidatos.size(); i++) {
            int idx = candidatos.get(i);
            double atratividade = Math.pow(tau[idx], alpha) * Math.pow(eta[idx], beta);
            if (atratividade < 1e-12) atratividade = 1e-12;
            probabilidades[i] = atratividade;
            soma += atratividade;
        }

        double r = rng.nextDouble() * soma;
        double acumulado = 0.0;
        for (int i = 0; i < candidatos.size(); i++) {
            acumulado += probabilidades[i];
            if (acumulado >= r) {
                return candidatos.get(i);
            }
        }
        return candidatos.get(candidatos.size() - 1);
    }

    private Solucao buscaLocal1Flip(Solucao base) {
        boolean[] esc = Arrays.copyOf(base.escolhidos, base.escolhidos.length);
        long melhorValor = base.valorTotal;
        long melhorPeso = base.pesoTotal;
        boolean melhorou;

        do {
            melhorou = false;
            for (int i = 0; i < esc.length; i++) {
                boolean novoEstado = !esc[i];
                long novoPeso = melhorPeso + (novoEstado ? itens[i].peso : -itens[i].peso);
                long novoValor = melhorValor + (novoEstado ? itens[i].valor : -itens[i].valor);
                if (novoEstado && novoPeso > capacidade) {
                    continue;
                }
                if (novoValor > melhorValor) {
                    esc[i] = novoEstado;
                    melhorValor = novoValor;
                    melhorPeso = novoPeso;
                    melhorou = true;
                }
            }
        } while (melhorou);

        return new Solucao(esc, melhorValor, melhorPeso);
    }

    private double tauMin = 1e-6;
    private double tauMax = 1e6;

    private void atualizarLimitesFeromona(Solucao melhor) {
        double z = Math.max(1.0, melhor.valorTotal);
        tauMax = 1.0 / (rho * z);
        tauMin = tauMax / (2.0 * itens.length);
        if (tauMin <= 0.0 || !Double.isFinite(tauMin)) tauMin = 1e-6;
        if (tauMax <= tauMin || !Double.isFinite(tauMax)) tauMax = tauMin * 1000.0;
    }

    private void evaporarFeromonas() {
        double fator = Math.max(0.0, 1.0 - rho);
        for (int i = 0; i < tau.length; i++) {
            tau[i] *= fator;
        }
    }

    private void depositarFeromonas(Solucao s) {
        double deposito = q / Math.max(1.0, s.valorTotal);
        for (int i = 0; i < s.escolhidos.length; i++) {
            if (s.escolhidos[i]) {
                tau[i] += deposito;
            }
        }
    }

    private void limitarFeromonas() {
        for (int i = 0; i < tau.length; i++) {
            if (tau[i] < tauMin) tau[i] = tauMin;
            if (tau[i] > tauMax) tau[i] = tauMax;
        }
    }

    private void reinicializarFeromonas() {
        Arrays.fill(tau, tauMax);
    }

    public static Instancia carregarInstancia(Path path) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String primeira = br.readLine();
            if (primeira == null) throw new IllegalArgumentException("Instância vazia: " + path);

            int n = Integer.parseInt(primeira.trim());
            if (n <= 0) throw new IllegalArgumentException("Número de itens inválido em " + path + ": " + n);

            Item[] itens = new Item[n];
            for (int i = 0; i < n; i++) {
                String linha = br.readLine();
                if (linha == null) throw new IllegalArgumentException("Faltam linhas de itens em " + path);

                String[] partes = linha.trim().split("\\s+");
                if (partes.length < 3) throw new IllegalArgumentException("Linha de item inválida: " + linha);

                long lucro = Long.parseLong(partes[1]);
                long peso = Long.parseLong(partes[2]);
                itens[i] = new Item(peso, lucro);
            }

            String capacidadeLinha = br.readLine();
            if (capacidadeLinha == null) throw new IllegalArgumentException("Linha de capacidade ausente em " + path);

            long capacidade = Long.parseLong(capacidadeLinha.trim());
            return new Instancia(itens, capacidade);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Uso: java org.ant.ACOKnapsack <arquivo-instancia> [--ants N] [--iters N] [--alpha A] [--beta B] [--rho R] [--q Q] [--stall N] [--seed S]");
            System.exit(1);
        }

        Path instanciaPath = Path.of(args[0]);
        int ants = 30;
        int iters = 300;
        double alpha = 1.0;
        double beta = 3.0;
        double rho = 0.2;
        double q = 1.0;
        int stall = 80;
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
                case "--stall" -> stall = Integer.parseInt(args[++i]);
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
                stall,
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
