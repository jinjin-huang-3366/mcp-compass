# Stdio installability pilot (EVI-11)

This research-only pilot does not add a product status or change search ranking. The supported
sample is two credential-free npm stdio servers: `@modelcontextprotocol/server-memory@2.1.1`
and `@modelcontextprotocol/server-filesystem@2.3.0`. These versions and their `dist/index.js`
entrypoints were checked against public npm release metadata on 2026-11-12. They are a convenience
sample, not an estimate of Registry coverage. Remote HTTP, Python, OCI, credential-dependent,
native-addon and lifecycle-script-dependent servers are excluded.

## Boundary and conditions

The integration test extends the existing `typescript-sandbox:1.0` image. During image preparation,
npm installs exact top-level versions with `--ignore-scripts --no-audit --no-fund`; each install has
a 90-second limit. Transitive dependencies are resolved afresh, so this is an observed dated
installation, not a reproducible dependency lock or supply-chain verification. Preparation needs
public npm network access and runs no package lifecycle scripts. It never launches server code.

The existing Docker runner launches each probe in a separate ephemeral container: UID/GID
65532, network none, 0.5 CPU, 256 MiB memory, 64 PIDs, read-only root, 64 MiB isolated `/tmp`,
all capabilities dropped, no-new-privileges, no host mounts or credentials. The probe has a
15-second protocol limit, 20-second observation and 30-second wall-time limit. Container cleanup
uses the existing runner's finally block. Only initialization and `tools/list` are sent; no tools run.
The filesystem sample receives an empty container-only `/tmp/pilot-data` directory.

The small JSON-line probe requests protocol version 2024-11-05 and checks initialize and tools/list
envelopes; this is a limited interoperability probe, not full MCP conformance certification.
Protocol stdout is capped at 32 KiB; stderr is discarded and secrets are never supplied.

| Status | Evidence |
| --- | --- |
| `INSTALLABLE` | Installed without scripts; initialized and returned a basic valid tools/list response in this sandbox. |
| `MISSING_OR_YANKED_PACKAGE` | npm returned E404 or ETARGET for the exact release. It cannot establish whether a release was yanked, unpublished or never existed. |
| `PROCESS_EXIT` | Server failed to launch or exited before successful tools/list; exit code/signal recorded when available. |
| `PROTOCOL_ERROR` | Invalid JSON/envelope/result, oversized response, or handshake deadline while running. |
| `NOT_TESTED` | No completed sandbox probe, unsupported install conditions, registry/network/install failure other than E404/ETARGET, or invalid report. Infrastructure failures are never relabeled as package failures. |

Negative controls include a deliberately nonexistent memory release `0.0.0-pilot-missing`, and
two synthetic container-only Node processes (exit 7 and invalid JSON). Synthetic controls are
marked in the report and excluded from supported-package coverage. Unit tests cover NOT_TESTED
for infrastructure, timeout and malformed report conditions without executing any package.

## Reproduce and inspect

From the repository root on a Linux Docker host with Java 21:

```sh
docker build -f validation-worker/runtime/typescript-v1/Dockerfile -t mcp-compass/typescript-sandbox:1.0 .
MCP_COMPASS_VERIFY_CONTAINER_EXECUTION=true ./mvnw -pl validation-worker -Dtest=StdioInstallabilityPilotTest test
cat validation-worker/target/stdio-installability-pilot/*.json
docker ps -a --filter label=dev.mcpcompass.validation=ephemeral
```

Expected: two INSTALLABLE reports, one MISSING_OR_YANKED_PACKAGE, one PROCESS_EXIT and
one PROTOCOL_ERROR; no leftover validation containers. Each report records UTC start, Node
version, installed package version, install exit/duration, runtime duration, network and script
policy. The Maven log prints each complete report and total image-preparation milliseconds.
Reports/build log are also written under `validation-worker/target/stdio-installability-pilot/`.
The existing task PR and baseline CI run this test under their container execution flag.

Local validation on Windows 2026-11-12: Docker client 29.6.2 found, but desktop-linux daemon pipe
was absent. Live execution was therefore NOT_TESTED locally; syntax and deterministic Java
tests were run. A passing live CI run is required before claiming this pilot observed installability.

## Product decision

Do not expose an installability badge yet. Even two successful samples cover only a narrow,
credential-free npm subset. A cold run requires a derivative image build, up to three bounded
network installations, separate per-package containers, cleanup and ongoing image maintenance;
an npm metadata or Registry declaration alone cannot substitute for these observations.
Use logged preparation/install/runtime milliseconds to assess cost; no fleet cost or coverage
percentage is inferred. Revisit only after a representative dated Registry sample, retained
dependency identities, freshness policy and an operational budget are agreed. Unsupported
packages remain not tested. An individual success does not prove useful tools, upstream API
access, safety, or continued availability.
