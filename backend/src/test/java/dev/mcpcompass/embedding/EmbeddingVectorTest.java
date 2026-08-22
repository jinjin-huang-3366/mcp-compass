package dev.mcpcompass.embedding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingVectorTest {
    @Test
    void serializesValidatedValuesForPgvector() {
        List<Double> values = new ArrayList<>(Collections.nCopies(EmbeddingProperties.DIMENSIONS, 0.0));
        values.set(0, 0.25);
        values.set(values.size() - 1, -0.5);

        EmbeddingVector vector = new EmbeddingVector("text-embedding-3-small", values);

        assertThat(vector.postgresLiteral()).startsWith("[0.25,0.0").endsWith(",-0.5]");
    }

    @Test
    void rejectsWrongDimensionsAndNonFiniteValues() {
        assertThatThrownBy(() -> new EmbeddingVector("model", List.of(1.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 384");

        List<Double> values = new ArrayList<>(Collections.nCopies(EmbeddingProperties.DIMENSIONS, 0.0));
        values.set(20, Double.NaN);
        assertThatThrownBy(() -> new EmbeddingVector("model", values))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }
}
