package org.metaheuristicas.knapsack;

import org.metaheuristicas.knapsack.tabusearch.algo.TabuSearchCore;
import org.metaheuristicas.knapsack.common.knapsack.io.InstanciaLoader;
import org.metaheuristicas.knapsack.common.knapsack.model.Instancia;
import org.metaheuristicas.knapsack.common.knapsack.model.Item;
import org.metaheuristicas.knapsack.common.knapsack.model.Solucao;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Tabu Search para o Problema da Mochila 0/1.
 *
 * <p>Esta classe é uma fachada: mantém API/CLI e delega o algoritmo para
 * {@link org.metaheuristicas.knapsack.tabusearch.algo.TabuSearchCore}.
 */
public class TSKnapsack {

    private final TabuSearchCore tsCore;

    public TSKnapsack(
            Item[] itens,
            long capacidade,
            int iteracoes,
            int tenureFlip,
            int tenureSwap,
            int limiteSemMelhoria,
            double diversifyStrength,
            long seed
    ) {
        if (capacidade <= 0) throw new IllegalArgumentException("Capacidade deve ser > 0");
        if (itens == null || itens.length == 0) throw new IllegalArgumentException("Lista de itens vazia");
        if (iteracoes <= 0) throw new IllegalArgumentException("Parâmetros de execução inválidos");

        this.tsCore = new TabuSearchCore(
                itens,
                capacidade,
                iteracoes,
                tenureFlip,
                tenureSwap,
                limiteSemMelhoria,
                diversifyStrength,
                seed
        );
    }

    /**
     * Construtor com parâmetros por omissão otimizados para instâncias típicas.
     */
    public TSKnapsack(Item[] itens, long capacidade) {
        this(itens, capacidade, 3000, 15, 10, 100, 0.2, System.nanoTime());
    }

    public Solucao resolver() {
        return tsCore.resolver();
    }

    public static Instancia carregarInstancia(Path path) throws IOException {
        return InstanciaLoader.carregar(path);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Utilização: java org.metaheuristicas.knapsack.TSKnapsack <ficheiro-instancia> [--iters N] [--tenure-flip N] [--tenure-swap N] [--stall N] [--diversify D] [--seed S]");
            System.exit(1);
        }

        Path instanciaPath = Path.of(args[0]);
        int iters = 3000;
        int tenureFlip = 15;
        int tenureSwap = 10;
        int stall = 100;
        double diversify = 0.2;
        long seed = System.nanoTime();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--iters" -> iters = Integer.parseInt(args[++i]);
                case "--tenure-flip" -> tenureFlip = Integer.parseInt(args[++i]);
                case "--tenure-swap" -> tenureSwap = Integer.parseInt(args[++i]);
                case "--stall" -> stall = Integer.parseInt(args[++i]);
                case "--diversify" -> diversify = Double.parseDouble(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                default -> throw new IllegalArgumentException("Parâmetro desconhecido: " + arg);
            }
        }

        Instancia instancia = carregarInstancia(instanciaPath);
        TSKnapsack ts = new TSKnapsack(
                instancia.itens,
                instancia.capacidade,
                iters,
                tenureFlip,
                tenureSwap,
                stall,
                diversify,
                seed
        );

        Solucao melhor = ts.resolver();
        System.out.println("Ficheiro: " + instanciaPath);
        System.out.println("Capacidade: " + instancia.capacidade);
        System.out.println("Itens: " + instancia.itens.length);
        System.out.println("Melhor valor: " + melhor.valorTotal);
        System.out.println("Peso total: " + melhor.pesoTotal);
        System.out.print("Itens escolhidos (índices): ");
        for (int i = 0; i < instancia.itens.length; i++) {
            if (melhor.escolhidos[i]) {
                System.out.print(i + " ");
            }
        }
        System.out.println();
    }
}
