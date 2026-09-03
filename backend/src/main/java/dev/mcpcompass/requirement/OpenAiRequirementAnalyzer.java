package dev.mcpcompass.requirement;

import dev.mcpcompass.capability.CapabilityNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;

@Component
@Primary
@ConditionalOnProperty(prefix = "app.requirement.llm", name = "enabled", havingValue = "true")
public class OpenAiRequirementAnalyzer implements RequirementAnalyzer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiRequirementAnalyzer.class);

    private final HeuristicRequirementAnalyzer heuristicAnalyzer;
    private final OpenAiRequirementClient llmClient;

    public OpenAiRequirementAnalyzer(
            HeuristicRequirementAnalyzer heuristicAnalyzer,
            OpenAiRequirementClient llmClient
    ) {
        this.heuristicAnalyzer = heuristicAnalyzer;
        this.llmClient = llmClient;
    }

    @Override
    public RequirementAnalysis analyze(String requirement) {
        RequirementAnalysis heuristicAnalysis = heuristicAnalyzer.analyze(requirement);
        try {
            StructuredRequirement structuredRequirement = preserveDeterministicSafety(
                    llmClient.analyze(requirement),
                    heuristicAnalysis.structuredRequirement()
            );
            return new RequirementAnalysis(
                    heuristicAnalysis.originalRequirement(),
                    heuristicAnalysis.keywords(),
                    structuredRequirement
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "LLM requirement analysis failed; using deterministic fallback ({})",
                    exception.getClass().getSimpleName()
            );
            return heuristicAnalysis;
        }
    }

    private static StructuredRequirement preserveDeterministicSafety(
            StructuredRequirement llmRequirement,
            StructuredRequirement deterministicRequirement
    ) {
        var forbiddenCapabilities = new LinkedHashSet<>(llmRequirement.forbiddenCapabilities());
        if (forbiddenCapabilities.isEmpty()) {
            forbiddenCapabilities.addAll(deterministicRequirement.forbiddenCapabilities());
        }

        var requiredCapabilities = new LinkedHashSet<>(llmRequirement.requiredCapabilities());
        requiredCapabilities.removeIf(required -> forbiddenCapabilities.stream()
                .anyMatch(forbidden -> sameCapability(required, forbidden)));

        return new StructuredRequirement(
                llmRequirement.schemaVersion(),
                llmRequirement.domain(),
                llmRequirement.service().isBlank()
                        ? deterministicRequirement.service()
                        : llmRequirement.service(),
                requiredCapabilities.stream().toList(),
                forbiddenCapabilities.stream().toList(),
                llmRequirement.constraints()
        );
    }

    private static boolean sameCapability(String left, String right) {
        String leftKey = CapabilityNameNormalizer.matchingKey(left);
        return leftKey != null && leftKey.equals(CapabilityNameNormalizer.matchingKey(right));
    }
}
