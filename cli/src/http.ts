import { CliError } from "./errors.js";

export const DEFAULT_API_URL = "http://localhost:8080";

export type Fetch = typeof fetch;

export function resolveApiUrl(value?: string): string {
  const candidate = value ?? process.env.MCP_COMPASS_API_URL ?? DEFAULT_API_URL;
  try {
    const url = new URL(candidate);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
      throw new Error("unsupported protocol");
    }
    return url.toString().replace(/\/$/, "");
  } catch {
    throw new CliError(`Invalid MCP Compass API URL: ${candidate}`, 2);
  }
}

export async function requestJson<T>(
  apiUrl: string,
  path: string,
  init: RequestInit,
  fetchImpl: Fetch = fetch,
): Promise<T> {
  let response: Response;
  try {
    response = await fetchImpl(`${resolveApiUrl(apiUrl)}${path}`, init);
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new CliError(`Could not reach MCP Compass at ${apiUrl}: ${detail}`);
  }

  if (!response.ok) {
    const detail = await response.text();
    const suffix = detail.trim() ? `: ${detail.trim()}` : "";
    throw new CliError(`MCP Compass API returned ${response.status} ${response.statusText}${suffix}`);
  }

  try {
    return (await response.json()) as T;
  } catch {
    throw new CliError("MCP Compass API returned an invalid JSON response");
  }
}
