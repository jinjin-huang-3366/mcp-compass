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
- future `generation`, `validation`, `ai` modules.

### PostgreSQL
Stores normalized server metadata, future tools/capabilities, enrichment, validation results, and optional vectors. The public Registry is not the search-time source of truth.

### Future sandbox worker
Generated or third-party MCP code must run in an isolated worker/container with bounded CPU/memory/time/filesystem/network. The main backend never executes it directly.

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
Sandbox + MCP protocol validation
        |
        v
ZIP / GitHub-ready project
```

## Architecture boundaries
- The Registry client knows upstream response/pagination details.
- Search services operate on normalized local data.
- Ranking accepts explicit features/candidates and remains deterministic where possible.
- LLM integration implements interfaces; it must not be embedded into controllers or repositories.
- Generation and validation are separate: successful code generation does not imply safe/valid execution.
- The TypeScript generator is a deterministic transform from an approved contract to an in-memory file
  manifest. It serializes contract-specific values into `contract.json` and combines them with a versioned,
  classpath-only TypeScript runtime pack. The runtime registers tools from contract data instead of baking reviewed
  values into generated source. The pack includes a lockfile and unit tests. Repository CI materializes the exact
  generated manifest, installs dependencies with lifecycle scripts disabled, compiles it, and runs tests with mocked
  network access. The production backend does not write, install, compile, or execute generated projects.
