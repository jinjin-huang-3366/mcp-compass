# Find MCP servers from the CLI

The Node.js 22 CLI calls the MCP Compass search API, which ranks servers from locally persisted Registry data. It
does not contact the public Registry or execute an MCP server.

Build and link the command locally:

```bash
cd cli
npm ci
npm run build
npm link
```

With the backend running, describe the capability your agent needs:

```bash
mcp-compass find "read and comment on GitHub issues"
```

The default output includes score, quality, capability coverage, matched and missing capabilities, ranking reasons,
and the server ID used by `GET /api/v1/mcp/{id}`:

```text
Found 1 MCP server for "read and comment on GitHub issues" (page 1/1)

1. GitHub Issues MCP @ 1.2.0
   io.github.example/issues | score 87.6% | quality 75.0% | ACTIVE
   Capability coverage: 50.0%
   Covers: github.issue.read
   Missing: github.issue.comment
   Why: matches github issues; active Registry status
   ID: 0d5844e5-32e9-4977-a2d8-dcd8f6f667f4
```

Use `--json` for automation, `--page` and `--page-size` for pagination, and `--api-url` to select a backend. The API
URL defaults to `MCP_COMPASS_API_URL` and then `http://localhost:8080`:

```bash
mcp-compass find "read documentation" --page-size 5 --json
```

Run `mcp-compass find --help` for the complete option list.
