// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { RankingExplanation } from "./RankingExplanation";

afterEach(cleanup);

describe("RankingExplanation", () => {
  it("shows each deterministic feature contribution and the final score", () => {
    render(
      <RankingExplanation
        finalScore={0.576}
        explanation={{
          preAdjustmentScore: 0.576,
          statusMultiplier: 1,
          contributions: [
            { feature: "capabilityCoverage", featureScore: 0.5, weight: 0.8, contribution: 0.4 },
            { feature: "retrievalRelevance", featureScore: 1, weight: 0.17, contribution: 0.17 },
            { feature: "quality", featureScore: 0.2, weight: 0.03, contribution: 0.006 },
          ],
        }}
      />,
    );

    expect(screen.getByRole("heading", { name: "Why this score?" })).toBeInTheDocument();
    expect(screen.getByText("50% signal × 80% weight")).toBeInTheDocument();
    expect(screen.getByText("100% signal × 17% weight")).toBeInTheDocument();
    expect(screen.getByText("20% signal × 3% weight")).toBeInTheDocument();
    expect(screen.getByText(/final score 58%/)).toBeInTheDocument();
  });

  it("makes a deprecated status multiplier visible", () => {
    render(
      <RankingExplanation
        finalScore={0.425}
        explanation={{
          preAdjustmentScore: 0.85,
          statusMultiplier: 0.5,
          contributions: [
            { feature: "retrievalRelevance", featureScore: 1, weight: 0.85, contribution: 0.85 },
            { feature: "quality", featureScore: 0, weight: 0.15, contribution: 0 },
          ],
        }}
      />,
    );

    expect(screen.getByText("Status adjustment")).toBeInTheDocument();
    expect(screen.getByText("85% × 0.5")).toBeInTheDocument();
    expect(screen.getByText(/final score 43%/)).toBeInTheDocument();
  });
});
