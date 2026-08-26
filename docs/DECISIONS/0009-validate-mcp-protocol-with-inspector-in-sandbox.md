# ADR 0009: Validate generated MCP protocol with Inspector inside the sandbox

## Status

Accepted.

## Context

VAL-02 proved only that a generated MCP server process remained alive for a short startup window. Process liveness
does not prove that the server can complete the MCP initialization handshake or return a valid protocol response.
Protocol checks must retain the existing rule that generated code never runs in the backend JVM or directly on the
host.

## Decision

Install the pinned MCP Inspector CLI in the versioned TypeScript validation image. For generated validation jobs, the
worker compiles the materialized snapshot and runs Inspector CLI in machine-readable mode against the generated stdio
server with `--method tools/list --format json`. Inspector therefore owns server startup, initialization, the
`tools/list` request, and shutdown inside the same ephemeral container boundary established by VAL-02.

A generated validation succeeds only when Inspector exits zero within the bounded observation window and its output
contains a JSON `result.tools` array. The worker persists that structured response together with the validator name,
version, and method in `validation_job.protocol_result`. Non-zero exits, timeouts, and malformed output fail the job
with a bounded diagnostic. Discovered OCI images retain VAL-02's liveness-only entry point until their validation-job
input model is defined.

## Consequences

`EXECUTED` now means a queued generated project completed an isolated MCP initialization plus `tools/list` protocol
probe, rather than merely remaining alive. The Inspector and Node versions are locked into the runtime image, and no
package installation or network access is required while a workload runs. This check does not invoke generated tools
or claim upstream API correctness, containment completeness, or tool-risk safety; those remain separate validation
concerns.
