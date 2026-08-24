# Generated TypeScript MCP server

This project uses the approved MCP Compass contract in `contract.json` as runtime data. The versioned runtime sources register those reviewed tools without embedding contract text in TypeScript source.

It is ready to initialize as a Git repository or push to GitHub. The checked-in workflow runs the locked install,
TypeScript build, and mocked-network unit tests for pushes and pull requests.

## Configure

Copy `.env.example` into your secret manager or runtime environment and set `API_BASE_URL`. Set `API_AUTH_TOKEN` when the approved contract declares authentication, and review the generic bearer-token mapping for your API. Never commit real credentials.

## Build and run

```bash
npm ci --ignore-scripts
npm test
npm start
```

`npm test` compiles the project and exercises request construction with a mocked `fetch`; it does not contact the
upstream API or start the MCP server. The server uses stdio, so stdout is reserved for MCP protocol messages. Review
the generated project before running it. MCP Compass does not execute this project during generation.
