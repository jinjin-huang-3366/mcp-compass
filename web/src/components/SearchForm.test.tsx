// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SearchForm } from "./SearchForm";
import { searchMcps } from "../lib/api";
import { clearSearchCache } from "../lib/search-cache";

const navigation = vi.hoisted(() => ({
  push: vi.fn(),
  search: "q=Read+GitHub+issues&page=2",
}));

vi.mock("next/navigation", () => ({
  usePathname: () => "/",
  useRouter: () => ({ push: navigation.push }),
  useSearchParams: () => new URLSearchParams(navigation.search),
}));

vi.mock("../lib/api", () => ({
  searchMcps: vi.fn(),
}));

afterEach(() => {
  cleanup();
  clearSearchCache();
  navigation.search = "q=Read+GitHub+issues&page=2";
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

    const firstVisit = render(<SearchForm />);

    expect(await screen.findByRole("link", { name: "View MCP details" }))
      .toHaveAttribute("href", "/mcp/server-1?q=Read+GitHub+issues&page=2");
    expect(screen.getByRole("link", { name: /Source repository/ }))
      .toHaveAttribute("href", "https://github.com/example/github-mcp");
    expect(screen.getByRole("heading", { name: "Parsed intent" })).toBeInTheDocument();
    expect(screen.getAllByText("github.issue.read")).toHaveLength(2);

    firstVisit.unmount();
    render(<SearchForm />);

    expect(await screen.findByRole("link", { name: "View MCP details" })).toBeInTheDocument();
    expect(searchMcps).toHaveBeenCalledTimes(1);
  });

  it("starts a new search before updating the shareable URL", () => {
    navigation.search = "";
    vi.mocked(searchMcps).mockImplementation(() => new Promise(() => undefined));

    render(<SearchForm />);
    fireEvent.change(screen.getByLabelText("What does your agent need to do?"), {
      target: { value: "Find a read-only PostgreSQL MCP" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Find MCP" }));

    expect(searchMcps).toHaveBeenCalledWith("Find a read-only PostgreSQL MCP", 1, 10);
    expect(navigation.push).toHaveBeenCalledWith("/?q=Find+a+read-only+PostgreSQL+MCP");
    expect(vi.mocked(searchMcps).mock.invocationCallOrder[0])
      .toBeLessThan(navigation.push.mock.invocationCallOrder[0]);
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
