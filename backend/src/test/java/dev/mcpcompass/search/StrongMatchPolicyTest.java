package dev.mcpcompass.search;

import dev.mcpcompass.ranking.RankingService;
import dev.mcpcompass.registry.McpServerEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StrongMatchPolicyTest {
    private final StrongMatchPolicy policy = new StrongMatchPolicy();

    @Test
    void keepsOnlyCandidatesAtOrAboveTheCalibratedThreshold() {
        StrongMatchPolicy.Assessment result = policy.assess(
                List.of(ranked(0.8), ranked(0.30), ranked(0.299)), 3, 0
        );

        assertThat(result.strongMatch()).isTrue();
        assertThat(result.matches()).extracting(RankingService.RankedServer::score)
                .containsExactly(0.8, 0.30);
        assertThat(result.abstentionReasons()).isEmpty();
    }

    @Test
    void explainsLowConfidenceAndMissingCapabilitiesWhenAbstaining() {
        RankingService.RankedServer weak = ranked(0.292, List.of("github.pull-request.comment.create"));

        StrongMatchPolicy.Assessment result = policy.assess(List.of(weak), 4, 2);

        assertThat(result.strongMatch()).isFalse();
        assertThat(result.matches()).isEmpty();
        assertThat(result.abstentionReasons()).containsExactly(
                "Best candidate confidence 29% is below the calibrated strong-match threshold of 30%.",
                "Best candidate is missing required capabilities: github.pull-request.comment.create."
        );
    }

    @Test
    void explainsWhenHardConstraintsExcludeEverything() {
        StrongMatchPolicy.Assessment result = policy.assess(List.of(), 3, 3);

        assertThat(result.abstentionReasons()).containsExactly(
                "All retrieved candidates were excluded by the parsed hard constraints."
        );
    }

    private static RankingService.RankedServer ranked(double score) {
        return ranked(score, List.of());
    }

    private static RankingService.RankedServer ranked(double score, List<String> missingCapabilities) {
        return new RankingService.RankedServer(
                mock(McpServerEntity.class), score, 0.0, null, List.of(), missingCapabilities,
                new RankingService.RankingExplanation(List.of(), score, 1.0), List.of()
        );
    }
}
