import assert from "node:assert/strict";
import { test } from "node:test";
import { generate } from "../src/commands/generate.js";

const proposal = {
  contractVersion: "1.0",
  status: "PROPOSED" as const,
  source: { title: "Pet Store" },
  tools: [{
    name: "list_pets",
    description: "List pets",
    risk: "READ_ONLY" as const,
    sourceOperation: { method: "GET", path: "/pets" },
  }],
};

test("reviews the proposed contract before exporting the generated ZIP", async () => {
  const requests: Array<{ url: string; init?: RequestInit }> = [];
  const logs: string[] = [];
  let written: { path: string; content: Uint8Array } | undefined;
  const fetchMock: typeof fetch = async (input, init) => {
    const url = String(input);
    requests.push({ url, init });
    if (url.endsWith("/contracts/openapi")) {
      assert.ok(init?.body instanceof FormData);
      assert.equal((init.body.get("file") as File).name, "petstore.yaml");
      return Response.json(proposal);
    }
    if (url.endsWith("/contracts/review")) {
      const body = JSON.parse(String(init?.body)) as { tools: Array<Record<string, unknown>> };
      assert.deepEqual(body.tools, [{
        toolIndex: 0,
        selected: true,
        name: "list_pets",
        description: "List pets",
      }]);
      return Response.json({ ...proposal, status: "APPROVED" });
    }
    return new Response(new Uint8Array([80, 75, 3, 4]), {
      headers: { "Content-Disposition": "attachment; filename=\"pet-store-mcp-server.zip\"" },
    });
  };

  await generate(["petstore.yaml", "--yes"], {
    fetch: fetchMock,
    readSource: async () => Buffer.from("openapi: 3.1.0"),
    writeArchive: async (path, content) => { written = { path, content }; },
    confirm: async () => { throw new Error("--yes must not prompt"); },
    log: (message) => logs.push(message),
  });

  assert.deepEqual(requests.map(({ url }) => url), [
    "http://localhost:8080/api/v1/generation/contracts/openapi",
    "http://localhost:8080/api/v1/generation/contracts/review",
    "http://localhost:8080/api/v1/generation/projects/typescript/export",
  ]);
  assert.equal(written?.path.endsWith("pet-store-mcp-server.zip"), true);
  assert.deepEqual(written?.content, new Uint8Array([80, 75, 3, 4]));
  assert.ok(logs.some((line) => line.includes("list_pets [READ_ONLY] GET /pets")));
  assert.ok(logs.some((line) => line.includes("Generated 1 approved tool")));
});

test("does not approve or export when interactive review is declined", async () => {
  let requestCount = 0;
  await assert.rejects(
    generate(["petstore.yaml"], {
      fetch: async () => {
        requestCount += 1;
        return Response.json(proposal);
      },
      readSource: async () => Buffer.from("openapi: 3.1.0"),
      writeArchive: async () => { throw new Error("must not write"); },
      confirm: async () => false,
      log: () => undefined,
    }),
    /Generation cancelled; no contract was approved or project exported\./,
  );
  assert.equal(requestCount, 1);
});

test("requires one local OpenAPI path", async () => {
  await assert.rejects(
    generate([], {
      fetch,
      readSource: async () => new Uint8Array(),
      writeArchive: async () => undefined,
      confirm: async () => false,
      log: () => undefined,
    }),
    /An OpenAPI file is required/,
  );
});

test("uses explicit API and output options", async () => {
  const urls: string[] = [];
  let output = "";
  await generate([
    "petstore.yaml",
    "--yes",
    "--api-url", "https://compass.example.test/",
    "--output", "custom.zip",
  ], {
    fetch: async (input) => {
      const url = String(input);
      urls.push(url);
      if (url.endsWith("/contracts/openapi")) return Response.json(proposal);
      if (url.endsWith("/contracts/review")) return Response.json({ ...proposal, status: "APPROVED" });
      return new Response(new Uint8Array([1]));
    },
    readSource: async () => Buffer.from("openapi: 3.1.0"),
    writeArchive: async (path) => { output = path; },
    confirm: async () => false,
    log: () => undefined,
  });

  assert.ok(urls.every((url) => url.startsWith("https://compass.example.test/api/v1/")));
  assert.equal(output.endsWith("custom.zip"), true);
});
