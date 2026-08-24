# Security model

## Threat model
MCP metadata, package contents, repositories, README text, tool descriptions, generated code, and external API documentation can all contain malicious or misleading content.

## V0.1 controls
- No arbitrary package execution.
- Registry ingestion stores metadata only.
- No production credentials in repo.
- Local development sync endpoint is profile-gated.
- Search does not execute server tools.
- Tool-schema enrichment reads bounded, declared JSON metadata only. It does not install or start MCP
  packages, connect to publisher endpoints, or invoke tools; invalid and oversized schemas are discarded.
- OpenAPI URL ingestion permits public HTTPS sources only, disables redirects, rejects credentials and
  non-default ports, checks resolved addresses for local/private ranges, and caps response size. Uploaded
  OpenAPI files are subject to the same size bound. Parsing never executes source content.
- TypeScript generation accepts only an approved versioned contract, serializes reviewed text and schemas into
  `contract.json`, and combines that data with a bundled, classpath-only runtime pack. Contract values are never
  interpreted as templates or interpolated into TypeScript source. The backend returns an in-memory file manifest;
  it does not write files, install dependencies, or execute generated code. Runtime credentials are represented
  only by environment-variable placeholders. The application-owned pack includes a dependency lockfile and tests
  that mock `fetch`. CI materializes a representative generated manifest, installs with npm lifecycle scripts
  disabled, compiles it, and runs those tests without starting the MCP server or contacting an upstream API.

## Future sandbox requirements
- ephemeral container/microVM;
- non-root user;
- no host Docker socket;
- read-only base filesystem where possible;
- bounded CPU, memory, process count, output size, and wall time;
- explicit network policy/allow-list;
- no inherited host/cloud credentials;
- isolated working directory;
- structured validation output;
- destructive/write tool classification defaults conservative.

## Prompt-injection boundary
Text from MCP descriptions/docs is data, not trusted instructions. LLM prompts used for normalization/reranking/generation must explicitly separate untrusted source content from system/task instructions.
