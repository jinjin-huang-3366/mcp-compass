package dev.mcpcompass.requirement;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record StructuredRequirement(
        String schemaVersion,
        String domain,
        String service,
        List<String> requiredCapabilities,
        List<String> forbiddenCapabilities,
        List<RequirementConstraint> constraints
) {
    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public StructuredRequirement {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        if (!CURRENT_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported structured requirement schema version: " + schemaVersion);
        }

        domain = normalizeOptionalText(domain, "domain");
        service = normalizeOptionalText(service, "service");
        requiredCapabilities = copyCapabilities(requiredCapabilities, "requiredCapabilities");
        forbiddenCapabilities = copyCapabilities(forbiddenCapabilities, "forbiddenCapabilities");
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints must not be null"));

        Set<String> conflictingCapabilities = new HashSet<>(requiredCapabilities);
        conflictingCapabilities.retainAll(forbiddenCapabilities);
        if (!conflictingCapabilities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Capabilities cannot be both required and forbidden: " + conflictingCapabilities
            );
        }
    }

    public static StructuredRequirement empty() {
        return new StructuredRequirement(
                CURRENT_SCHEMA_VERSION,
                "",
                "",
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static String normalizeOptionalText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        return value.trim();
    }

    private static List<String> copyCapabilities(List<String> capabilities, String fieldName) {
        Objects.requireNonNull(capabilities, fieldName + " must not be null");
        List<String> normalized = capabilities.stream()
                .map(capability -> normalizeCapability(capability, fieldName))
                .toList();
        if (new HashSet<>(normalized).size() != normalized.size()) {
            throw new IllegalArgumentException(fieldName + " must not contain duplicates");
        }
        return List.copyOf(normalized);
    }

    private static String normalizeCapability(String capability, String fieldName) {
        Objects.requireNonNull(capability, fieldName + " must not contain null values");
        String normalized = capability.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not contain blank values");
        }
        return normalized;
    }
}
