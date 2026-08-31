# AI coding-agent setup

## Codex
Current repo-scoped conventions used here:
- `AGENTS.md`: persistent project guidance loaded before work;
- nested `backend/AGENTS.md` and `web/AGENTS.md`: module-specific guidance;
- `.codex/config.toml`: project settings (loaded only for a trusted project);
- `.agents/skills/<name>/SKILL.md`: local Agent Skills.

In Codex CLI/IDE, skills can be selected explicitly with `$skill-name` or discovered automatically from their descriptions.

Useful project skills:
- `$mcp-compass-development`
- `$mcp-registry-ingestion`
- `$mcp-search-ranking`
- `$mcp-generator` (V0.3+)
- `$mcp-task-pr-flow`
- `$mcp-task-batch-flow`
- `$mcp-vercel-deploy`
- `$github-pr-workflow`
- `$ci-failure-triage`

Suggested first prompt:
```text
$mcp-compass-development
Implement the next unchecked V0.1 item in PLANS.md. Read only the relevant docs first, make a small coherent change, test it, and update PLANS.md.
```

### Parallel task delivery

`PLANS.md` contains the canonical **Parallel delivery groups** index. A group identifies dependency-independent tasks that may be developed at the same time. `$mcp-task-batch-flow` resolves one group and coordinates isolated child agents; every child uses `$mcp-task-pr-flow`, which remains a single-task workflow with one branch, workflow run, email, plan marker, and pull request.

To fan out the complete ready group with one command:

```text
Use $mcp-task-batch-flow for PG-01.
```

To run individual tasks instead, start separate sessions with prompts such as:

```text
Use $mcp-task-pr-flow for SRCH-04 from PG-01.
```

```text
Use $mcp-task-pr-flow for API-05 from PG-01.
```

The batch skill confirms every task's dependencies against the latest `main` before creating any branch. Each child then synchronizes with the latest base before dispatch, and the coordinator requires GitHub mergeability plus passing CI on the final PR head. Group membership describes dependency safety, not permanent merge-conflict safety: merging one sibling can advance `main`, so re-synchronize any affected open PR before its manual merge.

### Production deployment

Deploy the latest `main` commit to both existing Vercel projects with:

```text
Use $mcp-vercel-deploy to ensure CI passes for the latest main commit, then deploy it to all production Vercel projects.
```

The command reuses, waits for, or starts CI for the exact latest `main` commit, rechecks that `main` did not advance, and then dispatches the manual deployment workflow. It stages and smoke-tests the backend before promotion, then does the same for the frontend. Request only `backend` or `frontend` for an intentionally component-scoped deployment. Required GitHub Actions secret names and first-time setup are documented in `docs/DEPLOYMENT.md`.

## GitHub Copilot
This repo contains `.github/copilot-instructions.md` and path-specific `.github/instructions/*.instructions.md`.

GitHub also supports project Agent Skills in `.agents/skills`, so the same project-local skill set can be shared rather than duplicated.

If your GitHub CLI version supports Agent Skills, useful commands include searching, previewing, and installing skills. Always preview third-party skills before installing because a skill can contain instructions/scripts with security implications.

## Optional GitHub-focused skills
The repo includes these GitHub-focused instruction-only skills:
- `mcp-task-pr-flow`
- `mcp-task-batch-flow`
- `github-pr-workflow`
- `ci-failure-triage`

They intentionally do not pre-approve shell execution.

## Why no `.agent/marketplace.json`?
That is not the current Codex project-skill convention. See `.agent/README.md` for the naming clarification.
