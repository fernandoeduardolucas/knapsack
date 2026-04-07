package org.metaheuristicas.knapsack.common.knapsack.model;

import org.metaheuristicas.knapsack.common.knapsack.core.AcoCore;
import org.metaheuristicas.knapsack.common.knapsack.io.InstanciaLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ACO (MMAS simplificado) para o Problema da Mochila 0/1.
 *
 * <p>Esta classe é uma fachada: mantém API/CLI e delega o algoritmo para
 * {@link AcoCore}, onde está a implementação comentada
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


    public Solucao resolver() {
        return acoCore.resolver();
    }

    public static Instancia carregarInstancia(Path path) throws IOException {
        return InstanciaLoader.carregar(path);
    }

}
