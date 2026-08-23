export type ApiOperation = {
  method: string;
  path: string;
  authenticationRequired: boolean;
};

function requiredEnvironment(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`Missing required environment variable ${name}`);
  return value;
}

function queryValue(value: unknown): string {
  return typeof value === "string" ? value : JSON.stringify(value);
}

export async function callApi(operation: ApiOperation, input: Record<string, unknown>): Promise<unknown> {
  const baseUrl = requiredEnvironment("API_BASE_URL");
  const remaining = { ...input };
  const resolvedPath = operation.path.replace(/\{([^}]+)}/g, (_match, name: string) => {
    const value = remaining[name];
    if (value === undefined || value === null) throw new Error(`Missing path parameter ${name}`);
    delete remaining[name];
    return encodeURIComponent(String(value));
  });
  const body = remaining.body;
  delete remaining.body;
  const url = new URL(resolvedPath, baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`);
  for (const [name, value] of Object.entries(remaining)) {
    if (value !== undefined && value !== null) url.searchParams.set(name, queryValue(value));
  }

  const headers: Record<string, string> = { Accept: "application/json" };
  if (operation.authenticationRequired) {
    headers.Authorization = `Bearer ${requiredEnvironment("API_AUTH_TOKEN")}`;
  }
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const response = await fetch(url, {
    method: operation.method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const payload: unknown = text ? parseResponse(text, response.headers.get("content-type")) : null;
  if (!response.ok) throw new Error(`Upstream API returned ${response.status}: ${text.slice(0, 500)}`);
  return payload;
}

function parseResponse(text: string, contentType: string | null): unknown {
  if (contentType?.includes("json")) return JSON.parse(text);
  return text;
}
