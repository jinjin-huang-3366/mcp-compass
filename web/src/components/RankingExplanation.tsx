import type { RankingExplanation as RankingExplanationModel } from "@/lib/api";

const LABELS = {
  capabilityCoverage: "Capability coverage",
  retrievalRelevance: "Retrieval relevance",
  quality: "Quality and trust",
} as const;

type RankingExplanationProps = {
  explanation: RankingExplanationModel;
  finalScore: number;
};

function percentage(value: number) {
  return `${Math.round(value * 100)}%`;
}

export function RankingExplanation({ explanation, finalScore }: RankingExplanationProps) {
  return (
    <section className="rankingExplanation" aria-label="Ranking score explanation">
      <h3>Why this score?</h3>
      <dl>
        {explanation.contributions.map((item) => (
          <div key={item.feature}>
            <dt>{LABELS[item.feature]}</dt>
            <dd>
              {percentage(item.featureScore)} signal × {percentage(item.weight)} weight
              <strong>+{percentage(item.contribution)}</strong>
            </dd>
          </div>
        ))}
        {explanation.statusMultiplier < 1 && (
          <div>
            <dt>Status adjustment</dt>
            <dd>
              {percentage(explanation.preAdjustmentScore)} × {explanation.statusMultiplier.toFixed(1)}
              <strong>{percentage(finalScore)}</strong>
            </dd>
          </div>
        )}
      </dl>
      <p>
        Contributions total {percentage(explanation.preAdjustmentScore)} before status adjustments;
        final score {percentage(finalScore)}.
      </p>
    </section>
  );
}
