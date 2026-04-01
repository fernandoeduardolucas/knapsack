package org.metaheuristicas.ant.knapsack.model;

public class Item {
    public final long peso;
    public final long valor;

    public Item(long peso, long valor) {
        if (peso <= 0) throw new IllegalArgumentException("Peso deve ser > 0");
        if (valor < 0) throw new IllegalArgumentException("Valor não pode ser negativo");
        this.peso = peso;
        this.valor = valor;
    }
}
