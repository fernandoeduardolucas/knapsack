# Relatório: Algoritmo Genético - Problema da Mochila 0/1

## 1. Contexto e Abordagem
Neste trabalho, implementou-se um **Algoritmo Genético (AG)** para resolver o Problema da Mochila 0/1. A estrutura foca-se na otimização da escolha de itens, sujeita a uma restrição de capacidade total.

**Características do AG:**
- **Representação:** Binária (1 se o item está na mochila, 0 caso contrário)
- **População e Evolução:** Estratégia geracional estrita com substituição total
- **Seleção:** Torneio (garante pressão seletiva ajustável)
- **Cruzamento (Crossover):** 2 pontos (maior preservação de blocos genéticos face a 1 ponto)
- **Mutação:** Bit-flip (exploração do espaço de procura)
- **Reparação:** Heurística Greedy (garante viabilidade retirando os itens de menor rácio valor/peso e adicionando os melhores)
- **Elitismo:** Preservação dos melhores indivíduos para evitar perda da melhor solução

## 2. Resultados Consolidados (Melhores Configurações)

A tabela abaixo resume as melhores soluções encontradas para cada instância, após análise da grelha de testes.

| Instância | Ótimo | Melhor Valor (AG) | GAP (%) | Tempo (s) | Melhor Configuração (AG) |
|-----------|-------|-------------------|---------|-----------|--------------------------|
| n_1000_1 | 9999946233 | 9999827097 | 0.001191% | 0.4364 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_2 | 9999964987 | 9999857382 | 0.001076% | 0.4282 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_3 | 9999229281 | 9999228930 | 0.000004% | 0.3967 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_4 | 9999239905 | 9999239620 | 0.000003% | 0.4089 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_5 | 9999251796 | 9999251390 | 0.000004% | 0.4070 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_6 | 9996100344 | 9996100334 | 0.000000% | 0.2480 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_7 | 9996105266 | 9996105192 | 0.000001% | 0.1779 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_8 | 9996111502 | 9996111467 | 0.000000% | 0.3161 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_9 | 9980488131 | 9980486289 | 0.000018% | 0.3943 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |
| n_1000_10 | 9980507700 | 9980504089 | 0.000036% | 0.1680 | pop=50 gen=500 cRate=0.85 mRate=0.005 elite=3 tourn=3 seed=42 |

## 3. Análise e Discussão

- **Desvio Médio Geral (GAP):** 0.0002%
- **Ótimos Alcançados (ou quase ótimos):** 8 de 10 instâncias analisadas.

### Impacto dos Parâmetros
- **Tamanho da População & Gerações:** Populações maiores aumentam a diversidade inicial e previnem a convergência prematura, mas têm um custo computacional linearmente superior.
- **Pressão de Seleção (Torneio vs Elitismo):** Torneios maiores forçam a convergência rápida. O elitismo atuou como uma rede de segurança vital, impedindo que mutações destrutivas afetassem a melhor solução já encontrada.
- **Reparação Greedy:** A reparação não só garante a viabilidade das soluções, como injeta inteligência heurística no processo evolutivo, acelerando drasticamente a aproximação aos valores ótimos.

### Conclusões Relevantes
O Algoritmo Genético mostrou ser altamente competitivo, especialmente quando a fase de exploração (crossover e mutação) é equilibrada por uma heurística de reparação local eficiente. Como trabalho futuro, seria interessante incorporar parâmetros auto-adaptáveis ou testar operadores de cruzamento uniforme para avaliar o impacto na quebra de simetria nas instâncias mais densas.
