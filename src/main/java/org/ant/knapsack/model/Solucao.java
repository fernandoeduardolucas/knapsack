package org.ant.knapsack.model;

public class Solucao {
    public final boolean[] escolhidos;
    public final long valorTotal;
    public final long pesoTotal;

    public Solucao(boolean[] escolhidos, long valorTotal, long pesoTotal) {
        this.escolhidos = escolhidos;
        this.valorTotal = valorTotal;
        this.pesoTotal = pesoTotal;
    }
}
