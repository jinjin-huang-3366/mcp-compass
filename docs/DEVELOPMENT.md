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

Registry sync observability is available through Spring Boot Actuator metrics:

- `mcp.registry.sync.pages` — pages persisted successfully;
- `mcp.registry.sync.items` — Registry items persisted successfully;
- `mcp.registry.sync.errors` — failed sync attempts;
- `mcp.registry.sync.last.success` — Unix timestamp, in seconds, of the last fully completed sync.

All four metrics carry `source=official-mcp-registry`. A page-limited run that leaves a continuation cursor does not advance the last-success value.

## Frontend
```bash
cd web
cp .env.local.example .env.local
npm install
npm run dev
```

## Tests
```bash
./mvnw -pl backend test
cd web && npm run lint && npm run build
```

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
