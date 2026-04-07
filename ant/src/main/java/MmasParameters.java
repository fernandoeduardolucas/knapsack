public class MmasParameters {
    private final int ants;
    private final int iterations;
    private final double alpha;
    private final double beta;
    private final double rho;
    private final boolean useGlobalBest;
    private final boolean localSearch;
    private final int restartAfter;
    private final long seed;

    public MmasParameters(int ants, int iterations, double alpha, double beta, double rho,
                          boolean useGlobalBest, boolean localSearch, int restartAfter, long seed) {
        this.ants = ants;
        this.iterations = iterations;
        this.alpha = alpha;
        this.beta = beta;
        this.rho = rho;
        this.useGlobalBest = useGlobalBest;
        this.localSearch = localSearch;
        this.restartAfter = restartAfter;
        this.seed = seed;
    }

    public int getAnts() {
        return ants;
    }

    public int getIterations() {
        return iterations;
    }

    public double getAlpha() {
        return alpha;
    }

    public double getBeta() {
        return beta;
    }

    public double getRho() {
        return rho;
    }

    public boolean isUseGlobalBest() {
        return useGlobalBest;
    }

    public boolean isLocalSearch() {
        return localSearch;
    }

    public int getRestartAfter() {
        return restartAfter;
    }

    public long getSeed() {
        return seed;
    }

    @Override
    public String toString() {
        return "ants=" + ants
                + ", iterations=" + iterations
                + ", alpha=" + alpha
                + ", beta=" + beta
                + ", rho=" + rho
                + ", globalBest=" + useGlobalBest
                + ", localSearch=" + localSearch
                + ", restartAfter=" + restartAfter
                + ", seed=" + seed;
    }
}
