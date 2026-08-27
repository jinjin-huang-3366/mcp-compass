import assert from "node:assert/strict";
import { test } from "node:test";
import { CliError } from "../src/errors.js";
import { parseArgs, runFind } from "../src/commands/find.js";

test("find posts the requirement and renders actionable ranking evidence", async () => {
  let requestUrl = "";
  let requestBody = "";
  let output = "";
  const fetchImpl: typeof fetch = async (input, init) => {
    requestUrl = String(input);
    requestBody = String(init?.body);
    return Response.json({
      requirement: "manage GitHub issues",
      page: 1,
      pageSize: 5,
      totalMatches: 1,
      totalPages: 1,
      matches: [{
        id: "0d5844e5-32e9-4977-a2d8-dcd8f6f667f4",
        registryName: "io.github.example/issues",
        title: "GitHub Issues MCP",
        version: "1.2.0",
        status: "ACTIVE",
        score: 0.876,
        qualityScore: 0.75,
        capabilityCoverage: 0.5,
        matchedCapabilities: ["github.issue.read"],
        missingCapabilities: ["github.issue.comment"],
        reasons: ["matches github issues", "active Registry status"],
      }],
    });
  };

  await runFind(
    {
      requirement: "manage GitHub issues",
      apiUrl: "http://localhost:8080",
      page: 1,
      pageSize: 5,
      json: false,
    },
    { fetchImpl, write: (text) => { output += text; } },
  );

  assert.equal(requestUrl, "http://localhost:8080/api/v1/mcp/search");
  assert.deepEqual(JSON.parse(requestBody), {
    requirement: "manage GitHub issues",
    page: 1,
    pageSize: 5,
  });
  assert.match(output, /1\. GitHub Issues MCP @ 1\.2\.0/);
  assert.match(output, /score 87\.6%/);
  assert.match(output, /Covers: github\.issue\.read/);
  assert.match(output, /Missing: github\.issue\.comment/);
});

test("find can emit the unchanged API response as JSON", async () => {
  const response = {
    requirement: "read docs",
    page: 1,
    pageSize: 10,
    totalMatches: 0,
    totalPages: 0,
    matches: [],
  };
  let output = "";

  await runFind(
    { requirement: "read docs", apiUrl: "https://compass.example", page: 1, pageSize: 10, json: true },
    { fetchImpl: async () => Response.json(response), write: (text) => { output += text; } },
  );

  assert.deepEqual(JSON.parse(output), response);
});

test("find validates local arguments before making a request", () => {
  assert.throws(
    () => parseArgs(["search", "issues", "--page-size", "26"]),
    (error) => error instanceof CliError && error.exitCode === 2 && /1 to 25/.test(error.message),
  );
  assert.throws(
    () => parseArgs([]),
    (error) => error instanceof CliError && error.exitCode === 2 && /requirement is required/.test(error.message),
  );
});

test("find reports API errors without hiding the response", async () => {
  await assert.rejects(
    runFind(
      { requirement: "read docs", apiUrl: "http://localhost:8080", page: 1, pageSize: 10, json: false },
      { fetchImpl: async () => new Response("database unavailable", { status: 503, statusText: "Service Unavailable" }) },
    ),
    (error) => error instanceof CliError && /503 Service Unavailable: database unavailable/.test(error.message),
  );
});
