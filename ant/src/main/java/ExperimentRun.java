public class ExperimentRun {
    private final String alias;
    private final String descriptor;
    private final MmasParameters parameters;

    public ExperimentRun(String alias, String descriptor, MmasParameters parameters) {
        this.alias = alias;
        this.descriptor = descriptor;
        this.parameters = parameters;
    }

    public String getAlias() {
        return alias;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public MmasParameters getParameters() {
        return parameters;
    }
}
