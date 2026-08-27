import { CliError } from "../errors.js";
import { requestJson, resolveApiUrl, type Fetch } from "../http.js";

type SearchResponse = {
  requirement: string;
  page: number;
  pageSize: number;
  totalMatches: number;
  totalPages: number;
  matches: SearchMatch[];
};

type SearchMatch = {
  id: string;
  registryName: string;
  title: string | null;
  version: string | null;
  status: string;
  score: number;
  qualityScore: number;
  capabilityCoverage: number | null;
  matchedCapabilities: string[];
  missingCapabilities: string[];
  reasons: string[];
};

type FindOptions = {
  requirement: string;
  apiUrl: string;
  page: number;
  pageSize: number;
  json: boolean;
};

type FindDependencies = {
  fetchImpl?: Fetch;
  write?: (text: string) => void;
};

const HELP = `Usage: mcp-compass find <requirement> [options]

Find ranked MCP servers using MCP Compass's locally persisted Registry data.

Options:
  --api-url <url>   MCP Compass API URL (default: MCP_COMPASS_API_URL or http://localhost:8080)
  --page <number>   Result page, starting at 1 (default: 1)
  --page-size <n>   Results per page, from 1 to 25 (default: 10)
  --json            Print the API response as JSON
  -h, --help        Show this help`;

export async function run(args: string[]): Promise<void> {
  if (args.includes("--help") || args.includes("-h")) {
    console.log(HELP);
    return;
  }
  await runFind(parseArgs(args));
}

export async function runFind(options: FindOptions, dependencies: FindDependencies = {}): Promise<void> {
  const response = await requestJson<SearchResponse>(
    options.apiUrl,
    "/api/v1/mcp/search",
    {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        requirement: options.requirement,
        page: options.page,
        pageSize: options.pageSize,
      }),
    },
    dependencies.fetchImpl,
  );
  const output = options.json ? `${JSON.stringify(response, null, 2)}\n` : renderSearch(response);
  (dependencies.write ?? process.stdout.write.bind(process.stdout))(output);
}

export function parseArgs(args: string[]): FindOptions {
  let apiUrl: string | undefined;
  let page = 1;
  let pageSize = 10;
  let json = false;
  const requirementParts: string[] = [];

  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--json") {
      json = true;
    } else if (argument === "--api-url") {
      apiUrl = requiredValue(args, ++index, argument);
    } else if (argument === "--page") {
      page = integerValue(requiredValue(args, ++index, argument), argument, 1, 1000);
    } else if (argument === "--page-size") {
      pageSize = integerValue(requiredValue(args, ++index, argument), argument, 1, 25);
    } else if (argument?.startsWith("-")) {
      throw new CliError(`Unknown option: ${argument}`, 2);
    } else if (argument) {
      requirementParts.push(argument);
    }
  }

  const requirement = requirementParts.join(" ").trim();
  if (!requirement) {
    throw new CliError("A capability requirement is required. Run mcp-compass find --help for usage.", 2);
  }
  if (requirement.length > 2000) {
    throw new CliError("The capability requirement must be at most 2000 characters.", 2);
  }

  return { requirement, apiUrl: resolveApiUrl(apiUrl), page, pageSize, json };
}

function requiredValue(args: string[], index: number, option: string): string {
  const value = args[index];
  if (!value || value.startsWith("-")) {
    throw new CliError(`${option} requires a value`, 2);
  }
  return value;
}

function integerValue(value: string, option: string, minimum: number, maximum: number): number {
  if (!/^\d+$/.test(value)) {
    throw new CliError(`${option} must be an integer from ${minimum} to ${maximum}`, 2);
  }
  const parsed = Number(value);
  if (parsed < minimum || parsed > maximum) {
    throw new CliError(`${option} must be an integer from ${minimum} to ${maximum}`, 2);
  }
  return parsed;
}

function renderSearch(response: SearchResponse): string {
  const lines = [
    `Found ${response.totalMatches} MCP server${response.totalMatches === 1 ? "" : "s"} for "${response.requirement}" (page ${response.page}/${Math.max(response.totalPages, 1)})`,
  ];
  if (response.matches.length === 0) {
    lines.push("", "No matches on this page.");
    return `${lines.join("\n")}\n`;
  }

  response.matches.forEach((match, index) => {
    const title = match.title?.trim() || match.registryName;
    const version = match.version ? ` @ ${match.version}` : "";
    lines.push("", `${(response.page - 1) * response.pageSize + index + 1}. ${title}${version}`);
    lines.push(`   ${match.registryName} | score ${percent(match.score)} | quality ${percent(match.qualityScore)} | ${match.status}`);
    if (match.capabilityCoverage !== null) {
      lines.push(`   Capability coverage: ${percent(match.capabilityCoverage)}`);
    }
    if (match.matchedCapabilities.length > 0) {
      lines.push(`   Covers: ${match.matchedCapabilities.join(", ")}`);
    }
    if (match.missingCapabilities.length > 0) {
      lines.push(`   Missing: ${match.missingCapabilities.join(", ")}`);
    }
    if (match.reasons.length > 0) {
      lines.push(`   Why: ${match.reasons.join("; ")}`);
    }
    lines.push(`   ID: ${match.id}`);
  });
  return `${lines.join("\n")}\n`;
}

function percent(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}
