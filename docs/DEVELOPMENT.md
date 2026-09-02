# Development guide

## Prerequisites
- JDK 21
- Docker Desktop / compatible Docker engine
- Node.js 22+
- IntelliJ IDEA (Community is sufficient for opening Maven/Java; Ultimate adds richer framework tooling)

## Backend
```bash
docker compose up -d db
./mvnw -pl backend spring-boot:run
```

The Maven development runner activates the `local` Spring profile automatically. On Windows PowerShell, use:

```powershell
docker compose up -d db
.\mvnw.cmd -pl backend spring-boot:run
```

When running `McpCompassApplication` from IntelliJ, add `local` under **Active profiles** in the run configuration. The Registry sync controller is intentionally not registered without that profile.

Default DB connection:
- host: localhost:5432
- db/user/password: `mcp_compass`

Override with standard Spring environment variables if required.

For the local-only Registry sync endpoint, first confirm the startup log lists `local` as active:
```bash
curl -X POST "http://localhost:8080/api/v1/dev/registry/sync?maxPages=1"
```

PowerShell request:

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/dev/registry/sync?maxPages=1"
```

### Optional vector retrieval

Lexical retrieval is the default and needs no external credential. To test hybrid retrieval against the checked-in
baseline, set `MCP_COMPASS_VECTOR_ENABLED=true` and `OPENAI_API_KEY`; `OPENAI_EMBEDDING_MODEL` defaults to
`text-embedding-3-small`. The configured model must accept the requested 384 dimensions. Restart the backend, run a
Registry sync to populate or refresh versioned server search documents and embeddings, then issue the same search request with vector retrieval off
and on. Confirm relevant top results improve without regressing the baseline metrics, and record provider cost and
latency before proposing a default-on change. The candidate set is bounded at 100 and candidates below cosine
similarity `0.35` are excluded by default.

Embedding requests are batched once per Registry page and happen after metadata persistence and optional repository enrichment. An embedding provider or
pgvector query failure logs only the exception type and falls back to lexical search; it does not make Registry data or
user search unavailable.

### Optional GitHub repository enrichment

Set `MCP_COMPASS_GITHUB_ENRICHMENT_ENABLED=true` to refresh repository activity, latest release time, archived status,
SPDX license, bounded README text, and static tool metadata after each Registry page is persisted. Content enrichment
uses only GitHub's README endpoint and the configured allow-list (`.mcp/server.json` and `mcp-server.json` by default),
with byte, file, tool, and schema limits. `GITHUB_TOKEN` is optional for public repositories but recommended to obtain
a higher API rate limit. GitHub failures are logged per repository and do not fail Registry persistence or introduce a
dependency in the search request path. Repository content is untrusted data; this path never clones, installs, or runs it.

Registry sync observability is available through Spring Boot Actuator metrics:

- `mcp.registry.sync.pages` — pages persisted successfully;
- `mcp.registry.sync.items` — Registry items persisted successfully;
- `mcp.registry.sync.errors` — failed sync attempts;
- `mcp.registry.sync.last.success` — Unix timestamp, in seconds, of the last fully completed sync.

All four metrics carry `source=official-mcp-registry`. A page-limited run that leaves a continuation cursor does not advance the last-success value.

The local-only `POST /api/v1/dev/production-relevance/activate` endpoint supports the DEP-04 operational workflow.
It deliberately restarts one Registry traversal, caps each production run at 20 pages while preserving its continuation cursor, backfills search documents and
embeddings in provider-safe batches of at most 200, and returns aggregate coverage counts. Do not expose the `local`
profile publicly; production activation runs this endpoint only on an ephemeral trusted maintenance process.

## Frontend
```bash
cd web
cp .env.local.example .env.local
npm install
npm run dev
```

## Tests
```bash
./mvnw -pl backend,validation-worker test
cd web && npm ci && npm run lint && npm run build
cd cli && npm ci && npm run lint && npm test
python -m unittest discover -s .github/scripts -p 'test_*.py'
```

Generated-project verification is opt-in locally because it installs the generated manifest's locked npm
dependencies. With Node.js 22 available, run it on macOS/Linux with:

```bash
MCP_COMPASS_VERIFY_GENERATED_PROJECT=true ./mvnw -pl backend -Dtest=GeneratedTypeScriptProjectBuildTest test
```

Or on Windows PowerShell:

```powershell
$env:MCP_COMPASS_VERIFY_GENERATED_PROJECT = 'true'
.\mvnw.cmd -pl backend -Dtest=GeneratedTypeScriptProjectBuildTest test
Remove-Item Env:MCP_COMPASS_VERIFY_GENERATED_PROJECT
```

The test materializes the exact generator response in an isolated Maven build directory, runs
`npm ci --ignore-scripts`, then `npm test`. The generated tests mock `fetch`; they neither call the source API nor
start the MCP server.

## Isolated validation worker

Build the versioned TypeScript runtime image from the repository root, then package the separate worker JVM:

```bash
docker build -f validation-worker/runtime/typescript-v1/Dockerfile \
  -t mcp-compass/typescript-sandbox:1.0 .
./mvnw -pl validation-worker package
java -jar validation-worker/target/validation-worker-0.1.0-SNAPSHOT-all.jar queue
```

`queue` polls continuously; use `queue-once` for a single FIFO claim. Database settings default to the local Compose
credentials and can be overridden with `VALIDATION_DATABASE_URL`, `VALIDATION_DATABASE_USERNAME`, and
`VALIDATION_DATABASE_PASSWORD`. `VALIDATION_GENERATED_IMAGE`, `VALIDATION_WORKSPACE_ROOT`,
`VALIDATION_STARTUP_WINDOW_SECONDS`, `VALIDATION_PROTOCOL_TIMEOUT_SECONDS`, and
`VALIDATION_POLL_INTERVAL_SECONDS` control worker lifecycle without changing the backend. The protocol timeout bounds
the generated-project Inspector probe; the startup window controls direct discovered-image liveness checks. Sandbox
defaults and their accepted ranges are:

| Setting | Default | Accepted values |
| --- | --- | --- |
| `VALIDATION_PROTOCOL_TIMEOUT_SECONDS` | `30` | 1 to 300 seconds and no greater than the wall-time limit |
| `VALIDATION_CONTAINER_USER` | `65532:65532` | non-zero numeric `uid:gid` |
| `VALIDATION_CPU_LIMIT` | `0.5` | 0.1 to 8 CPUs |
| `VALIDATION_MEMORY_LIMIT_MB` | `256` | 64 to 4096 MiB |
| `VALIDATION_PROCESS_LIMIT` | `64` | 16 to 1024 processes |
| `VALIDATION_WALL_TIME_LIMIT_SECONDS` | `30` | 1 to 900 seconds and at least both observation windows |
| `VALIDATION_NETWORK` | `none` | `none` or a custom network listed in `VALIDATION_ALLOWED_NETWORKS` |
| `VALIDATION_ALLOWED_NETWORKS` | empty | comma-separated custom Docker network names |

Network names are policy profiles, not destination filters by themselves. Before allowing one, provision that custom
Docker network with an egress proxy or firewall that permits only the required destinations. The worker rejects the
built-in `host`, `bridge`, and `default` networks. It must run as a separate process on a host dedicated to sandbox
control; do not enable container control in the backend JVM.

Queued generated projects are compiled in the container and checked with the pinned MCP Inspector CLI using
`tools/list`. A successful job stores a structured `protocol_result` containing the Inspector version, method, and
machine-readable response; the probe does not call any generated tool. The same successful transaction stores a
`security_report` that classifies each approved/observed tool, defaults discrepancies to `DESTRUCTIVE`, records the
effective sandbox policy, and states the report's limitations. Retrieve it with:

```bash
curl http://localhost:8080/api/v1/validation/jobs/<job-id>
```

For a discovered MCP server already supplied as an OCI image, run:

```bash
java -jar validation-worker/target/validation-worker-0.1.0-SNAPSHOT-all.jar \
  discovered ghcr.io/example/weather-mcp:1.2.3 node server.js --stdio
```

This direct discovered-image entry point reports only that the server stayed alive for the startup window, then
forcibly removes the container. It is not yet an MCP Inspector check and does not invoke tools.

## Continuous integration

`.github/workflows/ci.yml` runs for every pull request, pushes to `main`, and manual dispatches. It has four
independent quality jobs:

- `backend` uses Java 21 and Node.js 22, builds the versioned sandbox image, enables exact-manifest generated-project
  verification plus a generated-container smoke test, and runs `./mvnw -pl backend,validation-worker test`;
- `web` uses Node.js 22, installs exactly from `package-lock.json` with `npm ci`, then runs lint and the
  production build;
- `cli` uses Node.js 22, installs exactly from `package-lock.json` with `npm ci`, then runs lint, build, and unit tests;
- `automation` tests the repository's workflow-support scripts, including the CI contract itself.

The commands in the Tests section mirror these CI gates and should pass before a branch is published.

`RegistrySearchAcceptanceTest` starts a fresh pgvector PostgreSQL container, serves a fixture-backed active
Registry page from a local stub, calls the local sync HTTP endpoint, and then searches the persisted server
through the public HTTP API. Docker must be running for this acceptance test; it is skipped when Docker is
unavailable so the remaining unit tests can still run.

Run only this acceptance path with:

```bash
./mvnw -pl backend -Dtest=RegistrySearchAcceptanceTest test
```

## Fresh-environment Registry search smoke test

1. Reset and start PostgreSQL with `docker compose down -v` followed by `docker compose up -d db`.
2. Start the backend with the `local` profile as described above and wait for the health endpoint to report
   `UP`.
3. Call `POST /api/v1/dev/registry/sync?maxPages=1` and confirm the response reports `pages: 1` and at least
   one persisted server.
4. Inspect a persisted name or title with:

   ```bash
   docker compose exec -T db psql -U mcp_compass -d mcp_compass -c \
     "SELECT registry_name, title FROM mcp_server ORDER BY registry_name LIMIT 5;"
   ```

5. Search for a distinctive word from one of those rows with `POST /api/v1/mcp/search`; confirm that the
   response includes the same Registry server with a positive score and human-readable reasons.
6. Start the frontend, enter the same search term, and confirm that the persisted server appears in the UI.

The Registry is contacted only by step 3. Both the API and UI searches in steps 5-6 use the local PostgreSQL
data populated by that sync.

## Reset database
```bash
docker compose down -v
docker compose up -d db
```

## IntelliJ
Open the repository root. Import Maven if IntelliJ does not auto-import. Mark Java 21 as project SDK. The Next.js project can remain in the same IntelliJ window; JetBrains IDEs detect `package.json` separately.
