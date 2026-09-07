import { afterEach, describe, expect, it, vi } from "vitest";
import { searchMcps, type SearchResponse } from "./api";
import { clearSearchCache, fetchCachedSearch, invalidateCachedSearch } from "./search-cache";

vi.mock("./api", () => ({
  searchMcps: vi.fn(),
}));

const response = {
  requirement: "Read GitHub issues",
  page: 1,
  pageSize: 10,
} as SearchResponse;

afterEach(() => {
  clearSearchCache();
  vi.clearAllMocks();
});

describe("search cache", () => {
  it("deduplicates pending requests and reuses successful results", async () => {
    vi.mocked(searchMcps).mockResolvedValue(response);

    const first = fetchCachedSearch("Read GitHub issues", 1, 10);
    const duplicate = fetchCachedSearch("Read GitHub issues", 1, 10);

    expect(duplicate).toBe(first);
    await expect(first).resolves.toBe(response);
    await expect(fetchCachedSearch("Read GitHub issues", 1, 10)).resolves.toBe(response);
    expect(searchMcps).toHaveBeenCalledTimes(1);
  });

  it("requests fresh results after explicit invalidation", async () => {
    vi.mocked(searchMcps).mockResolvedValue(response);
    await fetchCachedSearch("Read GitHub issues", 1, 10);

    invalidateCachedSearch("Read GitHub issues", 1, 10);
    await fetchCachedSearch("Read GitHub issues", 1, 10);

    expect(searchMcps).toHaveBeenCalledTimes(2);
  });
});
