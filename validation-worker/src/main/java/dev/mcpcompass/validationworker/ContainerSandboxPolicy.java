package dev.mcpcompass.validationworker;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

record ContainerSandboxPolicy(
        String user,
        String cpuLimit,
        int memoryLimitMegabytes,
        int processLimit,
        Duration wallTimeLimit,
        String network
) {
    private static final Pattern NUMERIC_USER = Pattern.compile("[1-9][0-9]{0,9}:[1-9][0-9]{0,9}");
    private static final Pattern NETWORK_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");

    ContainerSandboxPolicy {
        if (user == null || !NUMERIC_USER.matcher(user).matches()) {
            throw new IllegalArgumentException("Container user must be a non-zero numeric uid:gid");
        }
        Objects.requireNonNull(cpuLimit, "cpuLimit");
        BigDecimal cpus;
        try {
            cpus = new BigDecimal(cpuLimit);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("CPU limit must be a decimal number", error);
        }
        if (cpus.compareTo(new BigDecimal("0.1")) < 0 || cpus.compareTo(new BigDecimal("8")) > 0) {
            throw new IllegalArgumentException("CPU limit must be between 0.1 and 8");
        }
        cpuLimit = cpus.stripTrailingZeros().toPlainString();
        if (memoryLimitMegabytes < 64 || memoryLimitMegabytes > 4096) {
            throw new IllegalArgumentException("Memory limit must be between 64 and 4096 MiB");
        }
        if (processLimit < 16 || processLimit > 1024) {
            throw new IllegalArgumentException("Process limit must be between 16 and 1024");
        }
        if (wallTimeLimit == null || wallTimeLimit.isNegative() || wallTimeLimit.isZero()
                || wallTimeLimit.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("Wall-time limit must be between one millisecond and fifteen minutes");
        }
        if (network == null || !NETWORK_NAME.matcher(network).matches()) {
            throw new IllegalArgumentException("Network must be 'none' or a bounded Docker network name");
        }
        if ("host".equals(network) || "bridge".equals(network) || "default".equals(network)) {
            throw new IllegalArgumentException("Built-in shared Docker networks cannot be used for validation");
        }
    }

    String memoryLimit() {
        return memoryLimitMegabytes + "m";
    }
}
