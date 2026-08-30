---
name: mcp-vercel-deploy
description: Deploy the latest CI-green MCP Compass main commit to the existing production Vercel backend and frontend projects through the repository's manual deployment workflow. Use when the user asks to deploy or redeploy MCP Compass production. Do not use this flow to provision projects, change plans or regions, attach databases, or deploy the validation worker.
---

# MCP Compass Vercel deploy

Deploy an explicitly authorized target with `.github/workflows/vercel-deploy.yml`, monitor it through staged smoke tests and production promotion, and stop after reporting the result. The workflow is the executable source of truth. Do not make application changes, create a pull request, or deploy a local branch as part of this command.

## Inputs

- `target`: default to `all`; accept `backend` or `frontend` only when the user narrows the deployment.
- `base_branch`: always `main` for this production workflow.
- `git_sha`: derive the full current `origin/main` SHA immediately before dispatch.

An explicit request in the current message to run `$mcp-vercel-deploy` is production-deployment authorization for the chosen target. If the skill was selected implicitly, or the user asks only for preparation, status, or advice, do not dispatch without explicit confirmation to deploy production.

## Preflight

1. Read `AGENTS.md`, `docs/DEPLOYMENT.md`, `.agents/skills/vercel-springboot-neon/references/deployment-checklist.md`, and `.github/workflows/vercel-deploy.yml` from current `main`.
2. Confirm the remote is `jinjin-huang-3366/mcp-compass`, GitHub CLI authentication permits workflow dispatch and run inspection, and the workflow exists on `main`.
3. Verify these GitHub Actions secret names exist without reading or printing their values: `VERCEL_TOKEN`, `VERCEL_ORG_ID`, and the project ID required by the target (`VERCEL_BACKEND_PROJECT_ID`, `VERCEL_FRONTEND_PROJECT_ID`, or both for `all`). If any are missing, stop with the setup steps in `docs/DEPLOYMENT.md`.
4. Resolve the current remote `main` SHA without changing or including unrelated local work. Require a completed successful `CI` push run for that exact SHA. Wait for active CI rather than dispatching an untested commit; stop if CI fails.
5. Report the exact SHA and target that will be deployed. Do not provision a Vercel project, attach Neon, alter environment variables, select a paid plan, change DNS, or deploy the validation worker.

## Dispatch and monitor

Dispatch exactly once from `main`:

```text
gh workflow run vercel-deploy.yml --ref main -f git_sha=<full-main-sha> -f target=<all|backend|frontend> -f confirm_production=true
```

Identify the new `workflow_dispatch` run by workflow, actor, `main` ref, and creation time. Record its URL. Poll with `gh run view <run-id> --json status,conclusion,url,jobs`, giving the user a concise update at least once per minute while active. Never dispatch another run merely because the current run is queued, waiting for a protected `production` environment, building slowly, or cold-starting.

The workflow deploys the backend before the frontend for `all`. Each component is built as a staged production deployment, smoke-tested at its unique URL, and promoted only after those checks pass. The backend checks health plus `401` from the unauthenticated Registry cron; the frontend checks its root page. It then verifies each promoted production project.

## Failure and retry boundary

Collect diagnostics with `gh run view <run-id> --log-failed` and identify the first causal error. Do not expose tokens, project IDs, database values, or downloaded Vercel environment files.

Allow at most one retry, and only when the failure is demonstrably transient or an in-scope correction outside application source addresses it. If `all` promoted the backend but failed before promoting the frontend, retry only `frontend`. If it is unclear whether a component was promoted, a smoke test failed after promotion, credentials or project linkage are wrong, application code must change, or the retry also fails, stop and report the intervention required. Never automatically roll back, redeploy a successful component, or start a task PR.

## Completion

Require workflow success and passing staged and promoted smoke-test steps for every requested component. Report the workflow URL, deployed commit, target, staged deployment URLs shown in the workflow summary, and smoke-test outcomes. Note that the validation worker remains separate and undeployed. If `main` advanced after dispatch, state that the reported deployment is the exact recorded SHA; do not silently deploy again.
