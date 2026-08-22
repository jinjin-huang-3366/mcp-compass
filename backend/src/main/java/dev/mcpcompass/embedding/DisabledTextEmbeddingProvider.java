package dev.mcpcompass.embedding;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(
        prefix = "app.search.vector",
        name = "enabled",
        havingValue = "false",
        matchIfMissing = true
)
class DisabledTextEmbeddingProvider implements TextEmbeddingProvider {
    @Override
    public List<EmbeddingVector> embed(List<String> inputs) {
        return List.of();
    }
}
