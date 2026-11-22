# EVI-03 forbidden-capability enforcement pilot

**Decision.** Go for a repeatable, first-party worker control. No-go for product permission evidence from third-party MCP servers. The experiment shows that an authenticated role can be denied one concrete write and that a separate observer can verify the row stayed unchanged. In this fixture the observer also provisions the data store, ACLs, and credentials. For Registry candidates we lack independently attributable policy and state observations. Keep search permission enforcement `NOT_VERIFIED` and unobserved paths `UNKNOWN`.

## Threat model

The asset is the integrity of customer or third-party state, including state outside the apparent MCP tool's primary data store. A secondary asset is the credibility of a search result that might influence selection. The validation worker and test operator own fixture setup, the execution envelope, and evidence collection. MCP declarations, tool outputs, model-suggested arguments, and a server identity merely asserted by an MCP response have no enforcement authority. The fixture's MCP server is trusted first-party code. The worker runner and PostgreSQL have different responsibilities. The runner isolates the workload from the host. PostgreSQL authenticates the database role, applies table and column grants, and exposes the independently queried row state.

| Failure or misuse | Boundary and disposition |
| --- | --- |
| A test changes real data or sends a write to a public endpoint. | The probe accepts no target, external credential, SQL, or tool choice. Docker disables networking and mounts no host workspace. PostgreSQL listens only on a private Unix socket and holds a disposable row on tmpfs. The writer credential exists solely as an isolated positive control. |
| Ambient credentials, a different role, or an identity asserted by MCP is mistaken for the tested role. | All passwords are generated per run and separately scoped. Subprocesses get explicit environment maps, `psql` skips startup files, and each MCP fixture session receives only its selected credential. The read and write-success responses include `current_user` from PostgreSQL. A wrong password control establishes that a tool listing can precede a failed database call. Reports keep role names and grants, with no password values or setup SQL output. |
| A rejected argument, unavailable database, or tool-layer error looks like an enforced permission denial. | Require a valid read control, PostgreSQL `42501` for the valid write, no row mutation, and the same write under a role with the grant causing the expected mutation. The unsupported argument produces a distinct JSON-RPC error. A generic failure, timeout, incomplete report, or failed control leaves enforcement unknown. |
| An untrusted fixture rewrites evidence, changes its effective role, or writes through another channel. | The database observer is a separate connection outside the MCP protocol. The first-party probe, server, and database share a UID inside the test container. That is sufficient for this controlled fixture and leaves the observer's integrity unproven in the presence of untrusted server code. An MCP error, listed schema, or single unchanged row cannot establish the behavior of an unrelated tool, datastore, or side effect. |

## Observed experiment

The worker test launched the fixture on **2026-11-22 at 12:46:26 UTC**, on Linux/amd64 using Docker Engine 29.6.2 through Docker Desktop. The probe finished in 5,624 ms. The image used Alpine 3.23, Python 3.12.16, and PostgreSQL 17.12. Alpine package patches and the Python release tag may advance on rebuild. The run recorded the actual toolchain versions.

- **Worker policy.** Non-root UID:GID `65532:65532`, no network, read-only root, container-only 64 MiB `/tmp` tmpfs, dropped capabilities, `no-new-privileges`, no mounted workspace or runtime socket, 0.5 CPU, 256 MiB memory, 64 processes, 30-second observation window and 40-second workload wall-time limit. The runner manages container removal. Docker may use package registries when building the fixture image. The running fixture makes no network requests.
- **Transport.** Newline-delimited JSON-RPC over MCP stdio, protocol `2025-06-18`, with `initialize`, `notifications/initialized`, `tools/list` and the selected `tools/call` requests. PostgreSQL uses a private Unix-domain socket with SCRAM-SHA-256 authentication and no TCP listener.
- **Scopes.** `fixture_reader` had `SELECT` on `public.fixture_item`. `fixture_writer` had the same `SELECT` plus `UPDATE (name)` on that table. `fixture_admin` provisioned the fixture and independently queried row `id = 1` before and after the tested calls. The admin credential was absent from the MCP child environment.

| Role and call | Observed response | Observer's row state |
| --- | --- | --- |
| `fixture_reader`, `read_item` with `{}` | Success, `current_user = fixture_reader`, name `before` | Initial value `before` |
| `fixture_reader`, `rename_item` with `{"name":"after"}` | `isError: true`, PostgreSQL SQLSTATE `42501` | Still `before` |
| `fixture_writer`, the same `rename_item` call | Success, `current_user = fixture_writer`, name `after` | `after` |
| `fixture_reader` with a random incorrect password, `read_item` with `{}` | `isError: true`, `DATABASE_CALL_FAILED` | Used only as a failure control |
| `fixture_writer`, `rename_item` with `{"name":"outside-fixture"}` | JSON-RPC `-32602`; fixture rejects the argument before issuing SQL | Used only as a failure control |

The permission-denied observation applies to the fixed `UPDATE ... SET name = 'after' WHERE id = 1` exercised by this exact tool and argument under the tested ACL, input, database, transport, and runtime. Other tools, input shapes, rows, write operations, roles, credentials, destinations, MCP transports, server builds, deployment configurations, asynchronous effects, and public servers remain unknown. A candidate's overall write capability remains unknown.

## Reproduce in the worker

1. From the repository root with Docker running and Java 21 available, run the focused integration test. The Docker build prepares the fixture image. Maven launches the workload through `DockerCliContainerRunner` with the worker policy above.

   ```sh
   MCP_COMPASS_VERIFY_CONTAINER_EXECUTION=true ./mvnw -pl validation-worker -Dtest=ForbiddenCapabilityPilotTest test
   ```

   On Windows PowerShell, set `$env:MCP_COMPASS_VERIFY_CONTAINER_EXECUTION = 'true'` and use `./mvnw.cmd` with the same arguments. The test runs independently of the backend, web UI, existing database, and remote MCP endpoints.
2. Inspect `validation-worker/target/forbidden-capability-pilot/observation.json`. Expect a passed integration test, `readerWrite.sqlstate` of `42501`, `writerWrite.name` of `after`, and row states `before`, `before`, `after` in that order. Expect the wrong-password and invalid-argument controls to produce the distinct outcomes in the table. `image-build.log` in the same directory captures build details. Each rerun records a new timestamp and may use newer package patches.
3. Run `docker ps -a --filter ancestor=mcp-compass/forbidden-capability-pilot:1` after the test. Expect zero rows below the header. To omit the optional container test on later PowerShell runs, restore `MCP_COMPASS_VERIFY_CONTAINER_EXECUTION` to its prior value or remove it from the current session if it was previously unset.

Reconsider a product path when an operator can provision a disposable instance and distinct credentials, an observer outside the workload can authenticate the enforcement point and measure affected state independently, and there is evidence that a bounded result would change a real selection decision. Review side effects beyond the observed store, isolation and egress policy, explicit write-consent scope, version provenance, and handling of incomplete observations before asking for implementation.
