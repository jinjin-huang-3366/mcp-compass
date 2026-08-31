// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import McpDetailPage from "./page";
import { getMcpDetail } from "../../../lib/api";

vi.mock("next/navigation", () => ({ notFound: vi.fn() }));

vi.mock("../../../lib/api", () => ({
  getMcpDetail: vi.fn(),
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe("McpDetailPage", () => {
  it("returns to the search requirement and page supplied by the result link", async () => {
    vi.mocked(getMcpDetail).mockResolvedValue({
      id: "server-1",
      registryName: "io.example/github",
      title: "GitHub MCP",
      description: "Read GitHub issues",
      version: "1.0.0",
      status: "active",
      repositoryUrl: "https://github.com/example/github-mcp",
      firstSeenAt: "2026-08-01T10:00:00Z",
      lastSeenAt: "2026-08-30T10:00:00Z",
    });

    const page = await McpDetailPage({
      params: Promise.resolve({ id: "server-1" }),
      searchParams: Promise.resolve({ q: "Read GitHub issues", page: "2" }),
    });
    render(page);

    expect(screen.getByRole("link", { name: "Back to search" }))
      .toHaveAttribute("href", "/?q=Read+GitHub+issues&page=2");
    expect(screen.getByRole("link", { name: /Source repository/ }))
      .toHaveAttribute("href", "https://github.com/example/github-mcp");
  });
});
