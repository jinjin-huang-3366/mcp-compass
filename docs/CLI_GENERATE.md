# Generate an MCP project from the CLI

The Node.js 22 CLI exposes the existing contract-first backend workflow for local OpenAPI files. Build it, start the
backend, and run:

```bash
cd cli
npm ci
npm run build
npm link
mcp-compass generate ../petstore.yaml
```

The command uploads the document to the contract proposal endpoint and prints every proposed tool with its source
HTTP operation and conservative risk. It does not generate code until the developer explicitly approves all tools:

```text
Proposed 2 tools for Pet Store:
- list_pets [READ_ONLY] GET /pets
- create_pet [MUTATING] POST /pets
Approve all proposed tools and generate the project? [y/N] y
Generated 2 approved tools: C:\work\pet-store-mcp-server.zip
```

The downloaded ZIP is the same GitHub-ready, locked TypeScript project returned by
`POST /api/v1/generation/projects/typescript/export`. The CLI does not generate code locally or execute the generated
server. Use `--yes` for a non-interactive approval, `--output <file>` to choose the ZIP path, and `--api-url <url>` or
`MCP_COMPASS_API_URL` when the backend is not at `http://localhost:8080`. Existing output files are never overwritten.
