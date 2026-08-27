import assert from "node:assert/strict";
import { test } from "node:test";
import { main } from "../src/index.js";

test("dispatcher rejects an unknown command with a usage exit code", async () => {
  const originalError = console.error;
  const errors: string[] = [];
  console.error = (message?: unknown) => { errors.push(String(message)); };
  try {
    assert.equal(await main(["not-a-command"]), 2);
  } finally {
    console.error = originalError;
  }
  assert.deepEqual(errors, ["Unknown command: not-a-command"]);
});
