package dev.mcpcompass.requirement;

import java.util.Objects;

public record RequirementConstraint(String name, Operator operator, String value) {
    public RequirementConstraint {
        name = requireNonBlank(name, "name");
        operator = Objects.requireNonNull(operator, "operator must not be null");
        value = requireNonBlank(value, "value");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return trimmed;
    }

    public enum Operator {
        EQUALS,
        NOT_EQUALS,
        CONTAINS,
        AT_LEAST,
        AT_MOST
    }
}
