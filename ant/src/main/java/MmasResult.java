public class MmasResult {
    private final KnapsackSolution bestSolution;
    private final long executionTimeMs;
    private final int bestIteration;

    public MmasResult(KnapsackSolution bestSolution, long executionTimeMs, int bestIteration) {
        this.bestSolution = bestSolution;
        this.executionTimeMs = executionTimeMs;
        this.bestIteration = bestIteration;
    }

    public KnapsackSolution getBestSolution() {
        return bestSolution;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public int getBestIteration() {
        return bestIteration;
    }
}
