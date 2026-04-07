package org.metaheuristicas.knapsack.mmas.io;

import org.metaheuristicas.knapsack.mmas.experiments.MmasResult;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CsvWriter implements Closeable {
    private final BufferedWriter writer;

    public CsvWriter(Path outputPath) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.writer = Files.newBufferedWriter(outputPath);
        writer.write("instance,ants,iterations,alpha,beta,rho,q,stall,seed,best_value,total_weight,elapsed_ms");
        writer.newLine();
    }

    public void append(MmasResult result) throws IOException {
        writer.write(result.csvLine());
        writer.newLine();
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
