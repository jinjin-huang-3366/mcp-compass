// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SearchForm } from "./SearchForm";
import { recordSelectionEvent, searchMcps } from "../lib/api";
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
  recordSelectionEvent: vi.fn(),
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
      totalExcluded: 1,
      exclusions: [{
        id: "excluded-server",
        title: "PostgreSQL Report Reader",
        registryName: "io.example/postgres-report-reader",
        reasons: ["forbidden capability may be reachable (static tool schema signal: ARBITRARY_SQL)"],
        forbiddenCapabilityEvidence: [{
          capability: "postgres.row.delete",
          status: "SCHEMA_INFERRED_RISK",
          registryDeclaration: "SELF_DESCRIBED_READ_ONLY",
          normalizedToolMetadata: "SELF_DESCRIBED_NO_WRITE_PATH",
          observedToolsList: "NOT_OBSERVED",
          permissionEnforcement: "NOT_VERIFIED",
          staticToolSchema: {
            revision: "2.0.0",
            inspectorVersion: "1",
            partial: false,
            schemas: [{
              tool: "preview_report",
              source: "publisher-extension-metadata",
              sha256: "b".repeat(64),
              uncertainty: "INFERRED",
              signals: ["ARBITRARY_SQL"],
            }],
          },
        }],
      }],
      matches: [{
        id: "server-1",
        registryName: "io.example/github",
        title: "GitHub MCP",
        description: "Read GitHub issues",
        version: "1.0.0",
        status: "active",
        repositoryUrl: "https://github.com/example/github-mcp",
        remoteConnectionState: "REACHABLE",
        remoteConnectionCheckedAt: "2026-10-25T12:00:00Z",
        remoteConnectionReason: "MCP initialize succeeded",
        score: 0.9,
        qualityScore: 0.8,
        capabilityCoverage: 1,
        matchedCapabilities: ["github.issue.read"],
        missingCapabilities: [],
        forbiddenCapabilityEvidence: [{
          capability: "postgres.row.write",
          status: "UNKNOWN",
          registryDeclaration: "SELF_DESCRIBED_READ_ONLY",
          normalizedToolMetadata: "SELF_DESCRIBED_NO_WRITE_PATH",
          observedToolsList: "NOT_OBSERVED",
          permissionEnforcement: "NOT_VERIFIED",
          staticToolSchema: {
            revision: "1.0.0",
            inspectorVersion: "1",
            partial: true,
            schemas: [{
              tool: "preview",
              source: "registry-server-metadata",
              sha256: "a".repeat(64),
              uncertainty: "INCOMPLETE",
              signals: ["CALLER_CONTROLLED_APPROVAL"],
            }],
          },
        }],
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
    expect(screen.getByText(/MCP connection reached/)).toHaveTextContent("MCP initialize succeeded");
    expect(screen.getByRole("heading", { name: "Parsed intent" })).toBeInTheDocument();
    expect(screen.getAllByText("github.issue.read")).toHaveLength(2);
    expect(screen.getByText("postgres.row.write").closest("li"))
      .toHaveTextContent("postgres.row.write: UNKNOWN / NOT_VERIFIED");
    expect(screen.getAllByText(/has not observed tools\/list or verified server-enforced permissions/)).toHaveLength(2);
    expect(screen.getByText("Static schema inspection (partial)")).toBeInTheDocument();
    expect(screen.getByText(/Caller-controlled approval setting/)).toHaveTextContent("registry-server-metadata");
    expect(screen.getByText("postgres.row.delete").closest("li"))
      .toHaveTextContent("postgres.row.delete: SCHEMA_INFERRED_RISK / NOT_VERIFIED");
    expect(screen.getByText(/Caller-supplied SQL/)).toHaveTextContent("publisher-extension-metadata");
    expect(recordSelectionEvent).not.toHaveBeenCalled();

    const detailLink = screen.getByRole("link", { name: "View MCP details" });
    detailLink.addEventListener("click", (event) => event.preventDefault());
    fireEvent.click(detailLink);
    fireEvent(
      screen.getByRole("link", { name: /Source repository/ }),
      new MouseEvent("auxclick", { button: 1, bubbles: true }),
    );
    expect(vi.mocked(recordSelectionEvent).mock.calls).toEqual([["DETAIL_OPENED"], ["SOURCE_OPENED"]]);

    vi.mocked(recordSelectionEvent).mockResolvedValueOnce(false).mockResolvedValueOnce(true);
    fireEvent.click(screen.getByRole("button", { name: "Yes" }));
    expect(await screen.findByText("Your answer couldn't be recorded. You can try again.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Yes" }));
    expect(await screen.findByText("Thanks for sharing your answer.")).toBeInTheDocument();
    expect(recordSelectionEvent).toHaveBeenLastCalledWith("CHOICE_CHANGED");
    expect(screen.queryByRole("button", { name: "Yes" })).not.toBeInTheDocument();

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
    expect(vi.mocked(recordSelectionEvent).mock.calls).toEqual([["SEARCH_SUBMITTED"]]);
  });

  it("counts the exact outcome of an explicit search without including the requirement", async () => {
    navigation.search = "";
    vi.mocked(searchMcps).mockRejectedValueOnce(new Error("Network error"));
    render(<SearchForm />);

    fireEvent.click(screen.getByRole("button", { name: "Find MCP" }));
    await waitFor(() => expect(vi.mocked(recordSelectionEvent).mock.calls)
      .toEqual([["SEARCH_SUBMITTED"], ["SEARCH_FAILED"]]));
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
