package dev.mcpcompass.requirement;

import dev.mcpcompass.capability.CapabilityNameNormalizer;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

final class StructuredRequirementCanonicalizer {
    private static final Pattern DOCUMENTATION_INTENT = Pattern.compile("(?i)\\b(?:docs?|documentation)\\b");
    private static final Set<String> DOCUMENT_MUTATIONS = Set.of(
            "create", "edit", "publish", "share", "update", "write"
    );

    private StructuredRequirementCanonicalizer() {
    }

    static StructuredRequirement canonicalize(
            String originalRequirement,
            StructuredRequirement llmRequirement,
            StructuredRequirement deterministicRequirement
    ) {
        String service = canonicalService(llmRequirement.service(), deterministicRequirement.service());
        boolean documentationIntent = DOCUMENTATION_INTENT.matcher(originalRequirement).find();
        boolean deterministicReadOnly = ("postgres".equals(service) || documentationIntent) && hasConstraint(
                deterministicRequirement, "access-mode", "read-only"
        );
        boolean deterministicNoAuthentication = documentationIntent && hasConstraint(
                deterministicRequirement, "authentication", "none"
        );

        LinkedHashSet<String> requiredCapabilities = canonicalCapabilities(
                llmRequirement.requiredCapabilities(),
                service,
                documentationIntent,
                deterministicReadOnly,
                deterministicNoAuthentication,
                false
        );
        LinkedHashSet<String> forbiddenCapabilities = canonicalCapabilities(
                llmRequirement.forbiddenCapabilities(),
                service,
                documentationIntent,
                deterministicReadOnly,
                deterministicNoAuthentication,
                true
        );

        boolean llmOmittedForbiddenIntent = forbiddenCapabilities.isEmpty();
        deterministicRequirement.forbiddenCapabilities().stream()
                .filter(capability -> llmOmittedForbiddenIntent || isHighConfidenceSafetyProhibition(capability))
                .forEach(forbiddenCapabilities::add);
        requiredCapabilities.removeIf(required -> forbiddenCapabilities.stream()
                .anyMatch(forbidden -> sameCapability(required, forbidden)));

        return new StructuredRequirement(
                llmRequirement.schemaVersion(),
                llmRequirement.domain(),
                service,
                List.copyOf(requiredCapabilities),
                List.copyOf(forbiddenCapabilities),
                canonicalConstraints(
                        llmRequirement.constraints(),
                        deterministicRequirement.constraints(),
                        deterministicReadOnly,
                        deterministicNoAuthentication
                )
        );
    }

    private static LinkedHashSet<String> canonicalCapabilities(
            List<String> capabilities,
            String service,
            boolean documentationIntent,
            boolean deterministicReadOnly,
            boolean deterministicNoAuthentication,
            boolean forbidden
    ) {
        LinkedHashSet<String> canonical = new LinkedHashSet<>();
        capabilities.stream()
                .map(capability -> canonicalCapability(
                        capability,
                        service,
                        documentationIntent,
                        deterministicReadOnly,
                        deterministicNoAuthentication,
                        forbidden
                ))
                .filter(java.util.Objects::nonNull)
                .forEach(canonical::add);
        return canonical;
    }

    private static String canonicalCapability(
            String capability,
            String service,
            boolean documentationIntent,
            boolean deterministicReadOnly,
            boolean deterministicNoAuthentication,
            boolean forbidden
    ) {
        String canonical = CapabilityNameNormalizer.canonicalName(capability);
        if (canonical == null) {
            return null;
        }

        if ("postgres".equals(service)) {
            canonical = replacePrefix(canonical, "postgresql", "postgres");
            canonical = replacePrefix(canonical, "database", "postgres");
            if (deterministicReadOnly && forbidden) {
                canonical = switch (canonical) {
                    case "postgres.insert" -> "postgres.row.insert";
                    case "postgres.update" -> "postgres.row.update";
                    case "postgres.delete" -> "postgres.row.delete";
                    case "postgres.write" -> "postgres.row.write";
                    default -> canonical;
                };
            }
        }

        if (documentationIntent) {
            canonical = replacePrefix(canonical, "web.documentation", "documentation");
            canonical = replacePrefix(canonical, "web.docs", "documentation");
            canonical = replacePrefix(canonical, "docs", "documentation");
            canonical = switch (canonical) {
                case "web.search", "documentation.lookup" -> "documentation.search";
                case "web.fetch", "web.page.fetch", "web.page.read",
                        "documentation.fetch", "documentation.page.fetch" -> "documentation.page.read";
                default -> canonical;
            };

            if (forbidden && deterministicNoAuthentication && isAuthenticationCapability(canonical)) {
                return null;
            }
            if (forbidden && deterministicReadOnly) {
                String action = lastToken(canonical);
                if (DOCUMENT_MUTATIONS.contains(action)) {
                    return "publish".equals(action) || "share".equals(action)
                            ? "document.publish"
                            : "document.edit";
                }
            }
        }
        return canonical;
    }

    private static List<RequirementConstraint> canonicalConstraints(
            List<RequirementConstraint> llmConstraints,
            List<RequirementConstraint> deterministicConstraints,
            boolean deterministicReadOnly,
            boolean deterministicNoAuthentication
    ) {
        LinkedHashMap<String, RequirementConstraint> constraints = new LinkedHashMap<>();
        llmConstraints.stream()
                .filter(constraint -> !deterministicReadOnly || !describesReadOnly(constraint))
                .filter(constraint -> !deterministicNoAuthentication || !describesAuthentication(constraint))
                .forEach(constraint -> constraints.put(constraintKey(constraint), constraint));
        deterministicConstraints.stream()
                .filter(constraint -> deterministicReadOnly && describesReadOnly(constraint)
                        || deterministicNoAuthentication && describesAuthentication(constraint))
                .forEach(constraint -> constraints.put(constraintKey(constraint), constraint));
        return List.copyOf(constraints.values());
    }

    private static boolean describesReadOnly(RequirementConstraint constraint) {
        String name = normalizedText(constraint.name());
        String value = normalizedText(constraint.value());
        return name.contains("access") || name.contains("write")
                || value.equals("read only") || value.equals("no write");
    }

    private static boolean describesAuthentication(RequirementConstraint constraint) {
        return normalizedText(constraint.name()).contains("auth");
    }

    private static String constraintKey(RequirementConstraint constraint) {
        return normalizedText(constraint.name());
    }

    private static boolean hasConstraint(StructuredRequirement requirement, String name, String value) {
        return requirement.constraints().stream().anyMatch(constraint ->
                constraint.name().equalsIgnoreCase(name) && constraint.value().equalsIgnoreCase(value));
    }

    private static String canonicalService(String llmService, String deterministicService) {
        if ("postgres".equals(deterministicService)) {
            return "postgres";
        }
        String canonical = CapabilityNameNormalizer.canonicalName(llmService);
        if (canonical == null) {
            return deterministicService;
        }
        return switch (canonical) {
            case "postgresql" -> "postgres";
            default -> llmService.trim();
        };
    }

    private static boolean sameCapability(String left, String right) {
        String leftKey = CapabilityNameNormalizer.matchingKey(left);
        return leftKey != null && leftKey.equals(CapabilityNameNormalizer.matchingKey(right));
    }

    private static boolean isHighConfidenceSafetyProhibition(String capability) {
        String key = CapabilityNameNormalizer.matchingKey(capability);
        return key != null && (
                key.endsWith("repository.delete")
                        || key.endsWith("branch.delete")
                        || key.endsWith("voice.call.create")
                        || key.equals("postgres.row.insert")
                        || key.equals("postgres.row.update")
                        || key.equals("postgres.row.delete")
                        || key.equals("postgres.row.write")
                        || key.equals("postgres.schema.write")
                        || key.equals("document.publish")
                        || key.equals("document.edit")
        );
    }

    private static boolean isAuthenticationCapability(String capability) {
        return capability.startsWith("auth.")
                || capability.startsWith("authentication.")
                || capability.endsWith(".auth")
                || capability.endsWith(".authentication");
    }

    private static String replacePrefix(String value, String from, String to) {
        if (value.equals(from)) {
            return to;
        }
        return value.startsWith(from + ".") ? to + value.substring(from.length()) : value;
    }

    private static String lastToken(String capability) {
        int separator = capability.lastIndexOf('.');
        return separator < 0 ? capability : capability.substring(separator + 1);
    }

    private static String normalizedText(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
