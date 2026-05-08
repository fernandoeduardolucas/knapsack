"""
=============================================================================
Algoritmo Genético para o Problema da Mochila 0/1 (0/1 Knapsack Problem)
=============================================================================
Trabalho Prático - UC de Simulação e Otimização
Mestrado em Engenharia Informática

Características implementadas:
  - Representação binária da solução (cromossoma de bits)
  - Algoritmo geracional (substitui toda a população a cada geração)
  - Seleção por torneio
  - Cruzamento de 2 pontos (two-point crossover)
  - Mutação bit-flip
  - Reparação greedy de soluções inviáveis
  - Elitismo (preserva os k melhores indivíduos)
  - Critério de paragem por número de gerações ou estagnação
=============================================================================
"""

import os
import re
import csv
import time
import random
from dataclasses import dataclass, field
from typing import List, Optional, Tuple


# =============================================================================
# SECÇÃO 1 — ESTRUTURAS DE DADOS
# =============================================================================

@dataclass
class Item:
    """Representa um item da mochila com índice, valor e peso."""
    indice: int
    valor: int
    peso: int


@dataclass
class InstanciaMochila:
    """
    Contém todos os dados de uma instância do Problema da Mochila.

    Atributos:
        nome      : nome do ficheiro da instância
        n         : número de itens
        capacidade: capacidade máxima da mochila (em unidades de peso)
        valores   : lista de valores de cada item
        pesos     : lista de pesos de cada item
        ordem_greedy: índices dos itens ordenados pelo rácio valor/peso
                      (decrescente) — usada pela reparação greedy
    """
    nome: str
    n: int
    capacidade: int
    valores: List[int]
    pesos: List[int]
    ordem_greedy: List[int] = field(default_factory=list)

    def __post_init__(self):
        # Pré-calcular a ordenação greedy (maior rácio valor/peso primeiro).
        # Itens com peso 0 são tratados como infinitamente bons (rácio = inf).
        self.ordem_greedy = sorted(
            range(self.n),
            key=lambda i: (
                self.valores[i] / self.pesos[i] if self.pesos[i] > 0 else float('inf')
            ),
            reverse=True
        )


@dataclass
class Cromossoma:
    """
    Representa uma solução candidata (indivíduo da população).

    Atributos:
        genes : lista binária de tamanho n (1 = item incluído, 0 = excluído)
        valor : valor total dos itens seleccionados
        peso  : peso total dos itens seleccionados
    """
    genes: List[int]
    valor: int
    peso: int


# =============================================================================
# SECÇÃO 2 — LEITURA DE INSTÂNCIAS
# =============================================================================

def ler_instancia(caminho: str) -> InstanciaMochila:
    """
    Lê uma instância do Problema da Mochila a partir de um ficheiro de texto.

    Formato esperado do ficheiro:
        Linha 1        : número de itens (n)
        Linhas 2 a n+1 : 'indice valor peso' (um item por linha)
        Última linha   : capacidade da mochila

    Parâmetros:
        caminho : caminho completo para o ficheiro da instância

    Retorna:
        Um objecto InstanciaMochila com os dados lidos.
    """
    with open(caminho, "r", encoding="utf-8") as f:
        linhas = [l.strip() for l in f if l.strip()]

    n = int(linhas[0])
    capacidade = int(linhas[1 + n])

    valores, pesos = [], []
    for linha in linhas[1: 1 + n]:
        partes = linha.split()
        if len(partes) != 3:
            raise ValueError(
                f"Linha inválida em '{caminho}': '{linha}'. "
                "Esperado: 'indice valor peso'."
            )
        _, valor, peso = partes
        valores.append(int(valor))
        pesos.append(int(peso))

    return InstanciaMochila(
        nome=os.path.basename(caminho),
        n=n,
        capacidade=capacidade,
        valores=valores,
        pesos=pesos,
    )


# =============================================================================
# SECÇÃO 3 — REPARAÇÃO GREEDY
# =============================================================================

def reparar_solucao(genes: List[int], inst: InstanciaMochila) -> Cromossoma:
    """
    Torna uma solução viável (reparação greedy) e tenta melhorá-la.

    Estratégia em dois passos:
      1. REMOÇÃO: se o peso excede a capacidade, remove itens pela ordem
         greedy inversa (piores rácios valor/peso primeiro) até a solução
         ser viável.
      2. ADIÇÃO: tenta adicionar itens ainda não seleccionados, pela ordem
         greedy (melhores rácios primeiro), enquanto couberem na mochila.

    Este processo garante:
      - Viabilidade: peso_total <= capacidade
      - Melhoria local: aproveita ao máximo a capacidade restante

    Parâmetros:
        genes : lista binária (pode ser inviável)
        inst  : instância do problema

    Retorna:
        Um Cromossoma válido e melhorado localmente.
    """
    sol = genes[:]

    # Calcular peso e valor actuais
    peso_actual = sum(inst.pesos[i] for i in range(inst.n) if sol[i] == 1)
    valor_actual = sum(inst.valores[i] for i in range(inst.n) if sol[i] == 1)

    # Passo 1: Remover itens até respeitar a capacidade.
    # Percorre a ordem greedy ao contrário (piores itens primeiro).
    if peso_actual > inst.capacidade:
        for i in reversed(inst.ordem_greedy):
            if sol[i] == 1:
                sol[i] = 0
                peso_actual -= inst.pesos[i]
                valor_actual -= inst.valores[i]
                if peso_actual <= inst.capacidade:
                    break

    # Passo 2: Adicionar itens que ainda cabem na mochila.
    # Percorre os itens pelos melhores rácios primeiro.
    for i in inst.ordem_greedy:
        if sol[i] == 0 and peso_actual + inst.pesos[i] <= inst.capacidade:
            sol[i] = 1
            peso_actual += inst.pesos[i]
            valor_actual += inst.valores[i]

    return Cromossoma(genes=sol, valor=valor_actual, peso=peso_actual)


# =============================================================================
# SECÇÃO 4 — INICIALIZAÇÃO DA POPULAÇÃO
# =============================================================================

def criar_solucao_aleatoria(inst: InstanciaMochila) -> Cromossoma:
    """
    Cria um cromossoma aleatório e repara-o para ser viável.

    Gera um vector binário aleatório e aplica a reparação greedy,
    garantindo que a solução inicial é sempre válida.

    Parâmetros:
        inst : instância do problema

    Retorna:
        Um Cromossoma válido (peso <= capacidade).
    """
    genes = [random.randint(0, 1) for _ in range(inst.n)]
    return reparar_solucao(genes, inst)


def inicializar_populacao(
    inst: InstanciaMochila,
    tamanho_pop: int
) -> List[Cromossoma]:
    """
    Gera a população inicial com indivíduos aleatórios viáveis.

    Parâmetros:
        inst        : instância do problema
        tamanho_pop : número de indivíduos na população

    Retorna:
        Lista de Cromossomas (todos viáveis).
    """
    return [criar_solucao_aleatoria(inst) for _ in range(tamanho_pop)]


# =============================================================================
# SECÇÃO 5 — OPERADORES GENÉTICOS
# =============================================================================

def selecao_torneio(
    populacao: List[Cromossoma],
    tamanho_torneio: int
) -> Cromossoma:
    """
    Selecciona um indivíduo por torneio.

    Escolhe aleatoriamente 'tamanho_torneio' indivíduos da população
    e devolve o melhor (maior valor). Quanto maior o torneio, maior
    a pressão selectiva (tende a escolher sempre os melhores).

    Parâmetros:
        populacao       : lista de cromossomas actuais
        tamanho_torneio : número de candidatos no torneio (tipicamente 2 a 5)

    Retorna:
        O melhor cromossoma entre os candidatos sorteados.
    """
    candidatos = random.sample(populacao, tamanho_torneio)
    return max(candidatos, key=lambda c: c.valor)


def cruzamento_dois_pontos(
    pai1: Cromossoma,
    pai2: Cromossoma
) -> Tuple[List[int], List[int]]:
    """
    Cruzamento de 2 pontos (two-point crossover).

    Gera dois pontos de corte aleatórios p1 e p2 (p1 < p2) e troca
    o segmento central entre os dois pais:
        filho1 = pai1[:p1] + pai2[p1:p2] + pai1[p2:]
        filho2 = pai2[:p1] + pai1[p1:p2] + pai2[p2:]

    Este operador preserva blocos de genes contíguos de ambos os pais,
    promovendo diversidade sem destruir completamente a estrutura existente.

    Parâmetros:
        pai1, pai2 : cromossomas progenitores

    Retorna:
        Tuplo com os genes (listas binárias) dos dois filhos.
    """
    n = len(pai1.genes)
    p1, p2 = sorted(random.sample(range(1, n), 2))

    filho1 = pai1.genes[:p1] + pai2.genes[p1:p2] + pai1.genes[p2:]
    filho2 = pai2.genes[:p1] + pai1.genes[p1:p2] + pai2.genes[p2:]

    return filho1, filho2


def mutacao_bit_flip(genes: List[int], taxa_mutacao: float) -> List[int]:
    """
    Mutação bit-flip: inverte cada bit com probabilidade taxa_mutacao.

    Para cada gene do cromossoma, gera um número aleatório entre 0 e 1.
    Se for inferior à taxa de mutação, o bit é invertido (0→1 ou 1→0).

    Uma taxa baixa (0.001 a 0.01) mantém diversidade sem destruir boas
    soluções. Para n=1000, uma taxa de 0.005 inverte em média 5 bits.

    Parâmetros:
        genes         : lista binária original
        taxa_mutacao  : probabilidade de inversão de cada bit

    Retorna:
        Nova lista binária com possíveis mutações.
    """
    mutado = genes[:]
    for i in range(len(mutado)):
        if random.random() < taxa_mutacao:
            mutado[i] = 1 - mutado[i]
    return mutado


# =============================================================================
# SECÇÃO 6 — ALGORITMO GENÉTICO GERACIONAL COM ELITISMO
# =============================================================================

def algoritmo_genetico(
    inst: InstanciaMochila,
    tamanho_pop: int = 100,
    num_geracoes: int = 2000,
    taxa_cruzamento: float = 0.85,
    taxa_mutacao: float = 0.005,
    tamanho_elite: int = 3,
    tamanho_torneio: int = 3,
    max_sem_melhoria: int = 300,
    seed: int = 42,
    verbose: bool = False
) -> Tuple[List[int], int, int, int]:
    """
    Executa o Algoritmo Genético para o Problema da Mochila 0/1.

    Fluxo do algoritmo geracional:
      1. Inicialização: gerar população aleatória de tamanho_pop indivíduos
      2. Para cada geração:
         a) Elitismo: copiar os tamanho_elite melhores para a nova população
         b) Repetir até encher a nova população:
            - Seleccionar 2 pais por torneio
            - Aplicar cruzamento de 2 pontos (com prob. taxa_cruzamento)
            - Aplicar mutação bit-flip a cada filho
            - Reparar cada filho (garante viabilidade)
            - Adicionar filhos à nova população
         c) Substituir a população antiga pela nova (algoritmo geracional)
         d) Actualizar o melhor indivíduo encontrado
      3. Parar quando atingir num_geracoes ou max_sem_melhoria gerações
         consecutivas sem melhoria do melhor valor

    Parâmetros:
        inst             : instância do problema
        tamanho_pop      : μ — número de indivíduos na população (50 a 200)
        num_geracoes     : número máximo de gerações (1000 a 5000)
        taxa_cruzamento  : pC — probabilidade de cruzamento (0.6 a 0.9)
        taxa_mutacao     : pM — probabilidade de mutação por bit (0.001 a 0.01)
        tamanho_elite    : k — número de elites preservados por geração (1 a 5%
                           de tamanho_pop)
        tamanho_torneio  : tamanho do torneio de selecção (2 a 5)
        max_sem_melhoria : gerações consecutivas sem melhoria para parar
        seed             : semente do gerador aleatório (reproducibilidade)
        verbose          : se True, imprime progresso a cada 100 gerações

    Retorna:
        Tuplo (genes, valor, peso, geracao_fim, valor_inicial):
          - genes          : lista binária da melhor solução encontrada
          - valor          : valor total da melhor solução
          - peso           : peso total da melhor solução
          - geracao_fim    : geração em que o algoritmo terminou
          - valor_inicial  : melhor valor da população inicial (Solução Inicial)
    """
    random.seed(seed)

    # --- Inicialização ---
    populacao = inicializar_populacao(inst, tamanho_pop)
    melhor = max(populacao, key=lambda c: c.valor)
    valor_inicial = melhor.valor  # Melhor valor antes de qualquer evolução

    geracoes_sem_melhoria = 0
    geracao_fim = 0

    for geracao in range(1, num_geracoes + 1):
        geracao_fim = geracao

        # Ordenar a população por valor decrescente (melhor primeiro)
        populacao_ordenada = sorted(populacao, key=lambda c: c.valor, reverse=True)

        # --- Elitismo: copiar os melhores directamente para a próxima geração ---
        nova_populacao: List[Cromossoma] = populacao_ordenada[:tamanho_elite]

        # --- Preencher o resto da nova população com filhos ---
        while len(nova_populacao) < tamanho_pop:
            # Selecção por torneio
            pai1 = selecao_torneio(populacao, tamanho_torneio)
            pai2 = selecao_torneio(populacao, tamanho_torneio)

            # Cruzamento de 2 pontos (com probabilidade taxa_cruzamento)
            if random.random() < taxa_cruzamento:
                genes_filho1, genes_filho2 = cruzamento_dois_pontos(pai1, pai2)
            else:
                # Sem cruzamento: filhos são cópias dos pais
                genes_filho1, genes_filho2 = pai1.genes[:], pai2.genes[:]

            # Mutação bit-flip
            genes_filho1 = mutacao_bit_flip(genes_filho1, taxa_mutacao)
            genes_filho2 = mutacao_bit_flip(genes_filho2, taxa_mutacao)

            # Reparação greedy (garante viabilidade)
            filho1 = reparar_solucao(genes_filho1, inst)
            filho2 = reparar_solucao(genes_filho2, inst)

            nova_populacao.append(filho1)
            if len(nova_populacao) < tamanho_pop:
                nova_populacao.append(filho2)

        # Substituição geracional completa
        populacao = nova_populacao

        # Actualizar o melhor indivíduo global
        melhor_geracao = max(populacao, key=lambda c: c.valor)
        if melhor_geracao.valor > melhor.valor:
            melhor = melhor_geracao
            geracoes_sem_melhoria = 0
        else:
            geracoes_sem_melhoria += 1

        # Critério de paragem por estagnação
        if geracoes_sem_melhoria >= max_sem_melhoria:
            if verbose:
                print(
                    f"    [Paragem antecipada] Geração {geracao} — "
                    f"sem melhoria há {max_sem_melhoria} gerações."
                )
            break

        # Progresso periódico (opcional)
        if verbose and geracao % 100 == 0:
            print(
                f"    Geração {geracao:4d} | Melhor valor: {melhor.valor:,} "
                f"| Sem melhoria: {geracoes_sem_melhoria}"
            )

    return melhor.genes, melhor.valor, melhor.peso, geracao_fim, valor_inicial


# =============================================================================
# SECÇÃO 7 — ÓTIMOS DA PROFESSORA E FUNÇÕES DE COMPARAÇÃO
# =============================================================================

# Mapeamento: nome da instância (ficheiro) → valor ótimo conhecido
# Os 10 ótimos foram fornecidos pela professora por ordem crescente do
# índice da instância (n_1000_1 a n_1000_10).
VALORES_OTIMOS = {
    "n_1000_1":  9999946233,
    "n_1000_2":  9999964987,
    "n_1000_3":  9999229281,
    "n_1000_4":  9999239905,
    "n_1000_5":  9999251796,
    "n_1000_6":  9996100344,
    "n_1000_7":  9996105266,
    "n_1000_8":  9996111502,
    "n_1000_9":  9980488131,
    "n_1000_10": 9980507700,
}


def calcular_gap(valor_obtido: int, valor_otimo: int) -> float:
    """
    Calcula o gap percentual em relação ao ótimo.

    Gap = ((ótimo - obtido) / ótimo) × 100

    Um gap positivo significa que ficámos abaixo do ótimo.
    Um gap negativo (ou zero) significa que atingimos ou superámos o ótimo.

    Parâmetros:
        valor_obtido : valor da melhor solução encontrada pelo AG
        valor_otimo  : valor ótimo conhecido

    Retorna:
        Gap em percentagem (float).
    """
    return ((valor_otimo - valor_obtido) / valor_otimo) * 100.0


def comparar_com_otimo(valor_obtido: int, valor_otimo: int) -> str:
    """
    Compara o valor obtido com o ótimo e devolve uma etiqueta textual.

    Retorna:
        'ABAIXO' se valor_obtido < valor_otimo
        'IGUAL'  se valor_obtido == valor_otimo
        'ACIMA'  se valor_obtido > valor_otimo (improvável com ótimos corretos)
    """
    if valor_obtido < valor_otimo:
        return "ABAIXO"
    elif valor_obtido == valor_otimo:
        return "IGUAL"
    else:
        return "ACIMA"


# =============================================================================
# SECÇÃO 8 — EXECUÇÃO EM LOTE E EXPORTAÇÃO
# =============================================================================

def chave_ordenacao_natural(s: str) -> list:
    """
    Chave para ordenação natural de ficheiros (ex.: n_1000_2 antes de n_1000_10).
    Separa partes numéricas e de texto para ordenação correcta.
    """
    return [
        int(parte) if parte.isdigit() else parte.lower()
        for parte in re.split(r'(\d+)', s)
    ]


def executar_todas_instancias(
    pasta: str,
    ficheiro_csv: str = "ag_resultados.csv",
    # --- Parâmetros do AG ---
    tamanho_pop: int = 100,
    num_geracoes: int = 2000,
    taxa_cruzamento: float = 0.85,
    taxa_mutacao: float = 0.005,
    tamanho_elite: int = 3,
    tamanho_torneio: int = 3,
    max_sem_melhoria: int = 300,
    seed: int = 42,
    verbose: bool = False,
) -> None:
    """
    Processa todas as instâncias na pasta indicada e guarda os resultados.

    Colunas da tabela de resultados (conforme pedido na entrega):
      - Instância          : nome do problema teste
      - Solução Ótima (SO) : valor ótimo conhecido
      - Solução Inicial    : melhor valor da população inicial (método construtivo)
      - Solução Encontrada : melhor valor encontrado pelo AG
      - % Desvio           : (SO - SE) / SO  (negativo se SE > SO)
      - Tempo (s)          : tempo de execução

    Parâmetros:
        pasta            : caminho para a pasta com os ficheiros de instâncias
        ficheiro_csv     : nome do ficheiro CSV de saída
        (restantes)      : parâmetros passados directamente ao algoritmo_genetico()
    """
    if not os.path.isdir(pasta):
        raise FileNotFoundError(
            f"A pasta de instâncias '{pasta}' não existe.\n"
            "Crie a pasta 'instancias' e coloque lá os ficheiros das instâncias."
        )

    # Encontrar e ordenar ficheiros de instâncias
    ficheiros = [
        os.path.join(pasta, f)
        for f in os.listdir(pasta)
        if os.path.isfile(os.path.join(pasta, f))
    ]
    ficheiros.sort(key=lambda p: chave_ordenacao_natural(os.path.basename(p)))

    if not ficheiros:
        print("Não foram encontrados ficheiros de instâncias na pasta.")
        return

    # Imprimir cabeçalho da tabela
    sep = "=" * 115
    print(sep)
    print(
        f"  Parâmetros do AG: pop={tamanho_pop}, gerações={num_geracoes}, "
        f"pC={taxa_cruzamento}, pM={taxa_mutacao}, elite={tamanho_elite}, "
        f"torneio={tamanho_torneio}, estagnação={max_sem_melhoria}, seed={seed}"
    )
    print(sep)
    print(
        f"{'Instância':<12} "
        f"{'Sol. Ótima (SO)':>18} "
        f"{'Sol. Inicial':>15} "
        f"{'Sol. Encontrada (SE)':>22} "
        f"{'% Desvio':>10} "
        f"{'Ger.':>6} "
        f"{'Tempo (s)':>10}"
    )
    print(sep)

    resultados = []

    for caminho in ficheiros:
        inst = ler_instancia(caminho)
        print(f"  A processar: {inst.nome}...")

        inicio = time.perf_counter()
        genes, valor, peso, geracao_fim, valor_inicial = algoritmo_genetico(
            inst,
            tamanho_pop=tamanho_pop,
            num_geracoes=num_geracoes,
            taxa_cruzamento=taxa_cruzamento,
            taxa_mutacao=taxa_mutacao,
            tamanho_elite=tamanho_elite,
            tamanho_torneio=tamanho_torneio,
            max_sem_melhoria=max_sem_melhoria,
            seed=seed,
            verbose=verbose,
        )
        tempo = time.perf_counter() - inicio

        # Comparar com o ótimo da professora
        otimo = VALORES_OTIMOS.get(inst.nome)
        if otimo is not None:
            # Fórmula pedida: (SO - SE) / SO  (positivo = abaixo do ótimo)
            desvio = (otimo - valor) / otimo
        else:
            desvio = None

        linha = {
            "instancia":           inst.nome,
            "solucao_otima":       otimo,
            "solucao_inicial":     valor_inicial,
            "solucao_encontrada":  valor,
            "desvio_percentual":   round(desvio * 100, 6) if desvio is not None else None,
            "geracao_paragem":     geracao_fim,
            "tempo_execucao_s":    round(tempo, 4),
        }
        resultados.append(linha)

        desvio_str = f"{desvio * 100:.6f}%" if desvio is not None else "N/A"
        otimo_str  = f"{otimo:,}" if otimo else "N/A"
        print(
            f"  {inst.nome:<12} "
            f"  {otimo_str:>18} "
            f"  {valor_inicial:>15,} "
            f"  {valor:>22,} "
            f"  {desvio_str:>10} "
            f"  {geracao_fim:>6} "
            f"  {tempo:>10.4f}"
        )

    print(sep)

    # Guardar resultados em CSV
    campos = [
        "instancia", "solucao_otima", "solucao_inicial",
        "solucao_encontrada", "desvio_percentual",
        "geracao_paragem", "tempo_execucao_s",
    ]
    with open(ficheiro_csv, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=campos)
        writer.writeheader()
        writer.writerows(resultados)

    print(f"\n  Resultados guardados em: {ficheiro_csv}\n")


# =============================================================================
# SECÇÃO 9 — PONTO DE ENTRADA
# =============================================================================

if __name__ == "__main__":
    """
    Configuração de execução.

    PARÂMETROS — GUIA DE ESCOLHA:
    ─────────────────────────────────────────────────────────────────────────
    Parâmetro         Teste rápido    Execução final    Recomendado
    ─────────────────────────────────────────────────────────────────────────
    tamanho_pop       50              150–200           100
    num_geracoes      500             3000–5000         2000
    taxa_cruzamento   0.80            0.85–0.90         0.85
    taxa_mutacao      0.01            0.003–0.005       0.005
    tamanho_elite     2               3–5               3
    tamanho_torneio   3               3–5               3
    max_sem_melhoria  100             300–500           300
    ─────────────────────────────────────────────────────────────────────────
    Nota: maior tamanho_pop e num_geracoes → melhor qualidade, mas mais lento.
          Para n=1000 itens, cada geração leva ~0.02s; 2000 gerações ≈ 40s/inst.
    """

    base = os.path.dirname(os.path.abspath(__file__))
    pasta_instancias = os.path.join(base, "instancias")

    executar_todas_instancias(
        pasta=pasta_instancias,
        ficheiro_csv="ag_resultados.csv",

        # --- Parâmetros do AG (ajuste conforme necessário) ---
        tamanho_pop=50,           # Tamanho da população (μ)
        num_geracoes=500,         # Número máximo de gerações
        taxa_cruzamento=0.85,     # Probabilidade de cruzamento (pC)
        taxa_mutacao=0.005,       # Probabilidade de mutação por bit (pM)
        tamanho_elite=3,          # Número de elites preservados (k_elite)
        tamanho_torneio=3,        # Tamanho do torneio de selecção (k_torneio)
        max_sem_melhoria=100,     # Gerações sem melhoria para parar
        seed=42,                  # Semente aleatória (reproducibilidade)
        verbose=False,            # True para ver progresso por geração
    )
