# MCP Compass execution plan

This is the living implementation plan. Agents should update checkboxes and notes as work lands.

## Task IDs and dependencies

- Each checkbox is one task or acceptance task with a stable ID.
- `Depends on: none` means the task can be started in isolation.
- A task is ready when every listed dependency is checked. Dependencies are direct prerequisites; their own prerequisites are implied.
- Use one ready task ID per `$mcp-task-pr-flow` run and pull request. Independent ready tasks may proceed separately without waiting for one another.

## Product objective
A developer writes what their agent needs. MCP Compass returns the best existing MCP server with a transparent capability/quality explanation. If no sufficiently strong match exists, a later milestone offers contract-first MCP generation.

## V0.1 — searchable Registry intelligence

### Foundation
- [x] **FND-01** — Create Java 21 / Spring Boot backend module. _(Depends on: none)_
- [x] **FND-02** — Create Next.js TypeScript web shell. _(Depends on: none)_
- [x] **FND-03** — Add PostgreSQL + pgvector Docker Compose service. _(Depends on: none)_
- [x] **FND-04** — Add Flyway initial schema. _(Depends on: FND-01, FND-03)_
- [x] **FND-05** — Add repo agent instructions and project skills. _(Depends on: none)_
- [x] **FND-06** — Add baseline CI workflow. _(Depends on: FND-01, FND-02)_
- [x] **FND-07** — Add manual local-Codex task-to-PR workflow with CI validation and email summaries. _(Depends on: FND-05, FND-06)_

### Registry ingestion
- [x] **REG-01** — Add configurable MCP Registry HTTP client. _(Depends on: FND-01)_
- [x] **REG-02** — Parse direct or wrapped Registry server payloads defensively. _(Depends on: REG-01)_
- [x] **REG-03** — Upsert basic server metadata and raw payload. _(Depends on: FND-04, REG-02)_
- [x] **REG-04** — Add local-only manual sync endpoint. _(Depends on: REG-03)_
- [x] **REG-05** — Add cursor checkpoint persistence and incremental `updated_since` sync. _(Depends on: REG-03)_
- [x] **REG-06** — Add metrics for pages/items/errors and last successful sync. _(Depends on: REG-05)_
- [x] **REG-07** — Add integration test using a recorded Registry fixture. _(Depends on: REG-05)_

### Requirement analysis
- [x] **REQ-01** — Add deterministic heuristic analyzer for initial searchable keywords. _(Depends on: FND-01)_
- [x] **REQ-02** — Define versioned structured requirement schema: domain, service, required capabilities, forbidden capabilities, constraints. _(Depends on: FND-01)_
- [x] **REQ-03** — Add an LLM-backed analyzer behind the same interface. _(Depends on: REQ-02)_
- [x] **REQ-04** — Add golden tests for at least 20 developer requirements. _(Depends on: REQ-02, REQ-03)_

### Search and ranking
- [x] **SRCH-01** — Retrieve candidates from local database using requirement keywords. _(Depends on: REG-03, REQ-01)_
- [x] **SRCH-02** — Add deterministic text-overlap ranking and reasons. _(Depends on: SRCH-01)_
- [ ] **SRCH-03** — Add normalized capability tables populated from server/tool metadata. _(Depends on: REG-03, REQ-02)_
- [ ] **SRCH-04** — Add capability coverage as the dominant ranking factor. _(Depends on: SRCH-02, SRCH-03)_
- [ ] **SRCH-05** — Add maintenance/trust/installability features. _(Depends on: SRCH-02)_
- [ ] **SRCH-06** — Add vector retrieval only after a baseline benchmark exists. _(Depends on: FND-03, SRCH-07)_
- [ ] **SRCH-07** — Build a small relevance evaluation dataset and ranking report. _(Depends on: REQ-02, SRCH-02)_

### API/UI
- [x] **API-01** — Add `POST /api/v1/mcp/search`. _(Depends on: SRCH-02)_
- [x] **API-02** — Add `GET /api/v1/mcp/{id}`. _(Depends on: REG-03)_
- [x] **API-03** — Add simple developer search UI. _(Depends on: FND-02, API-01)_
- [ ] **API-04** — Render capability coverage and missing capability explanations. _(Depends on: SRCH-04, API-03)_
- [ ] **API-05** — Add MCP detail page. _(Depends on: FND-02, API-02)_
- [ ] **API-06** — Add pagination and shareable search query URLs. _(Depends on: API-03)_

### V0.1 exit criteria
- [ ] **EXIT-01** — A fresh local environment can ingest at least one Registry page and search it end-to-end. _(Depends on: REG-05, API-01, API-03)_
- [ ] **EXIT-02** — Ten manually selected requirements return sensible top-3 results. _(Depends on: SRCH-07)_
- [ ] **EXIT-03** — Backend tests and frontend lint/build pass in CI. _(Depends on: FND-06)_
- [ ] **EXIT-04** — No user search depends synchronously on Registry availability. _(Depends on: REG-03, SRCH-01)_

## V0.2 — enrichment and better ranking
- [ ] **ENR-01** — GitHub repository enrichment: activity, release age, archived status, license. _(Depends on: EXIT-01, EXIT-02, EXIT-03, EXIT-04)_
- [ ] **ENR-02** — Inspect discoverable MCP tool schemas without unsafe arbitrary execution where possible. _(Depends on: EXIT-01, EXIT-02, EXIT-03, EXIT-04)_
- [ ] **ENR-03** — Capability normalization and coverage scoring. _(Depends on: SRCH-03, SRCH-04, ENR-02)_
- [ ] **ENR-04** — Explain ranking feature contributions. _(Depends on: SRCH-05, ENR-03)_
- [ ] **ENR-05** — Add basic trust/quality score. _(Depends on: SRCH-05, ENR-01, ENR-02)_

## V0.3 — contract-first MCP generation
- [ ] **GEN-01** — Accept OpenAPI file/URL as source. _(Depends on: ENR-04, ENR-05)_
- [ ] **GEN-02** — Produce a proposed MCP tool contract before code. _(Depends on: REQ-02, GEN-01)_
- [ ] **GEN-03** — Let developer review/edit selected endpoints/tools. _(Depends on: FND-02, GEN-02)_
- [ ] **GEN-04** — Generate TypeScript MCP server from approved contract. _(Depends on: GEN-03)_
- [ ] **GEN-05** — Compile/test generated project. _(Depends on: GEN-04)_
- [ ] **GEN-06** — Export ZIP/GitHub-ready repository. _(Depends on: GEN-05)_

## V0.4 — sandbox validation
- [ ] **VAL-01** — Queue validation jobs. _(Depends on: GEN-06)_
- [ ] **VAL-02** — Run generated/discovered MCP servers in ephemeral isolated containers. _(Depends on: VAL-01)_
- [ ] **VAL-03** — Use MCP Inspector CLI for protocol validation. _(Depends on: VAL-02)_
- [ ] **VAL-04** — Network allow-listing, CPU/memory/time limits, non-root execution. _(Depends on: VAL-02)_
- [ ] **VAL-05** — Tool risk classification and security report. _(Depends on: VAL-03, VAL-04)_

## V0.5 — developer surfaces
- [ ] **DX-01** — CLI: `mcp-compass find "..."`. _(Depends on: API-01, API-02, VAL-05)_
- [ ] **DX-02** — CLI: `mcp-compass generate openapi.yaml`. _(Depends on: GEN-06, VAL-05)_
- [ ] **DX-03** — IntelliJ/VS Code integration only after API/CLI workflows are stable. _(Depends on: DX-01, DX-02)_

## Explicit non-goals for V0.1
- Agent runtime/orchestrator.
- General end-user personal assistant marketplace.
- Billing.
- Hosted MCP execution.
- Full security certification.
- Multi-service microservice architecture.
