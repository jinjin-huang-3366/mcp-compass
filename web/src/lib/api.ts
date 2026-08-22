export type SearchMatch = {
  id: string;
  registryName: string;
  title: string | null;
  description: string | null;
  version: string | null;
  status: string | null;
  score: number;
  capabilityCoverage: number | null;
  matchedCapabilities: string[];
  missingCapabilities: string[];
  reasons: string[];
};

export type SearchResponse = {
  requirement: string;
  keywords: string[];
  page: number;
  pageSize: number;
  totalMatches: number;
  totalPages: number;
  matches: SearchMatch[];
};

export type McpServerDetail = {
  id: string;
  registryName: string;
  title: string | null;
  description: string | null;
  version: string | null;
  status: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function searchMcps(
  requirement: string,
  page = 1,
  pageSize = 10,
  signal?: AbortSignal,
): Promise<SearchResponse> {
  const response = await fetch(`${API_BASE}/api/v1/mcp/search`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ requirement, page, pageSize }),
    signal,
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Search API returned ${response.status}${body ? `: ${body}` : ""}`);
  }

  return response.json() as Promise<SearchResponse>;
}

export async function getMcpDetail(id: string): Promise<McpServerDetail | null> {
  const response = await fetch(`${API_BASE}/api/v1/mcp/${encodeURIComponent(id)}`, {
    cache: "no-store",
  });

  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`MCP detail API returned ${response.status}`);
  }

  return response.json() as Promise<McpServerDetail>;
}
