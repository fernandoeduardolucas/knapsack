package org.metaheuristicas.knapsack.tabusearch.algo;

import org.metaheuristicas.knapsack.common.knapsack.model.Item;
import org.metaheuristicas.knapsack.common.knapsack.model.Solucao;

import java.util.Arrays;
import java.util.Random;

/**
 * Núcleo do Tabu Search para o Problema da Mochila 0/1.
 *
 * <p>Implementação otimizada com:
 * <ul>
 *   <li><b>Vizinhança híbrida</b>: movimentos 1-flip (adicionar/remover item)
 *       e swap (trocar item dentro por item fora).</li>
 *   <li><b>Lista tabu</b>: memória de curto prazo que impede a revisita
 *       de movimentos recentes, com tenures separados para flip e swap.</li>
 *   <li><b>Critério de aspiração</b>: um movimento tabu é aceite se
 *       produzir solução melhor que a melhor global conhecida.</li>
 *   <li><b>Diversificação por frequência</b>: quando estagna, penaliza
 *       itens frequentemente manipulados para forçar exploração de novas
 *       regiões do espaço de soluções.</li>
 *   <li><b>Reinicialização adaptativa</b>: após muitas iterações sem
 *       melhoria, reinicia a partir de uma solução gulosa perturbada.</li>
 * </ul>
 */
public class TabuSearchCore {

    private final Item[] itens;
    private final long capacidade;
    private final int iteracoes;
    private final int tenureFlip;
    private final int tenureSwap;
    private final int limiteSemMelhoria;
    private final double diversifyStrength;
    private final Random rng;

    // Estado da pesquisa
    private boolean[] solucaoAtual;
    private long valorAtual;
    private long pesoAtual;

    // Melhor solução global
    private boolean[] melhorEscolhidos;
    private long melhorValor;
    private long melhorPeso;

    // Lista tabu: iteração em que o item fica livre
    private int[] tabuFlip;
    private int[] tabuSwapIn;
    private int[] tabuSwapOut;

    // Frequência de alteração de cada item (para diversificação)
    private long[] frequencia;

    public TabuSearchCore(
            Item[] itens,
            long capacidade,
            int iteracoes,
            int tenureFlip,
            int tenureSwap,
            int limiteSemMelhoria,
            double diversifyStrength,
            long seed
    ) {
        this.itens = itens;
        this.capacidade = capacidade;
        this.iteracoes = iteracoes;
        this.tenureFlip = tenureFlip;
        this.tenureSwap = tenureSwap;
        this.limiteSemMelhoria = limiteSemMelhoria;
        this.diversifyStrength = diversifyStrength;
        this.rng = new Random(seed);
    }

    /**
     * Executa o Tabu Search e devolve a melhor solução encontrada.
     */
    public Solucao resolver() {
        inicializar();

        int semMelhoria = 0;

        for (int iter = 0; iter < iteracoes; iter++) {
            boolean melhorou = explorarVizinhanca(iter, semMelhoria >= limiteSemMelhoria / 2);

            if (melhorou) {
                semMelhoria = 0;
            } else {
                semMelhoria++;
            }

            // Diversificação: reinicialização adaptativa quando estagna
            if (semMelhoria >= limiteSemMelhoria) {
                diversificar();
                semMelhoria = 0;
            }
        }

        return new Solucao(melhorEscolhidos, melhorValor, melhorPeso);
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Inicialização
    // ─────────────────────────────────────────────────────────────────────

    private void inicializar() {
        int n = itens.length;
        tabuFlip = new int[n];
        tabuSwapIn = new int[n];
        tabuSwapOut = new int[n];
        frequencia = new long[n];

        // Construir solução gulosa inicial (ordenada por valor/peso)
        construirSolucaoGulosa();

        // Guardar como melhor global
        melhorEscolhidos = Arrays.copyOf(solucaoAtual, n);
        melhorValor = valorAtual;
        melhorPeso = pesoAtual;
    }

    private void construirSolucaoGulosa() {
        int n = itens.length;
        Integer[] ordem = new Integer[n];
        for (int i = 0; i < n; i++) {
            ordem[i] = i;
        }

        Arrays.sort(ordem, (a, b) ->
                Double.compare(
                        (double) itens[b].valor / itens[b].peso,
                        (double) itens[a].valor / itens[a].peso
                )
        );

        solucaoAtual = new boolean[n];
        pesoAtual = 0;
        valorAtual = 0;

        for (int indice : ordem) {
            if (pesoAtual + itens[indice].peso <= capacidade) {
                solucaoAtual[indice] = true;
                pesoAtual += itens[indice].peso;
                valorAtual += itens[indice].valor;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Exploração da vizinhança (flip + swap)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Explora toda a vizinhança (flip e swap) e aplica o melhor movimento
     * admissível. Devolve true se a melhor solução global foi melhorada.
     */
    private boolean explorarVizinhanca(int iter, boolean usarDiversificacao) {
        int n = itens.length;

        // Melhor movimento encontrado nesta iteração
        int melhorTipo = -1; // 0=flip, 1=swap
        int melhorI = -1;
        int melhorJ = -1;
        long melhorDeltaValor = Long.MIN_VALUE;
        long melhorNovoPeso = -1;

        // ── Movimentos 1-flip ──
        for (int i = 0; i < n; i++) {
            long deltaValor;
            long novoPeso;

            if (solucaoAtual[i]) {
                // Remover item i
                deltaValor = -itens[i].valor;
                novoPeso = pesoAtual - itens[i].peso;
            } else {
                // Adicionar item i
                novoPeso = pesoAtual + itens[i].peso;
                if (novoPeso > capacidade) {
                    continue; // infeasível
                }
                deltaValor = itens[i].valor;
            }

            // Penalização por frequência (diversificação)
            long deltaEfetivo = deltaValor;
            if (usarDiversificacao) {
                deltaEfetivo -= (long) (diversifyStrength * frequencia[i]);
            }

            // Verificar se é tabu
            boolean isTabu = tabuFlip[i] > iter;

            // Critério de aspiração: aceitar se supera melhor global
            boolean aspira = (valorAtual + deltaValor) > melhorValor;

            if ((!isTabu || aspira) && deltaEfetivo > melhorDeltaValor) {
                melhorTipo = 0;
                melhorI = i;
                melhorJ = -1;
                melhorDeltaValor = deltaEfetivo;
                melhorNovoPeso = novoPeso;
            }
        }

        // ── Movimentos swap (trocar item dentro por item fora) ──
        for (int i = 0; i < n; i++) {
            if (!solucaoAtual[i]) continue; // i deve estar dentro

            for (int j = 0; j < n; j++) {
                if (solucaoAtual[j]) continue; // j deve estar fora
                if (i == j) continue;

                long novoPeso = pesoAtual - itens[i].peso + itens[j].peso;
                if (novoPeso > capacidade) {
                    continue; // infeasível
                }

                long deltaValor = -itens[i].valor + itens[j].valor;

                long deltaEfetivo = deltaValor;
                if (usarDiversificacao) {
                    deltaEfetivo -= (long) (diversifyStrength * (frequencia[i] + frequencia[j]));
                }

                // Verificar se é tabu (ambos os itens)
                boolean isTabu = tabuSwapOut[i] > iter || tabuSwapIn[j] > iter;
                boolean aspira = (valorAtual + deltaValor) > melhorValor;

                if ((!isTabu || aspira) && deltaEfetivo > melhorDeltaValor) {
                    melhorTipo = 1;
                    melhorI = i; // sai
                    melhorJ = j; // entra
                    melhorDeltaValor = deltaEfetivo;
                    melhorNovoPeso = novoPeso;
                }
            }
        }

        // Se não encontrou nenhum movimento admissível, forçar diversificação
        if (melhorTipo == -1) {
            return false;
        }

        // ── Aplicar o melhor movimento ──
        if (melhorTipo == 0) {
            // Flip
            aplicarFlip(melhorI, iter);
        } else {
            // Swap
            aplicarSwap(melhorI, melhorJ, iter);
        }

        // Verificar se melhorou a melhor global
        if (valorAtual > melhorValor) {
            melhorEscolhidos = Arrays.copyOf(solucaoAtual, n);
            melhorValor = valorAtual;
            melhorPeso = pesoAtual;
            return true;
        }

        return false;
    }

    private void aplicarFlip(int i, int iter) {
        if (solucaoAtual[i]) {
            // Remover
            solucaoAtual[i] = false;
            valorAtual -= itens[i].valor;
            pesoAtual -= itens[i].peso;
        } else {
            // Adicionar
            solucaoAtual[i] = true;
            valorAtual += itens[i].valor;
            pesoAtual += itens[i].peso;
        }

        tabuFlip[i] = iter + tenureFlip;
        frequencia[i]++;
    }

    private void aplicarSwap(int sai, int entra, int iter) {
        solucaoAtual[sai] = false;
        valorAtual -= itens[sai].valor;
        pesoAtual -= itens[sai].peso;

        solucaoAtual[entra] = true;
        valorAtual += itens[entra].valor;
        pesoAtual += itens[entra].peso;

        tabuSwapOut[sai] = iter + tenureSwap;
        tabuSwapIn[entra] = iter + tenureSwap;
        frequencia[sai]++;
        frequencia[entra]++;
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Diversificação
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Diversificação: reinicia a partir de uma solução gulosa perturbada.
     * Remove aleatoriamente uma fração dos itens e reconstrói com gulodice.
     */
    private void diversificar() {
        int n = itens.length;

        // Partir da melhor solução global
        solucaoAtual = Arrays.copyOf(melhorEscolhidos, n);
        valorAtual = melhorValor;
        pesoAtual = melhorPeso;

        // Remover aleatoriamente uma fração dos itens selecionados
        for (int i = 0; i < n; i++) {
            if (solucaoAtual[i] && rng.nextDouble() < diversifyStrength) {
                solucaoAtual[i] = false;
                valorAtual -= itens[i].valor;
                pesoAtual -= itens[i].peso;
            }
        }

        // Tentar adicionar itens não selecionados (guloso com perturbação)
        Integer[] ordem = new Integer[n];
        // Pré-calcular chaves de ordenação perturbadas (evita violar contrato do Comparator)
        double[] chaveOrdenacao = new double[n];
        for (int i = 0; i < n; i++) {
            ordem[i] = i;
            double razao = (double) itens[i].valor / itens[i].peso;
            // Perturbação: multiplicador aleatório entre 0.8 e 1.2
            chaveOrdenacao[i] = razao * (0.8 + rng.nextDouble() * 0.4);
        }

        // Ordenar por chave perturbada (determinístico, transitivo)
        Arrays.sort(ordem, (a, b) -> Double.compare(chaveOrdenacao[b], chaveOrdenacao[a]));

        for (int indice : ordem) {
            if (!solucaoAtual[indice] && pesoAtual + itens[indice].peso <= capacidade) {
                solucaoAtual[indice] = true;
                pesoAtual += itens[indice].peso;
                valorAtual += itens[indice].valor;
            }
        }

        // Limpar listas tabu para nova fase de pesquisa
        Arrays.fill(tabuFlip, 0);
        Arrays.fill(tabuSwapIn, 0);
        Arrays.fill(tabuSwapOut, 0);
    }
}
