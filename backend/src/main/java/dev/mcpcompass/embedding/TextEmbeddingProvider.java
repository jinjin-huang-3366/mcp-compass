package dev.mcpcompass.embedding;

import java.util.List;

public interface TextEmbeddingProvider {
    List<EmbeddingVector> embed(List<String> inputs);
}
