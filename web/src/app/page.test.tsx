// @vitest-environment jsdom

import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "./page";

vi.mock("@/components/SearchForm", () => ({
  SearchForm: () => <div>Search form</div>,
}));

afterEach(cleanup);

describe("Home", () => {
  it("links clearly to Node.js MCP generation without the obsolete roadmap note", () => {
    render(<Home />);

    expect(screen.getByRole("link", { name: "Generate a Node.js MCP server from OpenAPI" }))
      .toHaveAttribute("href", "/generate");
    expect(screen.queryByText(/MCP generation comes after search quality is proven/i))
      .not.toBeInTheDocument();
  });
});
