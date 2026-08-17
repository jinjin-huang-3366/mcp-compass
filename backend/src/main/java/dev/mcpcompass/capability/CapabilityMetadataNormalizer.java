package dev.mcpcompass.capability;

import dev.mcpcompass.registry.RegistryClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class CapabilityMetadataNormalizer {
    static final String SERVER_METADATA_SOURCE = "server-metadata";
    static final String TOOL_METADATA_SOURCE = "tool-metadata";
    static final String TOOL_NAME_SOURCE = "tool-name";
    static final double DECLARED_CONFIDENCE = 1.0;
    static final double TOOL_NAME_CONFIDENCE = 0.7;

    private static final int MAX_NAME_LENGTH = 255;
    private static final Pattern CAPABILITY_SEPARATOR = Pattern.compile("[^a-z0-9-]+");
    private static final Pattern REPEATED_DOTS = Pattern.compile("\\.{2,}");

    public NormalizedCapabilityMetadata normalize(RegistryClient.RegistryServerPayload payload) {
        Map<String, NormalizedCapabilityMetadata.NormalizedCapability> serverCapabilities =
                new LinkedHashMap<>();
        for (RegistryClient.RegistryCapabilityPayload capability : payload.capabilities()) {
            normalizeCapability(capability, DECLARED_CONFIDENCE, SERVER_METADATA_SOURCE)
                    .ifPresent(normalized -> merge(serverCapabilities, normalized));
        }

        Map<String, NormalizedCapabilityMetadata.NormalizedTool> tools = new LinkedHashMap<>();
        for (RegistryClient.RegistryToolPayload tool : payload.tools()) {
            String toolName = normalizeToolName(tool.name());
            if (toolName == null || tools.containsKey(toolName)) {
                continue;
            }

            Map<String, NormalizedCapabilityMetadata.NormalizedCapability> toolCapabilities =
                    new LinkedHashMap<>();
            for (RegistryClient.RegistryCapabilityPayload capability : tool.capabilities()) {
                normalizeCapability(capability, DECLARED_CONFIDENCE, TOOL_METADATA_SOURCE)
                        .ifPresent(normalized -> merge(toolCapabilities, normalized));
            }
            if (toolCapabilities.isEmpty()) {
                normalizeCapability(
                        new RegistryClient.RegistryCapabilityPayload(
                                derivedToolCapability(payload.name(), toolName),
                                tool.description()
                        ),
                        TOOL_NAME_CONFIDENCE,
                        TOOL_NAME_SOURCE
                ).ifPresent(normalized -> merge(toolCapabilities, normalized));
            }
            toolCapabilities.values().forEach(capability -> merge(serverCapabilities, capability));

            tools.put(toolName, new NormalizedCapabilityMetadata.NormalizedTool(
                    toolName,
                    normalizeOptionalText(tool.description()),
                    normalizeOptionalText(tool.inputSchema()),
                    List.copyOf(toolCapabilities.values())
            ));
        }

        return new NormalizedCapabilityMetadata(
                List.copyOf(tools.values()),
                List.copyOf(serverCapabilities.values())
        );
    }

    private static java.util.Optional<NormalizedCapabilityMetadata.NormalizedCapability> normalizeCapability(
            RegistryClient.RegistryCapabilityPayload capability,
            double confidence,
            String source
    ) {
        if (capability == null) {
            return java.util.Optional.empty();
        }
        String canonicalName = canonicalName(capability.name());
        if (canonicalName == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new NormalizedCapabilityMetadata.NormalizedCapability(
                canonicalName,
                normalizeOptionalText(capability.description()),
                confidence,
                source
        ));
    }

    static String canonicalName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String canonical = CAPABILITY_SEPARATOR.matcher(
                        value.strip().toLowerCase(Locale.ROOT))
                .replaceAll(".");
        canonical = REPEATED_DOTS.matcher(canonical).replaceAll(".");
        canonical = trimDots(canonical);
        return canonical.isBlank() || canonical.length() > MAX_NAME_LENGTH ? null : canonical;
    }

    private static String derivedToolCapability(String registryName, String toolName) {
        String canonicalToolName = canonicalName(toolName);
        if (canonicalToolName == null) {
            return toolName;
        }
        String namespaceCandidate = registryName;
        if (registryName != null && registryName.contains("/")) {
            namespaceCandidate = registryName.substring(registryName.lastIndexOf('/') + 1);
        }
        String namespace = canonicalName(namespaceCandidate);
        if (namespace == null || canonicalToolName.startsWith(namespace + ".")) {
            return canonicalToolName;
        }
        return namespace + "." + canonicalToolName;
    }

    private static String normalizeToolName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() > MAX_NAME_LENGTH ? null : normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private static String trimDots(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '.') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(start, end);
    }

    private static void merge(
            Map<String, NormalizedCapabilityMetadata.NormalizedCapability> target,
            NormalizedCapabilityMetadata.NormalizedCapability incoming
    ) {
        target.merge(incoming.canonicalName(), incoming, (existing, replacement) -> {
            if (replacement.confidence() > existing.confidence()) {
                return replacement;
            }
            if (replacement.confidence() == existing.confidence()
                    && existing.description() == null
                    && replacement.description() != null) {
                return new NormalizedCapabilityMetadata.NormalizedCapability(
                        existing.canonicalName(),
                        replacement.description(),
                        existing.confidence(),
                        existing.source()
                );
            }
            return existing;
        });
    }
}
