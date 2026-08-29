---
name: vercel-springboot-neon
description: Deploy a Next.js frontend and containerized Spring Boot HTTP API to Vercel with Neon Postgres. Use when a repository needs deployment preparation, Vercel/Neon provisioning, environment configuration, scale-to-zero cron adaptation, or production smoke tests. Do not treat an always-on or untrusted-code worker as an ordinary Vercel Function.
---

# Vercel Spring Boot + Neon

Read [references/deployment-checklist.md](references/deployment-checklist.md) before changing or deploying a repository.

## Outcome

Deploy the HTTP application without committing secrets, weakening workload isolation, or assuming that an in-process scheduler survives Vercel scale-to-zero. Preserve the user's provider, plan, region, repository, and account choices.

## Workflow

1. Verify the current Vercel container, Hobby, cron, and Sandbox behavior in official documentation; these capabilities and limits change frequently.
2. Read repository instructions and inspect the smallest relevant deployment, backend, frontend, database, scheduler, migration, and worker files. Preserve unrelated dirty-tree changes.
3. Choose the simplest topology compatible with the repository. Two Vercel projects—one containerized backend and one Next.js frontend—are usually easier for an existing monorepo than combining services, but an existing project topology wins.
4. Prepare a non-root Spring Boot OCI image that listens on `PORT`. Keep database state external, use a pooled Neon connection with a deliberately small application pool, and leave Flyway as the schema owner.
5. Replace required in-process schedules with authenticated HTTP triggers or another durable scheduler. On Vercel Hobby, daily cron is the maximum native frequency and timing is approximate.
6. Keep background workers distinct from HTTP Functions. Never place untrusted or generated-code execution in the Spring process. Use Vercel Sandbox only through an explicit per-job adapter with bounded duration, network policy, cleanup, and persisted results; otherwise leave the worker on an isolated runtime.
7. Authenticate through the provider's interactive device/browser flow. Do not request passwords, permanent tokens, or database credentials in chat. Stop for user confirmation before selecting a paid plan, attaching a billable resource, changing production DNS, or replacing an existing production project.
8. Provision/link the backend project, attach Neon, configure secrets, and deploy the backend before building the frontend with its public API URL. Tighten CORS to the final frontend origin after the production URL exists.
9. Run production smoke tests for health, migrations, authenticated scheduler behavior, a representative API request, CORS, and the frontend. Check runtime logs without printing secrets.
10. Commit reusable configuration and documentation only after verification. Report deployed URLs, plan/region, remaining worker limitations, tests run, and rollback instructions.
