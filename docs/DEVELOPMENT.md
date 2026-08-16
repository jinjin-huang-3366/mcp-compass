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

Default DB connection:
- host: localhost:5432
- db/user/password: `mcp_compass`

Override with standard Spring environment variables if required.

For the local-only Registry sync endpoint:
```bash
SPRING_PROFILES_ACTIVE=local ./mvnw -pl backend spring-boot:run
curl -X POST "http://localhost:8080/api/v1/dev/registry/sync?maxPages=1"
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

## Reset database
```bash
docker compose down -v
docker compose up -d db
```

## IntelliJ
Open the repository root. Import Maven if IntelliJ does not auto-import. Mark Java 21 as project SDK. The Next.js project can remain in the same IntelliJ window; JetBrains IDEs detect `package.json` separately.
