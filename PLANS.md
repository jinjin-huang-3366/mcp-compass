# MCP Compass execution plan

This is the living implementation plan. Agents should update checkboxes and notes as work lands.

## Product objective
A developer writes what their agent needs. MCP Compass returns the best existing MCP server with a transparent capability/quality explanation. If no sufficiently strong match exists, a later milestone offers contract-first MCP generation.

## V0.1 — searchable Registry intelligence

### Foundation
- [x] Create Java 21 / Spring Boot backend module.
- [x] Create Next.js TypeScript web shell.
- [x] Add PostgreSQL + pgvector Docker Compose service.
- [x] Add Flyway initial schema.
- [x] Add repo agent instructions and project skills.
- [x] Add baseline CI workflow.
- [x] Add manual local-Codex task-to-PR workflow with CI validation and email summaries.

### Registry ingestion
- [x] Add configurable MCP Registry HTTP client.
- [x] Parse direct or wrapped Registry server payloads defensively.
- [x] Upsert basic server metadata and raw payload.
- [x] Add local-only manual sync endpoint.
- [ ] Add cursor checkpoint persistence and incremental `updated_since` sync.
- [ ] Add metrics for pages/items/errors and last successful sync.
- [ ] Add integration test using a recorded Registry fixture.

### Requirement analysis
- [x] Add deterministic heuristic analyzer for initial searchable keywords.
- [ ] Define versioned structured requirement schema: domain, service, required capabilities, forbidden capabilities, constraints.
- [ ] Add an LLM-backed analyzer behind the same interface.
- [ ] Add golden tests for at least 20 developer requirements.

### Search and ranking
- [x] Retrieve candidates from local database using requirement keywords.
- [x] Add deterministic text-overlap ranking and reasons.
- [ ] Add normalized capability tables populated from server/tool metadata.
- [ ] Add capability coverage as the dominant ranking factor.
- [ ] Add maintenance/trust/installability features.
- [ ] Add vector retrieval only after a baseline benchmark exists.
- [ ] Build a small relevance evaluation dataset and ranking report.

### API/UI
- [x] Add `POST /api/v1/mcp/search`.
- [x] Add `GET /api/v1/mcp/{id}`.
- [x] Add simple developer search UI.
- [ ] Render capability coverage and missing capability explanations.
- [ ] Add MCP detail page.
- [ ] Add pagination and shareable search query URLs.

### V0.1 exit criteria
- [ ] A fresh local environment can ingest at least one Registry page and search it end-to-end.
- [ ] Ten manually selected requirements return sensible top-3 results.
- [ ] Backend tests and frontend lint/build pass in CI.
- [ ] No user search depends synchronously on Registry availability.

## V0.2 — enrichment and better ranking
- [ ] GitHub repository enrichment: activity, release age, archived status, license.
- [ ] Inspect discoverable MCP tool schemas without unsafe arbitrary execution where possible.
- [ ] Capability normalization and coverage scoring.
- [ ] Explain ranking feature contributions.
- [ ] Add basic trust/quality score.

## V0.3 — contract-first MCP generation
- [ ] Accept OpenAPI file/URL as source.
- [ ] Produce a proposed MCP tool contract before code.
- [ ] Let developer review/edit selected endpoints/tools.
- [ ] Generate TypeScript MCP server from approved contract.
- [ ] Compile/test generated project.
- [ ] Export ZIP/GitHub-ready repository.

## V0.4 — sandbox validation
- [ ] Queue validation jobs.
- [ ] Run generated/discovered MCP servers in ephemeral isolated containers.
- [ ] Use MCP Inspector CLI for protocol validation.
- [ ] Network allow-listing, CPU/memory/time limits, non-root execution.
- [ ] Tool risk classification and security report.

## V0.5 — developer surfaces
- [ ] CLI: `mcp-compass find "..."`.
- [ ] CLI: `mcp-compass generate openapi.yaml`.
- [ ] IntelliJ/VS Code integration only after API/CLI workflows are stable.

## Explicit non-goals for V0.1
- Agent runtime/orchestrator.
- General end-user personal assistant marketplace.
- Billing.
- Hosted MCP execution.
- Full security certification.
- Multi-service microservice architecture.
