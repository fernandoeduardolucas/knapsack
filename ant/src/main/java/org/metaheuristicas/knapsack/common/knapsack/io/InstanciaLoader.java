package org.metaheuristicas.knapsack.common.knapsack.io;

import org.metaheuristicas.knapsack.common.knapsack.model.Instancia;
import org.metaheuristicas.knapsack.common.knapsack.model.Item;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class InstanciaLoader {

    private InstanciaLoader() {
    }

    public static Instancia carregar(Path path) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String primeira = br.readLine();
            if (primeira == null) throw new IllegalArgumentException("Instância vazia: " + path);

            int n = Integer.parseInt(primeira.trim());
            if (n <= 0) throw new IllegalArgumentException("Número de itens inválido em " + path + ": " + n);

            Item[] itens = new Item[n];
            for (int i = 0; i < n; i++) {
                String linha = br.readLine();
                if (linha == null) throw new IllegalArgumentException("Faltam linhas de itens em " + path);

                String[] partes = linha.trim().split("\\s+");
                if (partes.length < 3) throw new IllegalArgumentException("Linha de item inválida: " + linha);

                long lucro = Long.parseLong(partes[1]);
                long peso = Long.parseLong(partes[2]);
                itens[i] = new Item(peso, lucro);
            }

            String capacidadeLinha = br.readLine();
            if (capacidadeLinha == null) throw new IllegalArgumentException("Linha de capacidade ausente em " + path);

            long capacidade = Long.parseLong(capacidadeLinha.trim());
            return new Instancia(itens, capacidade);
        }
    }
}
