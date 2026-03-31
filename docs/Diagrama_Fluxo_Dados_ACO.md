# Diagrama de Fluxo de Dados (DFD) — Algoritmo ACO para Mochila 0/1

Este diagrama resume o fluxo de dados principal do `AcoCore`.

```mermaid
flowchart TD
    A[Entradas:<br/>itens, capacidade,<br/>numFormigas, iteracoes,<br/>alpha, beta, rho, q, seed] --> B[inicializarEstruturas()<br/>gera eta e tau inicial]
    B --> C[construirSolucaoGulosaInicial()<br/>gera melhorGlobal inicial]
    C --> D[atualizarLimitesFeromonio(melhorGlobal)<br/>define tauMin/tauMax]

    D --> E{{Loop de iterações}}
    E --> F{{Loop de formigas}}
    F --> G[construirSolucaoProbabilistica()<br/>usa tau, eta, alpha, beta]
    G --> H[melhorarComBuscaLocal1Flip()]
    H --> I[melhorIteracao]
    I --> F

    F --> J{melhorIteracao > melhorGlobal?}
    J -- Sim --> K[melhorGlobal = melhorIteracao<br/>semMelhoria = 0<br/>atualiza tauMin/tauMax]
    J -- Não --> L[semMelhoria++]

    K --> M[evaporarFeromonio()<br/>tau = tau*(1-rho)]
    L --> M
    M --> N[depositarFeromonio(melhorGlobal)<br/>tau[i]+=q/valor]
    N --> O[limitarFeromonio()<br/>clamp em tauMin/tauMax]
    O --> P{semMelhoria >= limite?}
    P -- Sim --> Q[reiniciarFeromonio()<br/>tau = tauMax]
    P -- Não --> E
    Q --> E

    E --> R[Saída: melhorGlobal<br/>escolhidos, valorTotal, pesoTotal]
```

## Dicionário rápido de dados
- `tau[i]`: nível de feromônio por item (memória coletiva).
- `eta[i]`: heurística local por item (valor/peso normalizado).
- `melhorIteracao`: melhor solução encontrada na iteração atual.
- `melhorGlobal`: melhor solução encontrada em toda a execução.
- `semMelhoria`: contador de estagnação para reinicialização de feromônio.
