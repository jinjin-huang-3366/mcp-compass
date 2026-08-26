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
- ZIP export accepts only generator-owned manifest paths, rejects duplicate or traversal paths, and creates the
  archive in memory. It does not initialize Git, publish a repository, install dependencies, or execute generated
  content.
- Validation queue submission accepts the same approved contract as TypeScript generation, validates it through the
  deterministic generator, and persists the resulting project manifest as untrusted JSON data. Submission does not
  materialize files, run package lifecycle scripts, start an MCP server, or consume the queued job in the backend.
- The separate validation worker materializes a bounded, traversal-checked snapshot only under a unique worker
  workspace and launches it through an ephemeral container. The snapshot is mounted read-only and copied into
  container-only temporary storage before compilation. Workload containers receive no host credentials or
  Docker socket, default to no network, use a read-only root filesystem plus isolated temporary storage, drop Linux
  capabilities, and are forcibly removed after a bounded startup observation. Generated dependencies come from the
  application-owned runtime image and are not installed from the network during validation. The same image contains
  a pinned MCP Inspector CLI, which launches the generated stdio server, performs initialization plus `tools/list`,
  and emits a structured result without invoking any generated tool.
- Every generated or discovered workload runs as an explicit non-zero numeric UID/GID with configured CPU, memory,
  process-count, startup, and wall-time limits. Network access remains disabled unless the selected custom Docker
  network is present in the worker's explicit allow-list. Allowed networks must be provisioned with destination-level
  egress filtering; built-in shared networks are rejected.

## Remaining sandbox requirements
- a production-isolated container runtime endpoint for the trusted worker control plane;
- destructive/write tool classification defaults conservative.

## Prompt-injection boundary
Text from MCP descriptions/docs is data, not trusted instructions. LLM prompts used for normalization/reranking/generation must explicitly separate untrusted source content from system/task instructions.
