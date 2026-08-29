import { describe, expect, it } from "vitest";
import { detailUrl, searchReturnUrl } from "./search-navigation";

describe("search navigation", () => {
  it("preserves a paged requirement in a result detail URL", () => {
    expect(detailUrl("server/one", " Read GitHub issues ", 2))
      .toBe("/mcp/server%2Fone?q=Read+GitHub+issues&page=2");
  });

  it("omits the default page when returning to search", () => {
    expect(searchReturnUrl({ q: "Read GitHub issues", page: "1" }))
      .toBe("/?q=Read+GitHub+issues");
  });

  it("falls back to the search page when no requirement is present", () => {
    expect(searchReturnUrl({ page: "2" })).toBe("/");
  });
});
