package dev.mcpcompass.requirement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

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
            StructuredRequirement structuredRequirement = llmClient.analyze(requirement);
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
}
