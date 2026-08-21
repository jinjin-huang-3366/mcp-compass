# MCP Compass execution plan

This is the living implementation plan. Agents should update checkboxes and notes as work lands.

## Task IDs and dependencies

- Each checkbox is one task or acceptance task with a stable ID.
- `Depends on: none` means the task can be started in isolation.
- A task is ready when every listed dependency is checked. Dependencies are direct prerequisites; their own prerequisites are implied.
- Use one ready task ID per `$mcp-task-pr-flow` run and pull request. Independent ready tasks may proceed separately without waiting for one another.

## Parallel delivery groups

Keep this scheduling index in `PLANS.md` so task state, dependencies, and delivery groups have one source of truth. The groups list task IDs only: do not duplicate task checkboxes or full task text here because the merge workflow must match exactly one canonical plan item.

The table covers every task that was unchecked when the groups were defined, and no task depends directly or transitively on another task in the same group. After tasks land, use the canonical checkboxes below to skip completed IDs; refresh this index when tasks or dependencies change. Group numbers describe dependency waves, not multi-task pull requests. A group is unlocked when the prerequisite tasks named in its **Start after** column are checked; unrelated work in earlier groups does not have to finish. Tasks in an unlocked group may run concurrently, but each task still requires its own branch, `$mcp-task-pr-flow` invocation, workflow run, and pull request. Dependency independence also does not guarantee conflict-free files, so coordinate tasks that edit the same module.

| Group | Task IDs | Start after |
| --- | --- | --- |
| **PG-01 — V0.1 independent work** | `SRCH-04`, `SRCH-05`, `SRCH-07`, `API-05`, `API-06`, `EXIT-01`, `EXIT-03`, `EXIT-04` | All direct dependencies are checked. |
| **PG-02 — finish V0.1** | `SRCH-06`, `API-04`, `EXIT-02` | `SRCH-04` and `SRCH-07` |
| **PG-03 — begin enrichment** | `ENR-01`, `ENR-02` | `EXIT-01`, `EXIT-02`, `EXIT-03`, `EXIT-04` |
| **PG-04 — score enriched data** | `ENR-03`, `ENR-05` | `SRCH-04`, `SRCH-05`, `ENR-01`, `ENR-02` |
| **PG-05 — explain ranking** | `ENR-04` | `SRCH-05`, `ENR-03` |
| **PG-06 — accept an API source** | `GEN-01` | `ENR-04`, `ENR-05` |
| **PG-07 — propose a contract** | `GEN-02` | `GEN-01` |
| **PG-08 — review the contract** | `GEN-03` | `GEN-02` |
| **PG-09 — generate from the contract** | `GEN-04` | `GEN-03` |
| **PG-10 — verify generated code** | `GEN-05` | `GEN-04` |
| **PG-11 — export generated code** | `GEN-06` | `GEN-05` |
| **PG-12 — queue validation** | `VAL-01` | `GEN-06` |
| **PG-13 — isolate validation** | `VAL-02` | `VAL-01` |
| **PG-14 — validate protocol and containment** | `VAL-03`, `VAL-04` | `VAL-02` |
| **PG-15 — report tool risk** | `VAL-05` | `VAL-03`, `VAL-04` |
| **PG-16 — add CLI surfaces** | `DX-01`, `DX-02` | `VAL-05` and, for `DX-02`, `GEN-06` |
| **PG-17 — add IDE integrations** | `DX-03` | `DX-01`, `DX-02` |

To deliver one task, use the group ID as scheduling context, for example: `Use $mcp-task-pr-flow for SRCH-04 from PG-01.` To fan out every ready task in a group as independent PRs, use `Use $mcp-task-batch-flow for PG-01.` The batch skill preserves one isolated `$mcp-task-pr-flow` child, branch, workflow run, and pull request per task.

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
- [x] **SRCH-03** — Add normalized capability tables populated from server/tool metadata. _(Depends on: REG-03, REQ-02)_
- [x] **SRCH-04** — Add capability coverage as the dominant ranking factor. _(Depends on: SRCH-02, SRCH-03)_
- [x] **SRCH-05** — Add maintenance/trust/installability features. _(Depends on: SRCH-02)_
- [ ] **SRCH-06** — Add vector retrieval only after a baseline benchmark exists. _(Depends on: FND-03, SRCH-07)_
- [x] **SRCH-07** — Build a small relevance evaluation dataset and ranking report. _(Depends on: REQ-02, SRCH-02)_

### API/UI
- [x] **API-01** — Add `POST /api/v1/mcp/search`. _(Depends on: SRCH-02)_
- [x] **API-02** — Add `GET /api/v1/mcp/{id}`. _(Depends on: REG-03)_
- [x] **API-03** — Add simple developer search UI. _(Depends on: FND-02, API-01)_
- [ ] **API-04** — Render capability coverage and missing capability explanations. _(Depends on: SRCH-04, API-03)_
- [x] **API-05** — Add MCP detail page. _(Depends on: FND-02, API-02)_
- [ ] **API-06** — Add pagination and shareable search query URLs. _(Depends on: API-03)_

### V0.1 exit criteria
- [x] **EXIT-01** — A fresh local environment can ingest at least one Registry page and search it end-to-end. _(Depends on: REG-05, API-01, API-03)_
- [ ] **EXIT-02** — Ten manually selected requirements return sensible top-3 results. _(Depends on: SRCH-07)_
- [x] **EXIT-03** — Backend tests and frontend lint/build pass in CI. _(Depends on: FND-06)_
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
