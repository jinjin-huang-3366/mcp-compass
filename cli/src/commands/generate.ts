import { basename, resolve } from "node:path";
import { readFile, writeFile } from "node:fs/promises";
import { createInterface } from "node:readline/promises";
import { stdin, stdout } from "node:process";
import { CliError } from "../errors.js";
import { Fetch, requestJson, resolveApiUrl } from "../http.js";

type ToolRisk = "READ_ONLY" | "MUTATING" | "DESTRUCTIVE";

type McpToolContract = {
  contractVersion: string;
  status: "PROPOSED" | "APPROVED";
  source: { title: string };
  tools: Array<{
    name: string;
    description: string;
    risk: ToolRisk;
    sourceOperation: { method: string; path: string };
  }>;
};

type Dependencies = {
  fetch: Fetch;
  readSource(path: string): Promise<Uint8Array>;
  writeArchive(path: string, content: Uint8Array): Promise<void>;
  confirm(question: string): Promise<boolean>;
  log(message: string): void;
};

type GenerateOptions = {
  sourcePath: string;
  apiUrl?: string;
  outputPath?: string;
  yes: boolean;
};

const HELP = `Usage: mcp-compass generate <openapi-file> [options]

Propose a tool contract from a local OpenAPI document, show every proposed
tool and its risk, request approval, and download a GitHub-ready TypeScript ZIP.

Options:
  --api-url <url>   MCP Compass backend (default: MCP_COMPASS_API_URL or http://localhost:8080)
  -o, --output <file>  ZIP destination (default: server-provided filename)
  -y, --yes         Approve all proposed tools without prompting
  -h, --help        Show this help`;

const dependencies: Dependencies = {
  fetch,
  readSource: readFile,
  async writeArchive(path, content) {
    try {
      await writeFile(path, content, { flag: "wx" });
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code === "EEXIST") {
        throw new CliError(`Refusing to overwrite existing file: ${path}`, 2);
      }
      const detail = error instanceof Error ? error.message : String(error);
      throw new CliError(`Could not write generated project to ${path}: ${detail}`);
    }
  },
  async confirm(question) {
    const prompt = createInterface({ input: stdin, output: stdout });
    try {
      return (await prompt.question(question)).trim().toLowerCase() === "y";
    } finally {
      prompt.close();
    }
  },
  log: console.log,
};

export async function run(args: string[]): Promise<void> {
  if (args.includes("--help") || args.includes("-h")) {
    console.log(HELP);
    return;
  }
  await generate(args, dependencies);
}

export async function generate(args: string[], deps: Dependencies): Promise<void> {
  const options = parseOptions(args);
  const apiUrl = resolveApiUrl(options.apiUrl);
  let source: Uint8Array;
  try {
    source = await deps.readSource(options.sourcePath);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new CliError(`Could not read OpenAPI file ${options.sourcePath}: ${detail}`, 2);
  }
  if (source.byteLength === 0) {
    throw new CliError(`OpenAPI file is empty: ${options.sourcePath}`, 2);
  }

  const form = new FormData();
  form.append("file", new Blob([new Uint8Array(source)]), basename(options.sourcePath));
  const proposal = await requestJson<McpToolContract>(
    apiUrl,
    "/api/v1/generation/contracts/openapi",
    { method: "POST", body: form },
    deps.fetch,
  );

  deps.log(`Proposed ${proposal.tools.length} tool${proposal.tools.length === 1 ? "" : "s"} for ${proposal.source.title}:`);
  proposal.tools.forEach((tool) => {
    deps.log(`- ${tool.name} [${tool.risk}] ${tool.sourceOperation.method} ${tool.sourceOperation.path}`);
  });

  if (!options.yes && !await deps.confirm("Approve all proposed tools and generate the project? [y/N] ")) {
    throw new CliError("Generation cancelled; no contract was approved or project exported.", 2);
  }

  const approved = await requestJson<McpToolContract>(
    apiUrl,
    "/api/v1/generation/contracts/review",
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        contract: proposal,
        tools: proposal.tools.map((tool, toolIndex) => ({
          toolIndex,
          selected: true,
          name: tool.name,
          description: tool.description,
        })),
      }),
    },
    deps.fetch,
  );

  const response = await requestArchive(apiUrl, approved, deps.fetch);
  const fileName = archiveFileName(response.headers.get("content-disposition"));
  const outputPath = resolve(options.outputPath ?? fileName);
  await deps.writeArchive(outputPath, new Uint8Array(await response.arrayBuffer()));
  deps.log(`Generated ${approved.tools.length} approved tool${approved.tools.length === 1 ? "" : "s"}: ${outputPath}`);
}

async function requestArchive(apiUrl: string, contract: McpToolContract, fetchImpl: Fetch): Promise<Response> {
  let response: Response;
  try {
    response = await fetchImpl(`${apiUrl}/api/v1/generation/projects/typescript/export`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(contract),
    });
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new CliError(`Could not reach MCP Compass at ${apiUrl}: ${detail}`);
  }
  if (!response.ok) {
    const detail = (await response.text()).trim();
    throw new CliError(`MCP Compass API returned ${response.status} ${response.statusText}${detail ? `: ${detail}` : ""}`);
  }
  return response;
}

function archiveFileName(disposition: string | null): string {
  const match = disposition?.match(/filename="?([^";]+)"?/i)?.[1];
  return match ? basename(match) : "generated-mcp-server.zip";
}

function parseOptions(args: string[]): GenerateOptions {
  let sourcePath: string | undefined;
  let apiUrl: string | undefined;
  let outputPath: string | undefined;
  let yes = false;

  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--yes" || argument === "-y") {
      yes = true;
    } else if (argument === "--api-url") {
      apiUrl = requiredValue(args, ++index, argument);
    } else if (argument?.startsWith("--api-url=")) {
      apiUrl = requiredInlineValue(argument);
    } else if (argument === "--output" || argument === "-o") {
      outputPath = requiredValue(args, ++index, argument);
    } else if (argument?.startsWith("--output=")) {
      outputPath = requiredInlineValue(argument);
    } else if (argument?.startsWith("-")) {
      throw new CliError(`Unknown option: ${argument}`, 2);
    } else if (sourcePath) {
      throw new CliError(`Unexpected argument: ${argument}`, 2);
    } else {
      sourcePath = argument;
    }
  }

  if (!sourcePath) {
    throw new CliError("An OpenAPI file is required. Run mcp-compass generate --help for usage.", 2);
  }
  return { sourcePath, apiUrl, outputPath, yes };
}

function requiredValue(args: string[], index: number, option: string): string {
  const value = args[index];
  if (!value || value.startsWith("-")) {
    throw new CliError(`${option} requires a value`, 2);
  }
  return value;
}

function requiredInlineValue(argument: string): string {
  const value = argument.slice(argument.indexOf("=") + 1);
  if (!value) {
    throw new CliError(`${argument.slice(0, argument.indexOf("="))} requires a value`, 2);
  }
  return value;
}
