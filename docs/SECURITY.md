# Security model

## Threat model
MCP metadata, package contents, repositories, README text, tool descriptions, generated code, and external API documentation can all contain malicious or misleading content.

## V0.1 controls
- No arbitrary package execution.
- Registry ingestion stores metadata only.
- No production credentials in repo.
- Local development sync endpoint is profile-gated.
- Search does not execute server tools.

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
