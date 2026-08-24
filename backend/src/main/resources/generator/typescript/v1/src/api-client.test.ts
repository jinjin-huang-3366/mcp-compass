import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import { callApi } from "./api-client.js";

const originalFetch = globalThis.fetch;
const originalBaseUrl = process.env.API_BASE_URL;
const originalAuthToken = process.env.API_AUTH_TOKEN;

afterEach(() => {
  globalThis.fetch = originalFetch;
  restoreEnvironment("API_BASE_URL", originalBaseUrl);
  restoreEnvironment("API_AUTH_TOKEN", originalAuthToken);
});

test("encodes path and query values and sends configured authentication", async () => {
  process.env.API_BASE_URL = "https://api.example.com/v1/";
  process.env.API_AUTH_TOKEN = "test-token";
  let requestedUrl = "";
  let requestedInit: RequestInit | undefined;
  globalThis.fetch = async (input, init) => {
    requestedUrl = String(input);
    requestedInit = init;
    return new Response(JSON.stringify({ name: "Milo" }), {
      status: 200,
      headers: { "content-type": "application/json" },
    });
  };

  const result = await callApi({
    method: "GET",
    path: "/pets/{petId}",
    authenticationRequired: true,
  }, { petId: "a/b", page: 2 });

  assert.deepEqual(result, { name: "Milo" });
  assert.equal(requestedUrl, "https://api.example.com/pets/a%2Fb?page=2");
  assert.equal(requestedInit?.method, "GET");
  assert.equal(new Headers(requestedInit?.headers).get("authorization"), "Bearer test-token");
  assert.equal(requestedInit?.body, undefined);
});

test("serializes the reserved body argument without adding it to the query", async () => {
  process.env.API_BASE_URL = "https://api.example.com";
  let requestedUrl = "";
  let requestedInit: RequestInit | undefined;
  globalThis.fetch = async (input, init) => {
    requestedUrl = String(input);
    requestedInit = init;
    return new Response(null, { status: 204 });
  };

  const result = await callApi({
    method: "POST",
    path: "/pets",
    authenticationRequired: false,
  }, { body: { name: "Milo" } });

  assert.equal(result, null);
  assert.equal(requestedUrl, "https://api.example.com/pets");
  assert.equal(requestedInit?.body, JSON.stringify({ name: "Milo" }));
  assert.equal(new Headers(requestedInit?.headers).get("content-type"), "application/json");
});

test("rejects a call when a declared path value is missing", async () => {
  process.env.API_BASE_URL = "https://api.example.com";

  await assert.rejects(
    callApi({
      method: "GET",
      path: "/pets/{petId}",
      authenticationRequired: false,
    }, {}),
    /Missing path parameter petId/,
  );
});

function restoreEnvironment(name: string, value: string | undefined): void {
  if (value === undefined) {
    delete process.env[name];
  } else {
    process.env[name] = value;
  }
}
