import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstanceMetadata {
    private static final Pattern PATTERN = Pattern.compile(
            "n_(\\d+)_c_(\\d+)_g_([^_]+)_f_([^_]+)_eps_([^_]+)_s_(\\d+)");

    private final String alias;
    private final String descriptor;
    private final Integer n;
    private final Long capacity;
    private final String g;
    private final String f;
    private final String epsilon;
    private final Long seed;

    public InstanceMetadata(String alias, String descriptor, Integer n, Long capacity,
                            String g, String f, String epsilon, Long seed) {
        this.alias = alias;
        this.descriptor = descriptor;
        this.n = n;
        this.capacity = capacity;
        this.g = g;
        this.f = f;
        this.epsilon = epsilon;
        this.seed = seed;
    }

    public static InstanceMetadata parse(String alias, String descriptor) {
        if (descriptor == null || descriptor.isBlank()) {
            return new InstanceMetadata(alias, descriptor, null, null, null, null, null, null);
        }

        Matcher matcher = PATTERN.matcher(descriptor.trim());
        if (!matcher.matches()) {
            return new InstanceMetadata(alias, descriptor, null, null, null, null, null, null);
        }

        return new InstanceMetadata(
                alias,
                descriptor,
                Integer.parseInt(matcher.group(1)),
                Long.parseLong(matcher.group(2)),
                matcher.group(3),
                matcher.group(4),
                matcher.group(5),
                Long.parseLong(matcher.group(6))
        );
    }

    public String getAlias() {
        return alias;
    }

    public String getDescriptor() {
        return descriptor;
    }

    public Integer getN() {
        return n;
    }

    public Long getCapacity() {
        return capacity;
    }

    public String getG() {
        return g;
    }

    public String getF() {
        return f;
    }

    public String getEpsilon() {
        return epsilon;
    }

    public Long getSeed() {
        return seed;
    }
}
