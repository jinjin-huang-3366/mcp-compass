# Vercel deployment

MCP Compass uses two Vercel projects and one Neon Postgres resource:

- the repository root deploys the Spring Boot API from `Dockerfile.vercel`;
- `web/` deploys the Next.js frontend;
- Neon provides PostgreSQL with `pgvector` and `pg_trgm` enabled by Flyway.

The default Vercel region is London (`lhr1`). Change `regions` in `vercel.json` and choose a matching Neon region before the first production deployment if another data location is required.

## Backend project

Link the repository root to a Vercel project, then install the Neon integration interactively. Connect the database to Production and any Preview environment that should have database access. The application accepts the integration's `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, `PGPASSWORD`, and `PGSSLMODE` variables.

Configure these backend variables in Vercel:

| Variable | Value or purpose |
| --- | --- |
| `MCP_COMPASS_CORS_ALLOWED_ORIGIN_PATTERNS` | Initial preview pattern, then the exact production frontend origin |
| `MCP_COMPASS_REGISTRY_CRON_ENABLED` | `true` in Production |
| `CRON_SECRET` | Random secret managed by Vercel; never commit it |
| `MCP_COMPASS_REGISTRY_CRON_MAX_PAGES` | Number of Registry pages per daily run; defaults to `5` |
| `LOGGING_LEVEL_DEV_MCPCOMPASS` | `INFO` for production |

`vercel.json` invokes `GET /api/v1/internal/registry/sync` daily at 02:00 UTC. Vercel supplies `Authorization: Bearer <CRON_SECRET>`. Hobby cron is limited to one approximate invocation per day, so the in-process hourly scheduler remains disabled.

The backend image runs as a non-root user, listens on Vercel's `PORT`, and runs Flyway at startup. A production smoke test should confirm `/actuator/health`, successful migrations, `401` from an unauthenticated cron request, and a representative search or generation request.

## Frontend project

Create or link a second Vercel project with `web/` as its root directory. Set `NEXT_PUBLIC_API_BASE_URL` to the backend production URL for Production and the desired Preview environments, then redeploy. After the frontend URL is stable, replace any broad preview CORS pattern with the exact production origin and redeploy the backend.

## Validation worker

The existing `validation-worker` is not an HTTP service and must not be folded into the Spring Boot container. It continuously claims PostgreSQL jobs and launches generated code only inside isolated Docker containers. Deploy it unchanged only on a dedicated hardened container-runtime host.

Vercel Sandbox is a possible future execution target because it provides isolated microVMs and Docker support, but using it requires a per-job adapter that preserves the current resource limits, network allow-listing, cleanup, and durable result persistence. Until that adapter exists, submitted validation jobs remain queued when no external worker is running.
