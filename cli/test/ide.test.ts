import assert from "node:assert/strict";
import { test } from "node:test";
import { resolve } from "node:path";
import { CliError } from "../src/errors.js";
import { installIdeIntegration, parseOptions } from "../src/commands/ide.js";

test("defaults to installing both integrations in the current directory", () => {
  assert.deepEqual(parseOptions([]), {
    directory: resolve(process.cwd()),
    ides: ["vscode", "intellij"],
  });
});

test("selects one IDE and resolves a target directory", () => {
  assert.deepEqual(parseOptions(["--ide=vscode", "--directory", "example"]), {
    directory: resolve("example"),
    ides: ["vscode"],
  });
});

test("rejects an unsupported IDE", () => {
  assert.throws(() => parseOptions(["--ide", "vim"]), (error) => {
    assert.ok(error instanceof CliError);
    assert.equal(error.message, "--ide must be vscode, intellij, or all");
    return true;
  });
});

test("writes VS Code and IntelliJ integrations that delegate to the CLI", async () => {
  const writes = new Map<string, string>();
  const logs: string[] = [];
  await installIdeIntegration(
    { directory: resolve("workspace"), ides: ["vscode", "intellij"] },
    {
      exists: async () => false,
      write: async (path, content) => { writes.set(path, content); },
      log: (message) => { logs.push(message); },
    },
  );

  const vscode = writes.get(resolve("workspace", ".vscode", "tasks.json"));
  assert.ok(vscode);
  assert.match(vscode, /"find"/);
  assert.match(vscode, /\$\{input:mcpCompassRequirement\}/);
  assert.match(vscode, /"generate"/);
  assert.match(vscode, /\$\{file\}/);

  const intellij = writes.get(resolve("workspace", ".idea", "tools", "External Tools.xml"));
  assert.ok(intellij);
  assert.match(intellij, /find &quot;\$Prompt\$&quot;/);
  assert.match(intellij, /generate &quot;\$FilePath\$&quot;/);
  assert.equal(logs.length, 2);
});

test("checks all requested files before writing and refuses an overwrite", async () => {
  let writes = 0;
  const existing = resolve("workspace", ".idea", "tools", "External Tools.xml");
  await assert.rejects(
    installIdeIntegration(
      { directory: resolve("workspace"), ides: ["vscode", "intellij"] },
      {
        exists: async (path) => path === existing,
        write: async () => { writes += 1; },
        log: () => undefined,
      },
    ),
    /Refusing to overwrite existing IDE configuration/,
  );
  assert.equal(writes, 0);
});
