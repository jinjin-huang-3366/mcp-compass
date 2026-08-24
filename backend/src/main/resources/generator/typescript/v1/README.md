# Generated TypeScript MCP server

This project uses the approved MCP Compass contract in `contract.json` as runtime data. The versioned runtime sources register those reviewed tools without embedding contract text in TypeScript source.

## Configure

Copy `.env.example` into your secret manager or runtime environment and set `API_BASE_URL`. Set `API_AUTH_TOKEN` when the approved contract declares authentication, and review the generic bearer-token mapping for your API. Never commit real credentials.

## Build and run

```bash
npm install
npm run build
npm start
```

The server uses stdio, so stdout is reserved for MCP protocol messages. Review and compile the generated project before running it. MCP Compass does not execute this project during generation.
