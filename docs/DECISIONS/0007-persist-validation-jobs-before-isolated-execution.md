# ADR 0007: Persist validation jobs before isolated execution

## Status

Accepted.

## Context

Generated MCP projects must eventually be validated by running untrusted code, but the main backend process must
never execute that code. A queued request also needs a stable input: regenerating a project after the runtime pack
changes could validate different bytes than the developer submitted.

## Decision

Queue validation work in PostgreSQL as part of the modular monolith. Each job starts in `QUEUED` status and stores
the exact deterministic generated TypeScript project manifest as JSONB, together with its project name,
generator/contract versions, and queue time. The queue index orders eligible work by status, timestamp, and ID.

Submission only generates and serializes the application-owned manifest. It does not materialize files, install
packages, invoke Docker, start an MCP server, or consume the job. The separate worker defined by ADR 0008 owns
claiming and executing jobs under the sandbox controls in `docs/SECURITY.md`.

## Consequences

Queue submission is durable and decoupled from eventual execution, and each worker will receive the exact submitted
artifact snapshot. PostgreSQL avoids introducing a second infrastructure system before operational evidence
requires one. The JSONB payload is untrusted data and remains behind the isolated-worker boundary when consumed.
