import { searchMcps, type SearchResponse } from "./api";

const CACHE_TTL_MS = 5 * 60 * 1000;
const MAX_CACHE_ENTRIES = 20;

type CacheEntry = {
  cachedAt: number;
  response: SearchResponse;
};

const resultCache = new Map<string, CacheEntry>();
const pendingSearches = new Map<string, Promise<SearchResponse>>();

export function getCachedSearch(requirement: string, page: number, pageSize: number) {
  const key = cacheKey(requirement, page, pageSize);
  const entry = resultCache.get(key);
  if (!entry) {
    return null;
  }
  if (Date.now() - entry.cachedAt >= CACHE_TTL_MS) {
    resultCache.delete(key);
    return null;
  }

  resultCache.delete(key);
  resultCache.set(key, entry);
  return entry.response;
}

export function fetchCachedSearch(requirement: string, page: number, pageSize: number) {
  const normalizedRequirement = requirement.trim();
  const key = cacheKey(normalizedRequirement, page, pageSize);
  const cached = getCachedSearch(normalizedRequirement, page, pageSize);
  if (cached) {
    return Promise.resolve(cached);
  }

  const pending = pendingSearches.get(key);
  if (pending) {
    return pending;
  }

  const request = searchMcps(normalizedRequirement, page, pageSize)
    .then((response) => {
      storeResult(key, response);
      return response;
    })
    .finally(() => {
      if (pendingSearches.get(key) === request) {
        pendingSearches.delete(key);
      }
    });
  pendingSearches.set(key, request);
  return request;
}

export function invalidateCachedSearch(requirement: string, page: number, pageSize: number) {
  resultCache.delete(cacheKey(requirement, page, pageSize));
}

export function clearSearchCache() {
  resultCache.clear();
  pendingSearches.clear();
}

function storeResult(key: string, response: SearchResponse) {
  resultCache.delete(key);
  resultCache.set(key, { cachedAt: Date.now(), response });
  while (resultCache.size > MAX_CACHE_ENTRIES) {
    const oldestKey = resultCache.keys().next().value;
    if (oldestKey === undefined) {
      return;
    }
    resultCache.delete(oldestKey);
  }
}

function cacheKey(requirement: string, page: number, pageSize: number) {
  return JSON.stringify([requirement.trim(), page, pageSize]);
}
