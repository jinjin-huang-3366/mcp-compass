import { access, mkdir, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";
import { CliError } from "../errors.js";

type Ide = "vscode" | "intellij";

type IdeOptions = {
  directory: string;
  ides: Ide[];
};

type IdeDependencies = {
  exists(path: string): Promise<boolean>;
  write(path: string, content: string): Promise<void>;
  log(message: string): void;
};

const HELP = `Usage: mcp-compass ide [options]

Install project-scoped VS Code tasks and/or IntelliJ External Tools that run
the MCP Compass find and generate CLI workflows.

Options:
  --ide <name>          vscode, intellij, or all (default: all)
  --directory <path>   Project directory to configure (default: current directory)
  -h, --help           Show this help`;

const defaultDependencies: IdeDependencies = {
  async exists(path) {
    try {
      await access(path);
      return true;
    } catch {
      return false;
    }
  },
  async write(path, content) {
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, content, { encoding: "utf8", flag: "wx" });
  },
  log: console.log,
};

export async function run(args: string[]): Promise<void> {
  if (args.includes("--help") || args.includes("-h")) {
    console.log(HELP);
    return;
  }
  await installIdeIntegration(parseOptions(args), defaultDependencies);
}

export async function installIdeIntegration(options: IdeOptions, dependencies: IdeDependencies): Promise<void> {
  const files = options.ides.map((ide) => integrationFile(ide, options.directory));
  const existing = await Promise.all(files.map(async (file) => await dependencies.exists(file.path) ? file.path : null));
  const collision = existing.find((path) => path !== null);
  if (collision) {
    throw new CliError(`Refusing to overwrite existing IDE configuration: ${collision}`, 2);
  }

  try {
    for (const file of files) {
      await dependencies.write(file.path, file.content);
      dependencies.log(`Installed ${file.ide} integration: ${file.path}`);
    }
  } catch (error) {
    if (error instanceof CliError) {
      throw error;
    }
    const detail = error instanceof Error ? error.message : String(error);
    throw new CliError(`Could not install IDE integration: ${detail}`, 2);
  }
}

export function parseOptions(args: string[]): IdeOptions {
  let directory = process.cwd();
  let ides: Ide[] = ["vscode", "intellij"];

  for (let index = 0; index < args.length; index += 1) {
    const argument = args[index];
    if (argument === "--ide") {
      ides = parseIde(requiredValue(args, ++index, argument));
    } else if (argument?.startsWith("--ide=")) {
      ides = parseIde(requiredInlineValue(argument));
    } else if (argument === "--directory") {
      directory = requiredValue(args, ++index, argument);
    } else if (argument?.startsWith("--directory=")) {
      directory = requiredInlineValue(argument);
    } else {
      throw new CliError(`Unknown option: ${argument}`, 2);
    }
  }
  return { directory: resolve(directory), ides };
}

function integrationFile(ide: Ide, directory: string): { ide: string; path: string; content: string } {
  if (ide === "vscode") {
    return {
      ide: "VS Code",
      path: resolve(directory, ".vscode", "tasks.json"),
      content: `${JSON.stringify({
        version: "2.0.0",
        tasks: [
          {
            label: "MCP Compass: Find server",
            type: "process",
            command: "mcp-compass",
            args: ["find", "${input:mcpCompassRequirement}"],
            problemMatcher: [],
          },
          {
            label: "MCP Compass: Generate from active OpenAPI file",
            type: "process",
            command: "mcp-compass",
            args: ["generate", "${file}"],
            problemMatcher: [],
          },
        ],
        inputs: [
          {
            id: "mcpCompassRequirement",
            type: "promptString",
            description: "What capability does your agent need?",
          },
        ],
      }, null, 2)}\n`,
    };
  }
  return {
    ide: "IntelliJ",
    path: resolve(directory, ".idea", "tools", "External Tools.xml"),
    content: `<?xml version="1.0" encoding="UTF-8"?>
<toolSet name="External Tools">
  <tool name="MCP Compass Find" description="Find ranked MCP servers" showInMainMenu="true" showInEditor="true" showInProject="true" showInSearchPopup="true" disabled="false" useConsole="true" showConsoleOnStdOut="true" showConsoleOnStdErr="true" synchronizeAfterRun="true">
    <exec>
      <option name="COMMAND" value="mcp-compass" />
      <option name="PARAMETERS" value="find &quot;$Prompt$&quot;" />
      <option name="WORKING_DIRECTORY" value="$ProjectFileDir$" />
    </exec>
  </tool>
  <tool name="MCP Compass Generate" description="Generate an MCP project from the active OpenAPI file" showInMainMenu="true" showInEditor="true" showInProject="true" showInSearchPopup="true" disabled="false" useConsole="true" showConsoleOnStdOut="true" showConsoleOnStdErr="true" synchronizeAfterRun="true">
    <exec>
      <option name="COMMAND" value="mcp-compass" />
      <option name="PARAMETERS" value="generate &quot;$FilePath$&quot;" />
      <option name="WORKING_DIRECTORY" value="$ProjectFileDir$" />
    </exec>
  </tool>
</toolSet>
`,
  };
}

function parseIde(value: string): Ide[] {
  if (value === "all") {
    return ["vscode", "intellij"];
  }
  if (value === "vscode" || value === "intellij") {
    return [value];
  }
  throw new CliError("--ide must be vscode, intellij, or all", 2);
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
