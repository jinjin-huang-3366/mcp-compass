# Contributing

Start with `AGENTS.md`, `PLANS.md`, and the relevant module documentation.

## Change scope
Keep pull requests small and coherent. Architecture changes should have an ADR in `docs/DECISIONS/`.

## Local checks

Backend:
```bash
./mvnw -pl backend test
```

Frontend:
```bash
cd web
npm install
npm run lint
npm run build
```

## Database
All schema changes require Flyway migrations. Never rely on Hibernate `ddl-auto` mutation.

## Security
Do not execute untrusted MCP packages or generated MCP code in the main application environment. See `docs/SECURITY.md`.
