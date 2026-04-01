package org.metaheuristicas.knapsack;

import org.metaheuristicas.knapsack.knapsack.algo.AcoCore;
import org.metaheuristicas.knapsack.common.knapsack.io.InstanciaLoader;
import org.metaheuristicas.knapsack.common.knapsack.model.Instancia;
import org.metaheuristicas.knapsack.common.knapsack.model.Item;
import org.metaheuristicas.knapsack.common.knapsack.model.Solucao;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ACO (MMAS simplificado) para o Problema da Mochila 0/1.
 *
 * <p>Esta classe é uma fachada: mantém API/CLI e delega o algoritmo para
 * {@link org.metaheuristicas.knapsack.knapsack.algo.AcoCore}, onde está a implementação comentada
 * com base no documento {@code docs/03_Colonia_Formigas.docx}.
 */
public class ACOKnapsack {

    private final AcoCore acoCore;

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

        this.acoCore = new AcoCore(
                itens,
                capacidade,
                numFormigas,
                iteracoes,
                alpha,
                beta,
                rho,
                q,
                limiteSemMelhoria,
                seed
        );
    }

    public ACOKnapsack(Item[] itens, long capacidade) {
        this(itens, capacidade, 30, 300, 1.0, 3.0, 0.2, 1.0, 80, System.nanoTime());
    }

    public Solucao resolver() {
        return acoCore.resolver();
    }

    public static Instancia carregarInstancia(Path path) throws IOException {
        return InstanciaLoader.carregar(path);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Utilização: java org.metaheuristicas.knapsack.ACOKnapsack <ficheiro-instancia> [--ants N] [--iters N] [--alpha A] [--beta B] [--rho R] [--q Q] [--stall N] [--seed S]");
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
