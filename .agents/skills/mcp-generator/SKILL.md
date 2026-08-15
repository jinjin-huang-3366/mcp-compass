---
name: mcp-generator
description: Design or implement MCP server generation from OpenAPI/API docs/SDKs, tool-contract planning, generated TypeScript projects, compile-repair loops, or MCP validation for MCP Compass.
---

# MCP generation workflow

This skill is mainly for V0.3+ work. Do not implement generator features during V0.1 unless explicitly requested.

1. Search/reuse first. Generation is a fallback when no suitable MCP meets the requirement.
2. Identify the underlying integration source: OpenAPI, official API docs, SDK, CLI, protocol, or browser-only surface.
3. Generate a **tool contract/specification first**. Include tool names, descriptions, inputs, outputs, source endpoint/method, auth needs, and risk classification.
4. Require an approved/coherent contract before generating code.
5. TypeScript is the first generated server language.
6. Generated secrets belong in environment variables and `.env.example`, never source.
7. Compile and test generated code before declaring success.
8. Protocol validation must use an isolated/sandboxed execution path.
9. Never run generated or third-party MCP packages in the main backend process.
10. Prefer MCP Inspector CLI for protocol checks when the sandbox milestone exists.
