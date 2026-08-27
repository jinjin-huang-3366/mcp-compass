# Use MCP Compass from VS Code or IntelliJ

MCP Compass installs project-scoped IDE actions that delegate to the same reviewed CLI workflows used in a terminal.
The IDE configuration contains no backend scoring or generation logic and does not execute MCP servers.

First build and link the CLI, then install integrations in the project where you work with agent capabilities or
OpenAPI documents:

```bash
cd cli
npm ci
npm run build
npm link
cd ../my-agent-project
mcp-compass ide
```

The default command creates both `.vscode/tasks.json` and `.idea/tools/External Tools.xml`. Select one editor or a
different project directory with `--ide vscode`, `--ide intellij`, or `--directory <path>`. Existing configuration
files are never overwritten; merge the generated task/tool definitions manually if a target file already exists.

Set `MCP_COMPASS_API_URL` before starting the IDE when the backend is not available at `http://localhost:8080`.

## VS Code

Open **Terminal > Run Task** and choose:

- **MCP Compass: Find server** prompts for the capability requirement and shows ranked results in the task terminal.
- **MCP Compass: Generate from active OpenAPI file** sends the active file through contract proposal, shows tool risk,
  asks for approval, and downloads the generated ZIP.

For example, choosing **MCP Compass: Find server** and entering `read and comment on GitHub issues` runs the equivalent
of `mcp-compass find "read and comment on GitHub issues"` without leaving VS Code.

## IntelliJ

Restart or reopen the project after installation, then use **Tools > External Tools**:

- **MCP Compass Find** opens a requirement prompt and displays ranked results in the Run console.
- **MCP Compass Generate** uses the active editor file as the OpenAPI input and keeps CLI approval interactive.

Both editors therefore preserve the CLI's reuse-before-generate and contract-review boundaries.
