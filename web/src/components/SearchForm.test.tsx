// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SearchForm } from "./SearchForm";
import { searchMcps } from "../lib/api";

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams("q=Read+GitHub+issues&page=2"),
}));

vi.mock("../lib/api", () => ({
  searchMcps: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("SearchForm", () => {
  it("carries the active search and page into MCP detail links", async () => {
    vi.mocked(searchMcps).mockResolvedValue({
      requirement: "Read GitHub issues",
      keywords: ["github", "issues"],
      parsedIntent: {
        domain: "source-control",
        service: "github",
        requiredCapabilities: ["github.issue.read"],
        forbiddenCapabilities: [],
        constraints: [],
      },
      strongMatch: true,
      confidenceThreshold: 0.3,
      abstentionReasons: [],
      page: 2,
      pageSize: 10,
      totalMatches: 11,
      totalPages: 2,
      totalExcluded: 0,
      exclusions: [],
      matches: [{
        id: "server-1",
        registryName: "io.example/github",
        title: "GitHub MCP",
        description: "Read GitHub issues",
        version: "1.0.0",
        status: "active",
        repositoryUrl: "https://github.com/example/github-mcp",
        score: 0.9,
        qualityScore: 0.8,
        capabilityCoverage: 1,
        matchedCapabilities: ["github.issue.read"],
        missingCapabilities: [],
        rankingExplanation: {
          contributions: [],
          preAdjustmentScore: 0.9,
          statusMultiplier: 1,
        },
        reasons: ["matches github issues"],
      }],
    });

    render(<SearchForm />);

    expect(await screen.findByRole("link", { name: "View MCP details" }))
      .toHaveAttribute("href", "/mcp/server-1?q=Read+GitHub+issues&page=2");
    expect(screen.getByRole("link", { name: /Source repository/ }))
      .toHaveAttribute("href", "https://github.com/example/github-mcp");
    expect(screen.getByRole("heading", { name: "Parsed intent" })).toBeInTheDocument();
    expect(screen.getAllByText("github.issue.read")).toHaveLength(2);
  });

  it("shows parsed hard conditions and an explicit abstention", async () => {
    vi.mocked(searchMcps).mockResolvedValue({
      requirement: "Read GitHub issues",
      keywords: ["github", "issues"],
      parsedIntent: {
        domain: "source-control",
        service: "github",
        requiredCapabilities: ["github.issue.read"],
        forbiddenCapabilities: ["github.repository.delete"],
        constraints: [{ name: "access-mode", operator: "EQUALS", value: "read-only" }],
      },
      strongMatch: false,
      confidenceThreshold: 0.3,
      abstentionReasons: ["All retrieved candidates were excluded by the parsed hard constraints."],
      page: 2,
      pageSize: 10,
      totalMatches: 0,
      totalPages: 0,
      totalExcluded: 2,
      exclusions: [],
      matches: [],
    });

    render(<SearchForm />);

    expect(await screen.findByRole("heading", { name: "No strong match" })).toBeInTheDocument();
    expect(screen.getByText("github.repository.delete")).toBeInTheDocument();
    expect(screen.getByText("access-mode EQUALS read-only")).toBeInTheDocument();
    expect(screen.getByText("No result met the 30% strong-match threshold.")).toBeInTheDocument();
  });
});
