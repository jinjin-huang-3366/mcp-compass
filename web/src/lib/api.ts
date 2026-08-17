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
  matches: SearchMatch[];
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export async function searchMcps(requirement: string): Promise<SearchResponse> {
  const response = await fetch(`${API_BASE}/api/v1/mcp/search`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ requirement }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Search API returned ${response.status}${body ? `: ${body}` : ""}`);
  }

  return response.json() as Promise<SearchResponse>;
}
