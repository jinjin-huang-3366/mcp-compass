import { readFileSync } from "node:fs";
import { McpServer, fromJsonSchema } from "@modelcontextprotocol/server";
import { serveStdio } from "@modelcontextprotocol/server/stdio";
import { callApi } from "./api-client.js";

type JsonSchema = Record<string, unknown>;
type Risk = "READ_ONLY" | "MUTATING" | "DESTRUCTIVE";

type ApprovedContract = {
  contractVersion: string;
  status: string;
  tools: Array<{
    name: string;
    description: string;
    inputSchema: JsonSchema;
    outputSchema: JsonSchema;
    sourceOperation: { method: string; path: string; operationId: string };
    authenticationRequirements: string[];
    risk: Risk;
  }>;
};

type PackageMetadata = { name: string; version: string };

function readJson<T>(relativePath: string): T {
  return JSON.parse(readFileSync(new URL(relativePath, import.meta.url), "utf8")) as T;
}

const contract = readJson<ApprovedContract>("../contract.json");
const packageMetadata = readJson<PackageMetadata>("../package.json");

if (contract.contractVersion !== "1.0" || contract.status !== "APPROVED") {
  throw new Error("contract.json must contain an APPROVED version 1.0 contract");
}

function createServer(): McpServer {
  const server = new McpServer({ name: packageMetadata.name, version: packageMetadata.version });

  for (const tool of contract.tools) {
    server.registerTool(tool.name, {
      description: tool.description,
      inputSchema: fromJsonSchema(tool.inputSchema),
      outputSchema: fromJsonSchema(tool.outputSchema),
      annotations: {
        readOnlyHint: tool.risk === "READ_ONLY",
        destructiveHint: tool.risk === "DESTRUCTIVE",
        idempotentHint: ["GET", "PUT", "DELETE", "HEAD", "OPTIONS"].includes(tool.sourceOperation.method),
        openWorldHint: true,
      },
    }, async (input) => {
      try {
        const result = await callApi({
          method: tool.sourceOperation.method,
          path: tool.sourceOperation.path,
          authenticationRequired: tool.authenticationRequirements.length > 0,
        }, input as Record<string, unknown>);
        return { content: [{ type: "text", text: JSON.stringify(result) }] };
      } catch (error) {
        const message = error instanceof Error ? error.message : "Unknown API error";
        return { content: [{ type: "text", text: message }], isError: true };
      }
    });
  }

  return server;
}

void serveStdio(createServer);
console.error(`${packageMetadata.name} running on stdio`);
