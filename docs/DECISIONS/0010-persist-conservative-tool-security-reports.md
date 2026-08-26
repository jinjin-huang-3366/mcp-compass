# ADR 0010: Persist conservative tool security reports

## Status

Accepted.

## Context

An MCP Inspector `tools/list` result proves protocol discovery but does not tell a developer whether listed tools can
change external state or which sandbox controls bounded the observation. Risk reporting must remain deterministic,
must not invoke untrusted tools, and must not imply a security certification.

## Decision

After a generated project completes Inspector `tools/list`, the validation worker compares the observed tool names
with the immutable approved `contract.json` in the queued project snapshot. It records each tool's approved
`READ_ONLY`, `MUTATING`, or `DESTRUCTIVE` classification and promotes the highest classification to `overallRisk`.
The worker independently infers a minimum risk from the source HTTP method and upgrades any lower approved
classification. An unsupported classification, an approved tool missing from `tools/list`, or an observed undeclared
tool defaults to `DESTRUCTIVE` and produces a finding.

The versioned JSON report also records the effective non-root, network, CPU, memory, process, wall-time, read-only
filesystem, capability-drop, and no-new-privileges controls. It explicitly says that tools were listed but not
invoked and records operational limitations. The worker persists the report beside the protocol result, and the
backend exposes both through `GET /api/v1/validation/jobs/{id}`.

## Consequences

Developers receive deterministic tool-inventory and containment evidence without expanding the execution boundary.
A destructive classification is information, not a failed protocol validation. The report does not verify upstream
API behavior, inspect arbitrary tool implementation semantics, or certify the workload as safe.
