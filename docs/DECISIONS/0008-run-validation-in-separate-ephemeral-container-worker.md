# ADR 0008: Run validation in a separate ephemeral-container worker

## Status

Accepted. The generated-project liveness outcome is superseded by ADR 0009's Inspector protocol validation; the
isolation boundary and discovered-image liveness path remain current.

## Context

Queued validation snapshots contain generated code and eventually discovered third-party MCP servers. Starting that
code in the backend JVM or through a host command would cross the core trust boundary. VAL-02 needs lifecycle
execution without prematurely claiming MCP protocol correctness, full containment policy, or risk reporting.

## Decision

Add a separate `validation-worker` process and Maven module. It atomically claims a PostgreSQL `QUEUED` job with
`FOR UPDATE SKIP LOCKED`, materializes the bounded snapshot under a unique worker directory, and passes an
application-owned command to a Docker CLI adapter. The adapter creates a named, auto-remove container, observes the
attached process for a bounded startup window, and forcibly removes it on every outcome. The backend remains limited
to inert job submission and has no Docker/runtime dependency.

Generated TypeScript projects use a versioned Node runtime image built from the generator's checked-in lockfile.
Dependencies are installed into that image with lifecycle scripts disabled; validation runs with no network and does
not install dependencies at runtime. A second request form starts an already-discovered OCI image and passes its
command as container arguments, never through a host shell.

The VAL-02 safe baseline gives workload containers no Docker socket or inherited credentials, mounts generated
snapshots read-only and compiles a copy in container-only temporary storage, uses a read-only root filesystem,
has no network, drops Linux capabilities, and sets `no-new-privileges`.
Production must place the trusted worker and its container-runtime endpoint away from the application host.

ADR 0009 changes queued generated-project `EXECUTED` semantics to require a successful MCP Inspector `tools/list`
probe. Non-root enforcement, explicit allow-list policy, comprehensive CPU/memory/process/total-time limits, and
runtime endpoint hardening remain VAL-04. Structured security/risk reporting remains VAL-05.

## Consequences

Generated or discovered MCP code never executes in the main application JVM or directly on the host. Worker crashes
can leave a claimed job in `RUNNING`; leasing/recovery can be added when operational requirements are known. The
current discovered-server entry point requires an OCI image; turning Registry package metadata into a trusted,
immutable container input is a separate discovery concern and does not weaken the execution boundary.
