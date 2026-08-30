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
| `PORT` | `8080`; Vercel otherwise defaults containers to port 80, which a non-root process cannot bind |
| `SPRING_MAIN_LAZY_INITIALIZATION` | `true` to keep Spring's cold start within Vercel's container initialization window |
| `SPRING_DATA_JPA_REPOSITORIES_BOOTSTRAP_MODE` | `lazy` so Hibernate repository initialization does not delay the listening socket |
| `JAVA_TOOL_OPTIONS` | `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC` to favor cold-start latency over peak JVM throughput |
| `MCP_COMPASS_INTERNAL_PORT` | Optional private Spring port; defaults to `8081` behind the startup gate |

Set the backend project's Framework Preset to `Container` and keep Fluid compute enabled. A project created with the generic `Other` preset does not build `Dockerfile.vercel` as a container.

`vercel.json` invokes `GET /api/v1/internal/registry/sync` daily at 02:00 UTC. Vercel supplies `Authorization: Bearer <CRON_SECRET>`. Hobby cron is limited to one approximate invocation per day, so the in-process hourly scheduler remains disabled.

The backend image runs as a non-root user and starts a small `socat` gate on Vercel's public `PORT`. The gate holds and forwards connections to Spring's private port once Tomcat is ready, allowing Flyway and Hibernate to complete safely even when their combined cold start exceeds Vercel's socket initialization deadline. The entrypoint invokes both `socat` and Temurin's Java binary by absolute path so Vercel's certificate wrapper can start them reliably. Embedded generator assets use visible build-time template names because Vercel's source upload omits hidden files and directories; generated projects still receive `.env.example`, `.gitignore`, and `.github/workflows/ci.yml`. A production smoke test should confirm `/actuator/health`, successful migrations, `401` from an unauthenticated cron request, and a representative search or generation request.

## Frontend project

Create or link a second Vercel project with `web/` as its root directory. Set `NEXT_PUBLIC_API_BASE_URL` to the backend production URL for Production and the desired Preview environments, then redeploy. After the frontend URL is stable, replace any broad preview CORS pattern with the exact production origin and redeploy the backend.

## Validation worker

The existing `validation-worker` is not an HTTP service and must not be folded into the Spring Boot container. It continuously claims PostgreSQL jobs and launches generated code only inside isolated Docker containers. Deploy it unchanged only on a dedicated hardened container-runtime host.

Vercel Sandbox is a possible future execution target because it provides isolated microVMs and Docker support, but using it requires a per-job adapter that preserves the current resource limits, network allow-listing, cleanup, and durable result persistence. Until that adapter exists, submitted validation jobs remain queued when no external worker is running.

## Repeatable production deployments

The `MCP Compass Vercel production deployment` GitHub Actions workflow deploys an exact, CI-green `main` commit to the existing projects. It stages each production build without assigning the production domain, runs component smoke tests, and promotes the build only after those checks pass. When both projects are selected, the backend is promoted before the frontend.

Configure these GitHub Actions repository secrets before the first workflow run:

| Secret | Purpose |
| --- | --- |
| `VERCEL_TOKEN` | Vercel access token for non-interactive CLI authentication |
| `VERCEL_ORG_ID` | Account or team ID shared by the two existing projects |
| `VERCEL_BACKEND_PROJECT_ID` | Project ID for the repository-root container project |
| `VERCEL_FRONTEND_PROJECT_ID` | Project ID for the `web/` Next.js project |

Retrieve the organization and project IDs by linking each existing project with Vercel CLI and reading its generated `.vercel/project.json`; never commit that directory. Store values under **Settings > Secrets and variables > Actions**. The workflow also targets the GitHub `production` environment, where required reviewers can be configured if desired.

From Codex, invoke `$mcp-vercel-deploy` and explicitly request `all`, `backend`, or `frontend`. The skill resolves the latest remote `main`, requires successful CI for that exact commit, dispatches `.github/workflows/vercel-deploy.yml` from `main`, and monitors staging, smoke tests, and promotion. The workflow will not provision projects, change Vercel or Neon settings, update DNS, or deploy the validation worker.

The workflow pins Vercel CLI `59.10.0`; update that pin deliberately after reviewing Vercel CLI release notes and validating this staged-promotion flow.
