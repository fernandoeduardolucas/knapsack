package org.metaheuristicas.ant.knapsack.model;

public class Instancia {
    public final Item[] itens;
    public final long capacidade;

    public Instancia(Item[] itens, long capacidade) {
        this.itens = itens;
        this.capacidade = capacidade;
    }
}
