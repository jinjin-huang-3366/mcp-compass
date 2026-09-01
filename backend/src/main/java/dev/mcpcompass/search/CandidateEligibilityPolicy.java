package dev.mcpcompass.search;

import dev.mcpcompass.capability.CapabilityNameNormalizer;
import dev.mcpcompass.registry.McpServerEntity;
import dev.mcpcompass.requirement.RequirementConstraint;
import dev.mcpcompass.requirement.StructuredRequirement;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CandidateEligibilityPolicy {
    private static final Set<String> MUTATING_ACTIONS = Set.of(
            "archive", "create", "delete", "edit", "insert", "manage", "publish", "share", "update", "write"
    );

    public Eligibility evaluate(
            StructuredRequirement requirement,
            McpServerEntity server,
            Collection<String> serverCapabilities
    ) {
        LinkedHashSet<String> reasons = new LinkedHashSet<>();
        String advertisedText = advertisedText(server);

        for (String forbidden : requirement.forbiddenCapabilities()) {
            String capabilityEvidence = serverCapabilities.stream()
                    .filter(available -> violates(forbidden, available))
                    .findFirst()
                    .orElse(null);
            if (capabilityEvidence != null) {
                reasons.add("forbidden capability advertised: " + forbidden + " (normalized capability "
                        + capabilityEvidence + ")");
            } else if (advertisesForbidden(forbidden, advertisedText)) {
                reasons.add("forbidden capability advertised: " + forbidden + " (Registry metadata)");
            }
        }

        for (RequirementConstraint constraint : requirement.constraints()) {
            String violation = constraintViolation(constraint, advertisedText);
            if (violation != null) {
                reasons.add(violation);
            }
        }
        return new Eligibility(reasons.isEmpty(), List.copyOf(reasons));
    }

    private static boolean violates(String forbidden, String available) {
        String forbiddenKey = CapabilityNameNormalizer.matchingKey(forbidden);
        String availableKey = CapabilityNameNormalizer.matchingKey(available);
        if (forbiddenKey == null || availableKey == null) {
            return false;
        }
        if (forbiddenKey.equals(availableKey)) {
            return true;
        }
        List<String> forbiddenTokens = List.of(forbiddenKey.split("\\."));
        List<String> availableTokens = List.of(availableKey.split("\\."));
        if (forbiddenTokens.size() < 2 || availableTokens.size() < 2
                || !forbiddenTokens.getFirst().equals(availableTokens.getFirst())) {
            return false;
        }
        String forbiddenAction = forbiddenTokens.getLast();
        String availableAction = availableTokens.getLast();
        List<String> forbiddenSubject = forbiddenTokens.subList(0, forbiddenTokens.size() - 1);
        List<String> availableSubject = availableTokens.subList(0, availableTokens.size() - 1);
        boolean sameSubject = forbiddenSubject.equals(availableSubject);
        return sameSubject && (("write".equals(forbiddenAction) && MUTATING_ACTIONS.contains(availableAction))
                || (MUTATING_ACTIONS.contains(forbiddenAction) && "write".equals(availableAction)));
    }

    private static boolean advertisesForbidden(String forbidden, String text) {
        String canonical = CapabilityNameNormalizer.canonicalName(forbidden);
        if (canonical == null) {
            return false;
        }
        if (canonical.contains("voice.call")) {
            return containsAny(text, "voice", "make calls", "phone calls");
        }
        if (canonical.endsWith("repository.delete")) {
            return containsAny(text, "repository", "repositories", "repo", "repos")
                    && containsAny(text, "delete", "remove", "manage");
        }
        if (canonical.endsWith("branch.delete")) {
            return containsAny(text, "branch", "branches") && containsAny(text, "delete", "remove", "manage");
        }
        if (canonical.contains("row.insert")) {
            return mutationAdvertised(text, "insert", "write", "crud");
        }
        if (canonical.contains("row.update")) {
            return mutationAdvertised(text, "update", "write", "crud");
        }
        if (canonical.contains("row.delete")) {
            return mutationAdvertised(text, "delete", "write", "crud");
        }
        if (canonical.contains("row.write")) {
            return mutationAdvertised(text, "write", "insert", "update", "delete", "crud");
        }
        if (canonical.contains("schema.write")) {
            return text.contains("schema") && containsAny(text, "write", "create", "update", "crud");
        }
        if (canonical.endsWith("document.publish")) {
            return containsAny(text, "publish", "publishing", "public share");
        }
        if (canonical.endsWith("document.edit")) {
            return containsAny(text, "edit", "editing");
        }

        List<String> tokens = List.of(canonical.split("\\."));
        if (tokens.size() < 2) {
            return false;
        }
        String action = tokens.getLast();
        String subject = tokens.get(tokens.size() - 2);
        return text.contains(subject) && text.contains(action);
    }

    private static String constraintViolation(RequirementConstraint constraint, String text) {
        String expected = normalized(constraint.value());
        boolean evidenced = containsConstraintValue(constraint.name(), expected, text);
        return switch (constraint.operator()) {
            case EQUALS, CONTAINS -> evidenced
                    ? null
                    : "hard constraint not evidenced: %s %s %s".formatted(
                            constraint.name(), constraint.operator(), constraint.value());
            case NOT_EQUALS -> evidenced
                    ? "hard constraint violated: %s %s %s".formatted(
                            constraint.name(), constraint.operator(), constraint.value())
                    : null;
            case AT_LEAST, AT_MOST -> "hard constraint cannot be verified from normalized candidate metadata: %s %s %s"
                    .formatted(constraint.name(), constraint.operator(), constraint.value());
        };
    }

    private static boolean containsConstraintValue(String name, String expected, String text) {
        if ("read only".equals(expected)) {
            return Pattern.compile("\\bread[ -]?only\\b").matcher(text).find()
                    || containsAny(text, "writes refused", "write rejected", "select only");
        }
        if ("none".equals(expected) && "authentication".equalsIgnoreCase(name)) {
            return containsAny(text, "keyless", "no authentication", "without authentication", "auth not required");
        }
        if ("oauth2".equals(expected)) {
            return text.contains("oauth2") || text.contains("oauth 2");
        }
        return !expected.isBlank() && text.contains(expected);
    }

    private static String advertisedText(McpServerEntity server) {
        return normalized(String.join(" ",
                value(server.getRegistryName()),
                value(server.getTitle()),
                value(server.getDescription())
        ));
    }

    private static String normalized(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mutationAdvertised(String text, String... actions) {
        if (containsAny(text, "writes refused", "write refused", "writes rejected", "write rejected",
                "read only", "readonly")) {
            return false;
        }
        return containsAny(text, actions);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public record Eligibility(boolean eligible, List<String> reasons) {
    }
}
