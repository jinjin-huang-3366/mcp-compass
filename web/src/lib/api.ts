export type SearchMatch = {
  id: string;
  registryName: string;
  title: string | null;
  description: string | null;
  version: string | null;
  status: string | null;
  repositoryUrl: string | null;
  score: number;
  qualityScore: number;
  capabilityCoverage: number | null;
  matchedCapabilities: string[];
  missingCapabilities: string[];
  rankingExplanation: RankingExplanation;
  reasons: string[];
};

export type RankingExplanation = {
  contributions: RankingFeatureContribution[];
  preAdjustmentScore: number;
  statusMultiplier: number;
};

export type RankingFeatureContribution = {
  feature: "capabilityCoverage" | "retrievalRelevance" | "quality";
  featureScore: number;
  weight: number;
  contribution: number;
};

export type SearchResponse = {
  requirement: string;
  keywords: string[];
  parsedIntent: ParsedIntent;
  strongMatch: boolean;
  confidenceThreshold: number;
  abstentionReasons: string[];
  page: number;
  pageSize: number;
  totalMatches: number;
  totalPages: number;
  totalExcluded: number;
  exclusions: SearchExclusion[];
  matches: SearchMatch[];
};

export type ParsedIntent = {
  domain: string;
  service: string;
  requiredCapabilities: string[];
  forbiddenCapabilities: string[];
  constraints: RequirementConstraint[];
};

export type RequirementConstraint = {
  name: string;
  operator: "EQUALS" | "NOT_EQUALS" | "CONTAINS" | "AT_LEAST" | "AT_MOST";
  value: string;
};

export type SearchExclusion = {
  id: string;
  registryName: string;
  title: string | null;
  reasons: string[];
};

export type McpServerDetail = {
  id: string;
  registryName: string;
  title: string | null;
  description: string | null;
  version: string | null;
  status: string | null;
  repositoryUrl: string | null;
  firstSeenAt: string;
  lastSeenAt: string;
};

export type McpToolContract = {
  contractVersion: string;
  status: "PROPOSED" | "APPROVED";
  source: {
    type: "FILE" | "URL";
    location: string;
    openApiVersion: string;
    title: string;
    apiVersion: string;
  };
  tools: McpTool[];
};

export type McpTool = {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  outputSchema: Record<string, unknown>;
  sourceOperation: {
    method: string;
    path: string;
    operationId: string | null;
  };
  authenticationRequirements: string[];
  risk: "READ_ONLY" | "MUTATING" | "DESTRUCTIVE";
};

export type McpToolReview = {
  toolIndex: number;
  selected: boolean;
  name: string;
  description: string;
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

export async function proposeOpenApiContract(file: File): Promise<McpToolContract> {
  const form = new FormData();
  form.append("file", file);
  const response = await fetch(`${API_BASE}/api/v1/generation/contracts/openapi`, {
    method: "POST",
    body: form,
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Contract proposal returned ${response.status}${body ? `: ${body}` : ""}`);
  }
  return response.json() as Promise<McpToolContract>;
}

export async function approveMcpToolContract(
  contract: McpToolContract,
  tools: McpToolReview[],
): Promise<McpToolContract> {
  const response = await fetch(`${API_BASE}/api/v1/generation/contracts/review`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ contract, tools }),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Contract review returned ${response.status}${body ? `: ${body}` : ""}`);
  }
  return response.json() as Promise<McpToolContract>;
}

export async function exportTypeScriptMcpProject(
  contract: McpToolContract,
): Promise<{ archive: Blob; fileName: string }> {
  const response = await fetch(`${API_BASE}/api/v1/generation/projects/typescript/export`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(contract),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Project export returned ${response.status}${body ? `: ${body}` : ""}`);
  }
  const disposition = response.headers.get("Content-Disposition") ?? "";
  const fileName = disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? "generated-mcp-server.zip";
  return { archive: await response.blob(), fileName };
}
