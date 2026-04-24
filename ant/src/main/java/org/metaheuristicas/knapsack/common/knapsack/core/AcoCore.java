package org.metaheuristicas.knapsack.common.knapsack.core;

import org.metaheuristicas.knapsack.common.knapsack.model.Item;
import org.metaheuristicas.knapsack.common.knapsack.model.Solucao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Núcleo do ACO/MMAS para mochila 0/1.
 *
 * <p>Mapeamento direto do documento {@code docs/03_Colonia_Formigas.docx}:
 * <ul>
 *   <li><b>Representação de feromonas</b>: vetor {@code tau[i]} (seção 2.1).</li>
 *   <li><b>Heurística local</b>: {@code eta[i] = valor/peso} normalizado (seção 2.2).</li>
 *   <li><b>Ciclo MMAS</b>: construção -> avaliação -> evaporação -> reforço -> limitação
 *   -> reinicialização por estagnação (seção 4.1).</li>
 *   <li><b>Busca local 1-flip</b> após construção de cada formiga (seção 4.2).</li>
 * </ul>
 *
 * <p>A ideia é manter o código legível "de ponta a ponta", com nomes de método
 * que representem cada etapa do pseudocódigo do documento.
 */
public class AcoCore {
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
    private double tauMin = 1e-6;
    private double tauMax = 1e6;

    public AcoCore(
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

    public Solucao resolver() {
        // MMAS: começa com uma solução gulosa para ter um bom ponto inicial x*.
        Solucao melhorGlobal = construirSolucaoGulosaInicial();
        atualizarLimitesFeromonio(melhorGlobal);
        // MMAS: inicialização uniforme no limite superior.
        reiniciarFeromonio();

        int semMelhoria = 0;

        for (int t = 0; t < iteracoes; t++) {
            Solucao melhorIteracao = null;

            for (int formiga = 0; formiga < numFormigas; formiga++) {
                // Cada formiga constrói solução factível guiada por tau e eta.
                Solucao candidata = construirSolucaoProbabilistica();
                // Integração com busca local 1-flip (ACO memético da seção 4.2).
                Solucao refinada = melhorarComBuscaLocal1Flip(candidata);

                if (melhorIteracao == null || refinada.valorTotal > melhorIteracao.valorTotal) {
                    melhorIteracao = refinada;
                }
            }

            if (melhorIteracao != null && melhorIteracao.valorTotal > melhorGlobal.valorTotal) {
                melhorGlobal = melhorIteracao;
                semMelhoria = 0;
                atualizarLimitesFeromonio(melhorGlobal);
            } else {
                semMelhoria++;
            }

            // Atualização de feromonas do MMAS:
            // 1) evaporação global
            // Diversificação: evaporação reduz reforços antigos e abre espaço para novas combinações.
            evaporarFeromonio();
            // 2) reforço só da melhor solução (estratégia best/global-best)
            // Intensificação: reforço elitista concentra a busca na melhor solução conhecida.
            depositarFeromonio(melhorGlobal);
            // 3) truncamento em [tauMin, tauMax] para evitar estagnação
            limitarFeromonio();

            if (semMelhoria >= limiteSemMelhoria) {
                // Reinicialização periódica quando há estagnação.
                reiniciarFeromonio();
                semMelhoria = 0;
            }
        }

        return melhorGlobal;
    }

    private void inicializarEstruturas() {
        tau = new double[itens.length];
        eta = new double[itens.length];

        // Seção 2.2: heurística do KP é valor/peso.
        double maiorRazao = 0.0;
        for (Item item : itens) {
            maiorRazao = Math.max(maiorRazao, (double) item.valor / item.peso);
        }

        if (maiorRazao == 0.0) {
            maiorRazao = 1.0;
        }

        for (int i = 0; i < itens.length; i++) {
            double razao = (double) itens[i].valor / itens[i].peso;
            // Normalização para manter valores em escala estável.
            eta[i] = razao / maiorRazao;
        }

        // Feromonas uniformes no início (estado sem preferência).
        Arrays.fill(tau, 1.0);
    }

    private Solucao construirSolucaoGulosaInicial() {
        Integer[] ordem = new Integer[itens.length];
        for (int i = 0; i < itens.length; i++) {
            ordem[i] = i;
        }

        Arrays.sort(ordem, (a, b) -> Double.compare((double) itens[b].valor / itens[b].peso, (double) itens[a].valor / itens[a].peso));

        boolean[] escolhidos = new boolean[itens.length];
        long pesoAtual = 0;
        long valorAtual = 0;

        for (int indice : ordem) {
            if (pesoAtual + itens[indice].peso <= capacidade) {
                escolhidos[indice] = true;
                pesoAtual += itens[indice].peso;
                valorAtual += itens[indice].valor;
            }
        }

        return new Solucao(escolhidos, valorAtual, pesoAtual);
    }

    private Solucao construirSolucaoProbabilistica() {
        boolean[] escolhidos = new boolean[itens.length];
        long pesoAtual = 0;
        long valorAtual = 0;

        List<Integer> ordemItens = new ArrayList<>(itens.length);
        for (int i = 0; i < itens.length; i++) {
            ordemItens.add(i);
        }
        // Diversificação: embaralhar a ordem de visita dos itens aumenta a exploração estocástica.
        java.util.Collections.shuffle(ordemItens, rng);

        for (int indice : ordemItens) {
            long capacidadeResidual = capacidade - pesoAtual;
            // Gestão de viabilidade (seção 2.3): se não cabe, exclui automaticamente.
            if (itens[indice].peso > capacidadeResidual) {
                continue;
            }

            // Regra binária de decisão (seção 2.2):
            // P(x_i = 1) = (tau_i^alpha * eta_i^beta) / (tau_i^alpha * eta_i^beta + (1-tau_i)^alpha)
            double tauNormalizado = normalizarTauParaProbabilidade(tau[indice]);
            double incluir = Math.pow(tauNormalizado, alpha) * Math.pow(eta[indice], beta);
            double excluir = Math.pow(Math.max(1e-12, 1.0 - tauNormalizado), alpha);
            double probIncluir = incluir / Math.max(1e-12, incluir + excluir);

            if (rng.nextDouble() < probIncluir) {
                escolhidos[indice] = true;
                pesoAtual += itens[indice].peso;
                valorAtual += itens[indice].valor;
            }
        }

        return new Solucao(escolhidos, valorAtual, pesoAtual);
    }

    private double normalizarTauParaProbabilidade(double tauValue) {
        double denominador = Math.max(1e-12, tauMax - tauMin);
        double normalizado = (tauValue - tauMin) / denominador;
        return Math.min(1.0 - 1e-12, Math.max(1e-12, normalizado));
    }

    private Solucao melhorarComBuscaLocal1Flip(Solucao base) {
        // Intensificação: busca local explora a vizinhança imediata da solução para refinamento.
        boolean[] escolhidos = Arrays.copyOf(base.escolhidos, base.escolhidos.length);
        long melhorValor = base.valorTotal;
        long melhorPeso = base.pesoTotal;
        boolean houveMelhoria;

        do {
            houveMelhoria = false;

            for (int i = 0; i < escolhidos.length; i++) {
                boolean novoEstado = !escolhidos[i];
                long novoPeso = melhorPeso + (novoEstado ? itens[i].peso : -itens[i].peso);
                long novoValor = melhorValor + (novoEstado ? itens[i].valor : -itens[i].valor);

                if (novoEstado && novoPeso > capacidade) {
                    continue;
                }

                if (novoValor > melhorValor) {
                    escolhidos[i] = novoEstado;
                    melhorPeso = novoPeso;
                    melhorValor = novoValor;
                    houveMelhoria = true;
                }
            }
        } while (houveMelhoria);

        return new Solucao(escolhidos, melhorValor, melhorPeso);
    }

    private void atualizarLimitesFeromonio(Solucao melhor) {
        // Seção MMAS: tauMax = 1/(rho*z*), tauMin = tauMax/(2n)
        double z = Math.max(1.0, melhor.valorTotal);
        tauMax = 1.0 / (rho * z);
        tauMin = tauMax / (2.0 * itens.length);

        if (tauMin <= 0.0 || !Double.isFinite(tauMin)) {
            tauMin = 1e-6;
        }

        if (tauMax <= tauMin || !Double.isFinite(tauMax)) {
            tauMax = tauMin * 1000.0;
        }

        // A regra binária de decisão usa (1 - tau_i), portanto mantém tau no intervalo (0,1).
        tauMax = Math.min(0.999999, tauMax);
        tauMin = Math.max(1e-6, Math.min(tauMin, tauMax / 2.0));
    }

    private void evaporarFeromonio() {
        double fator = Math.max(0.0, 1.0 - rho);
        for (int i = 0; i < tau.length; i++) {
            tau[i] *= fator;
        }
    }

    private void depositarFeromonio(Solucao melhor) {
        double deposito = 1.0 / Math.max(1.0, melhor.valorTotal);
        for (int i = 0; i < melhor.escolhidos.length; i++) {
            if (melhor.escolhidos[i]) {
                tau[i] += deposito;
            }
        }
    }

    private void limitarFeromonio() {
        for (int i = 0; i < tau.length; i++) {
            if (tau[i] < tauMin) {
                tau[i] = tauMin;
            }
            if (tau[i] > tauMax) {
                tau[i] = tauMax;
            }
        }
    }

    private void reiniciarFeromonio() {
        Arrays.fill(tau, tauMax);
    }
}
