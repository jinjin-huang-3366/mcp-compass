#!/usr/bin/env node

import { pathToFileURL } from "node:url";
import { CliError } from "./errors.js";

type CliCommand = {
  run(args: string[]): Promise<void>;
};

const USAGE = `Usage: mcp-compass <command> [options]

Commands:
  find       Find ranked MCP servers for a capability requirement
  generate   Generate a reviewed MCP project from an OpenAPI document
  ide        Install VS Code and/or IntelliJ project actions

Run "mcp-compass <command> --help" for command-specific help.`;

export async function main(args = process.argv.slice(2)): Promise<number> {
  const [command, ...commandArgs] = args;
  if (!command || command === "--help" || command === "-h") {
    console.log(USAGE);
    return 0;
  }
  if (!/^[a-z][a-z0-9-]*$/.test(command)) {
    console.error(`Invalid command: ${command}`);
    return 2;
  }

  try {
    const module = (await import(`./commands/${command}.js`)) as Partial<CliCommand>;
    if (typeof module.run !== "function") {
      throw new CliError(`Command is not executable: ${command}`);
    }
    await module.run(commandArgs);
    return 0;
  } catch (error) {
    if (isMissingCommand(error, command)) {
      console.error(`Unknown command: ${command}`);
      return 2;
    }
    if (error instanceof CliError) {
      console.error(error.message);
      return error.exitCode;
    }
    throw error;
  }
}

function isMissingCommand(error: unknown, command: string): boolean {
  return error instanceof Error
    && "code" in error
    && error.code === "ERR_MODULE_NOT_FOUND"
    && error.message.replaceAll("\\", "/").includes(`/commands/${command}.js`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  process.exitCode = await main();
}
