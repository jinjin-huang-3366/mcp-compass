# Deployment checklist

## Discover

- Read repository instructions, deployment docs, nested module instructions, and the current dirty-tree status.
- Identify the frontend root, backend build root, Java version, health route, migration owner, database extensions, CORS policy, scheduled work, and non-HTTP workers.
- Confirm the target Vercel scope, personal versus commercial use, region/data location, database provider, domain, and whether a paid action is authorized.

## Prepare

- Use `Dockerfile.vercel` or the current Vercel container entrypoint convention.
- Build only the backend module and copy only its runnable artifact into a small runtime image.
- Run as a numeric non-root user, declare the server with OCI `CMD`, and listen on `PORT`.
- Prefer an absolute runtime executable path in `CMD`; Vercel's certificate-injection wrapper may not preserve an image-specific `PATH`.
- Set Vercel's Framework Preset to `Container`, enable Fluid compute, and configure `PORT` to a non-privileged value such as `8080`; the generic `Other` preset can skip container detection and the default port 80 cannot be bound by a non-root process.
- Measure a cold start against Vercel's initialization window. If eager Spring initialization is too slow, set `SPRING_MAIN_LAZY_INITIALIZATION=true`, consider `SPRING_DATA_JPA_REPOSITORIES_BOOTSTRAP_MODE=lazy`, and use startup-oriented JVM flags such as `-XX:TieredStopAtLevel=1 -XX:+UseSerialGC`. Retest the first request as well as later routes that instantiate deferred beans; these settings trade peak throughput for startup latency.
- When a correct startup remains just beyond the socket deadline, a minimal TCP forwarder may listen on public `PORT` immediately and hold connections until Spring is ready on a private loopback port. Run it non-root, forward only to loopback, keep the Java process as PID 1, and never return a fabricated health response.
- Do not assume hidden source assets reach the remote Docker context. Store embedded dotfiles and hidden-directory content under visible template names when necessary, then restore their intended paths only in generated output.
- Do not bake credentials into Docker layers, build arguments, `vercel.json`, or public frontend variables.
- Configure Spring from `PGHOST`, `PGPORT`, `PGDATABASE`, `PGUSER`, and `PGPASSWORD`, or from an explicit JDBC URL. Prefer Neon pooling for autoscaling HTTP instances and keep Hikari's maximum pool small.
- Verify that required extensions can be created by the migration role. Use a direct connection for administrative migrations if the selected pooler cannot support them.
- Make allowed browser origins configurable. Begin with the narrowest preview pattern required, then use the final production origin.

## Schedule and workers

- Vercel container Functions scale down when idle, so Spring `@Scheduled` work is not reliable.
- Convert essential schedules to an idempotent HTTP endpoint protected by `Authorization: Bearer <CRON_SECRET>` or use a durable external scheduler.
- Vercel Hobby cron runs at most once daily with approximate timing. Do not silently reduce a required hourly schedule.
- Treat a continuously polling worker as a separate deployment problem.
- For untrusted execution, require microVM/container isolation, no inherited credentials, non-root execution, bounded CPU/memory/process/time, no network by default, allow-listed egress, cleanup, and durable result persistence.

## Provision and deploy

1. Authenticate interactively and verify the intended account/team.
2. Link or create the backend project without overwriting an unrelated existing project.
3. Install the Neon integration interactively, explicitly choosing the approved free or paid plan and region.
4. Verify that database variables are attached to Production and the required preview environments.
5. Add application settings and secrets through Vercel environment management. Never echo secret values into logs.
6. Deploy the backend and verify its health endpoint plus Flyway completion.
7. Invoke the secured cron endpoint without credentials and expect `401`. Make one authorized test only when the operation is safe and bounded.
8. Link/create the frontend project from its actual root, set `NEXT_PUBLIC_API_BASE_URL` to the backend production URL, and deploy.
9. Tighten backend CORS to the final frontend origin and redeploy the backend.
10. Exercise a representative browser workflow and inspect both services' runtime logs.

## Handoff

- Record project names, production URLs, target region, database resource, cron schedule, and where secrets are managed.
- State which components were not deployed, especially background or validation workers.
- Preserve configuration in source control without `.vercel`, `.env`, CLI state, tokens, or generated local caches.
- Provide rollback through the previous Vercel production deployment and database recovery mechanism appropriate to the selected Neon plan.
