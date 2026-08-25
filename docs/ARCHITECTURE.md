# Architecture

## Product flow

```text
Developer requirement
        |
        v
Requirement Analyzer
        |
        v
Structured keywords/capabilities
        |
        v
Candidate Retrieval ----> Local MCP metadata DB <---- Registry ingestion
        |
        v
Ranking + explanation
        |
        +---- good match ----> recommend existing MCP
        |
        +---- weak match ----> future generator path
```

## Runtime components

### Web
Next.js UI for requirement entry, match display, MCP details, and later generation review.

### Backend modular monolith
Spring Boot modules/packages:
- `registry` — official Registry ingestion and normalization;
- `requirement` — natural-language requirement parsing;
- `search` — request orchestration and candidate retrieval;
- `ranking` — deterministic ranking and explanations;
- `server` — MCP server detail API;
- `github` — optional repository maintenance enrichment persisted for later ranking;
- `generation` — contract-first deterministic TypeScript project generation and export;
- `validation` — durable validation job submission, with execution delegated to the separate isolated worker;
- future `ai` integrations.

### PostgreSQL
Stores normalized server metadata, future tools/capabilities, enrichment, validation results, and optional vectors. The public Registry is not the search-time source of truth.

### Validation worker
Generated or third-party MCP code runs only in an ephemeral container controlled by the separate
`validation-worker` JVM. The main backend never materializes or executes a workload and has no container-runtime
dependency. The worker claims queued snapshots atomically, materializes each snapshot under a unique worker-owned
directory, mounts it read-only, copies it into a container-only temporary workspace, runs MCP Inspector against the
generated stdio server, removes the container, deletes the host workspace, and records `EXECUTED` or `FAILED`.

Generated TypeScript workloads use a versioned runtime image whose dependencies were installed with lifecycle
scripts disabled. The workload container receives only its per-job workspace and no inherited credentials. A
runtime-neutral image path also supports starting a discovered OCI-packaged MCP server. Package discovery and
protocol validation for discovered images remains a separate concern until their queued input model exists.

The backend queues a validation request by persisting the exact deterministic generated-project manifest as inert
JSON with `QUEUED` status. Persisting the snapshot makes the eventual worker input stable even if the generator pack
changes after submission. The backend does not consume jobs, materialize files, or start generated code; those
operations remain behind the validation-worker/container boundary.

## Search pipeline evolution

### V0.1
heuristic keywords -> local lexical candidate filter plus optional pgvector candidate retrieval -> deterministic
ranking with capability coverage and bounded Registry maintenance, provenance, and installability signals. Vector
retrieval is disabled by default, records the embedding model with every vector, and falls back to lexical candidates
when the configured provider or vector query is unavailable.

### V0.2
structured requirement -> hybrid retrieval -> capability coverage -> quality/trust/installability score -> bounded reranking.

Target weighting direction (not a fixed contract): capability coverage should dominate; semantic similarity, maintenance, trust, docs/installability are secondary.

## Generator architecture (V0.3+)

```text
Requirement + missing capabilities
        |
        v
Integration source discovery
(OpenAPI/API docs/SDK/CLI)
        |
        v
Tool contract designer
        |
        v
Reviewed MCP tool specification
        |
        v
TypeScript code generator
        |
        v
Compile/test repair loop
        |
        v
ZIP / GitHub-ready project
        |
        v
Sandbox + MCP protocol validation
```

## Architecture boundaries
- The Registry client knows upstream response/pagination details.
- Search services operate on normalized local data.
- Ranking accepts explicit features/candidates and remains deterministic where possible.
- LLM integration implements interfaces; it must not be embedded into controllers or repositories.
- Generation and validation are separate: successful code generation does not imply safe/valid execution.
- The validation queue is PostgreSQL-backed inside the modular monolith. It records work for an isolated consumer;
  queue submission is not a hidden execution path.
- The validation worker is a separate process and Maven module. It is the only production component allowed to
  control the container runtime or materialize queued project files, and workload commands are always container
  arguments rather than host-shell commands.
- The TypeScript generator is a deterministic transform from an approved contract to an in-memory file
  manifest. It serializes contract-specific values into `contract.json` and combines them with a versioned,
  classpath-only TypeScript runtime pack. The runtime registers tools from contract data instead of baking reviewed
  values into generated source. The pack includes a lockfile, unit tests, `.gitignore`, and a GitHub Actions workflow.
  The export endpoint streams the validated manifest as a deterministic in-memory ZIP rooted at the generated project
  name. Repository CI materializes the exact generated manifest, installs dependencies with lifecycle scripts
  disabled, compiles it, and runs tests with mocked network access. The production backend does not write, install,
  compile, publish, or execute generated projects.
