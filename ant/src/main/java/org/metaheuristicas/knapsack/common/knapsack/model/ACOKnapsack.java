package org.metaheuristicas.knapsack.common.knapsack.model;

import org.metaheuristicas.knapsack.common.knapsack.core.AcoCore;
import org.metaheuristicas.knapsack.common.knapsack.io.InstanciaLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * ACO (MMAS simplificado) para o Problema da Mochila 0/1.
 *
 * <p><b>Objetivo desta classe</b>: atuar como <i>facade</i> (fachada) para deixar
 * explícito o "contrato de uso" do MAX-MIN Ant System (MMAS) no contexto de
 * meta-heurísticas. A implementação do ciclo de otimização continua em
 * {@link AcoCore}, enquanto esta classe:
 *
 * <ul>
 *   <li>centraliza validações de entrada;</li>
 *   <li>expõe uma API mais legível para experimentar parâmetros;</li>
 *   <li>isola o cliente dos detalhes internos de feromona/heurística.</li>
 * </ul>
 *
 * <p>Mapeamento dos principais parâmetros (documentação orientada a estudo):
 * <ul>
 *   <li>{@code numeroFormigas (m)}: número de soluções construídas por iteração;</li>
 *   <li>{@code numeroCiclos}: limite de iterações do MMAS;</li>
 *   <li>{@code alpha (α)}: peso da memória de feromona;</li>
 *   <li>{@code beta (β)}: peso da heurística {@code valor/peso};</li>
 *   <li>{@code rho (ρ)}: taxa de evaporação de feromonas;</li>
 *   <li>{@code q}: intensidade de depósito (mantido para compatibilidade experimental);</li>
 *   <li>{@code ciclosSemMelhoria}: gatilho de reinicialização por estagnação;</li>
 *   <li>{@code seed}: semente para reprodutibilidade.</li>
 * </ul>
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

    /**
     * Construtor de fachada recomendado para estudos e relatórios.
     *
     * <p>Encapsula os parâmetros do MMAS em {@link ParametrosMMAS}, deixando
     * explícito o papel semântico de cada variável.
     */
    public ACOKnapsack(Item[] itens, long capacidade, ParametrosMMAS parametros) {
        this(
                itens,
                capacidade,
                parametros.numeroFormigas(),
                parametros.numeroCiclos(),
                parametros.alpha(),
                parametros.beta(),
                parametros.rho(),
                parametros.q(),
                parametros.ciclosSemMelhoria(),
                parametros.seed()
        );
    }

    /**
     * Executa o ciclo MMAS e devolve a melhor solução global encontrada.
     */
    public Solucao resolver() {
        return acoCore.resolver();
    }

    /**
     * Método utilitário de fachada para reduzir código boilerplate em clientes.
     */
    public static Solucao resolverComParametros(Item[] itens, long capacidade, ParametrosMMAS parametros) {
        return new ACOKnapsack(itens, capacidade, parametros).resolver();
    }

    public static Instancia carregarInstancia(Path path) throws IOException {
        return InstanciaLoader.carregar(path);
    }

    /**
     * "DTO" de parâmetros do MMAS, documentado para uso didático e experimental.
     *
     * <p>Usar {@link #padrao()} como ponto de partida e depois ajustar
     * explicitamente os campos necessários para cada experiência.
     */
    public record ParametrosMMAS(
            int numeroFormigas,
            int numeroCiclos,
            double alpha,
            double beta,
            double rho,
            double q,
            int ciclosSemMelhoria,
            long seed
    ) {
        public ParametrosMMAS {
            if (numeroFormigas <= 0) throw new IllegalArgumentException("numeroFormigas deve ser > 0");
            if (numeroCiclos <= 0) throw new IllegalArgumentException("numeroCiclos deve ser > 0");
            if (alpha <= 0.0 || beta <= 0.0) throw new IllegalArgumentException("alpha e beta devem ser > 0");
            if (rho <= 0.0 || rho >= 1.0) throw new IllegalArgumentException("rho deve estar no intervalo (0,1)");
            if (q <= 0.0) throw new IllegalArgumentException("q deve ser > 0");
            if (ciclosSemMelhoria <= 0) throw new IllegalArgumentException("ciclosSemMelhoria deve ser > 0");
        }

        /**
         * Configuração base típica para MMAS no problema da mochila.
         */
        public static ParametrosMMAS padrao() {
            return new ParametrosMMAS(
                    30,
                    300,
                    1.0,
                    3.0,
                    0.2,
                    1.0,
                    80,
                    12345L
            );
        }
    }
}
