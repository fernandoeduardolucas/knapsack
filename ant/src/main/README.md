# MMAS para o Problema da Mochila 0/1 em Java

Projeto Java para resolver instâncias do **Knapsack Problem (KP)** com **MAX–MIN Ant System (MMAS)**.

## O que esta versão implementa

Esta versão foi ajustada para ficar alinhada com a formulação teórica pedida:

- cada formiga percorre todos os itens;
- para cada item `i`, decide **incluir/excluir**;
- a decisão usa a probabilidade:

```text
P(x_i = 1) = (tau_i^alpha * eta_i^beta) /
             (tau_i^alpha * eta_i^beta + (1 - tau_i)^alpha)
```

onde:

- `tau_i` = feromona do item `i`
- `eta_i = valor/peso`
- `alpha` = peso da feromona
- `beta` = peso da heurística

Além disso:

- apenas a melhor formiga reforça feromonas;
- as feromonas são limitadas a `[tauMin, tauMax]`;
- inicialização com `tauMax`;
- reinicialização após estagnação;
- `tauMax = 1 / (rho * zEstimate)`;
- `tauMin = tauMax / (2n)`.

## Estrutura dos ficheiros

- `src/` — código fonte Java
- `example.properties` — exemplo de configuração
- `build_and_run.sh` — script simples de compilação e execução

## Formato esperado das instâncias

```text
1000
0 valor peso
1 valor peso
...
999 valor peso
10000000000
```

Ou seja:

- primeira linha: número de itens
- linhas seguintes: `id valor peso`
- última linha: capacidade da mochila

## Metadados no `.properties`

Exemplo:

```properties
n_1000_1=n_1000_c_10000000000_g_10_f_0.1_eps_0.0001_s_100
```

Legenda:

- `n` = número de itens
- `c` = capacidade da mochila
- `g` = parâmetro `g` do gerador
- `f` = parâmetro `f` do gerador
- `eps` = parâmetro `epsilon` do gerador
- `s` = seed usada na geração

## Compilar

```bash
javac -d out src/*.java
```

## Executar

```bash
java -cp out Main example.properties
```

## Notas de implementação

As feromonas do MMAS são mantidas no intervalo `[tauMin, tauMax]`.
Como a fórmula de decisão binária usa explicitamente `(1 - tau_i)`, o código normaliza internamente a feromona para `[0,1]` antes de calcular a probabilidade de inclusão.

Isso torna a implementação consistente com:

- os limites clássicos do MMAS;
- a regra binária de decisão que tu querias usar.
