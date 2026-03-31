package org.ant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ACOKnapsackTests {

    @Test
    void deveEncontrarSolucaoValida() {
        ACOKnapsack.Item[] itens = new ACOKnapsack.Item[]{
                new ACOKnapsack.Item(2, 6),
                new ACOKnapsack.Item(2, 10),
                new ACOKnapsack.Item(3, 12),
                new ACOKnapsack.Item(1, 7)
        };

        ACOKnapsack solver = new ACOKnapsack(
                itens,
                5,
                20,
                200,
                1.0,
                3.0,
                0.2,
                1.0,
                50,
                12345L
        );

        ACOKnapsack.Solucao melhor = solver.resolver();

        assertNotNull(melhor);
        assertTrue(melhor.pesoTotal <= 5);
        assertTrue(melhor.valorTotal >= 19);
    }

    @Test
    void deveCarregarInstanciaDeArquivo() throws IOException {
        Path arquivo = Files.createTempFile("instancia", ".txt");
        Files.writeString(arquivo, String.join("\n",
                "3",
                "1 10 5",
                "2 7 3",
                "3 3 2",
                "5"
        ));

        ACOKnapsack.Instancia instancia = ACOKnapsack.carregarInstancia(arquivo);

        assertEquals(3, instancia.itens.length);
        assertEquals(5, instancia.capacidade);
        assertEquals(10, instancia.itens[0].valor);
        assertEquals(5, instancia.itens[0].peso);
    }

    @Test
    void deveCarregarInstanciaComValoresLongos() throws IOException {
        Path arquivo = Files.createTempFile("instancia-grande", ".txt");
        Files.writeString(arquivo, String.join("\n",
                "2",
                "1 5001000076 9000000000",
                "2 12 5",
                "10000000000"
        ));

        ACOKnapsack.Instancia instancia = ACOKnapsack.carregarInstancia(arquivo);

        assertEquals(2, instancia.itens.length);
        assertEquals(5001000076L, instancia.itens[0].valor);
        assertEquals(9000000000L, instancia.itens[0].peso);
        assertEquals(10000000000L, instancia.capacidade);
    }
}
