# Ready-to-use Codex prompts

These prompts assume Codex is launched from the repository root.

## Continue the current milestone

```text
$mcp-compass-development
Read AGENTS.md and PLANS.md. Pick the next unchecked V0.1 task that can be completed as one coherent change. Read only the relevant docs, implement it, run relevant tests, and update PLANS.md. Do not start V0.2+ work.
```

## Improve Registry ingestion

```text
$mcp-registry-ingestion
Implement persisted cursor/checkpoint support for Registry sync, including Flyway migration changes if required and fixture-driven tests. Preserve idempotent upsert behavior and do not make the user search path call the Registry.
```

## Improve ranking

```text
$mcp-search-ranking
Design the first versioned structured requirement schema and add golden tests for representative developer requirements. Keep the existing heuristic analyzer as a deterministic fallback. Do not add embeddings yet.
```

## Prepare a PR

```text
$github-pr-workflow
Review the current diff for scope, architecture and security. Run relevant tests, identify anything not run, and draft a concise PR title/body with risks and intentional follow-ups.
```

## Fix CI

```text
$ci-failure-triage
Diagnose the failing CI check, identify the first causal error, reproduce it with the narrow local command, fix it without weakening tests, and rerun the relevant checks.
```

## Later: MCP generation

```text
$mcp-generator
Read the V0.3 plan before coding. Design the OpenAPI-to-tool-contract stage first. Do not generate implementation source until a structured MCP tool contract exists and is testable.
```
