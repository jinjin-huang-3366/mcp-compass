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
- `$github-pr-workflow`
- `$ci-failure-triage`

Suggested first prompt:
```text
$mcp-compass-development
Implement the next unchecked V0.1 item in PLANS.md. Read only the relevant docs first, make a small coherent change, test it, and update PLANS.md.
```

## GitHub Copilot
This repo contains `.github/copilot-instructions.md` and path-specific `.github/instructions/*.instructions.md`.

GitHub also supports project Agent Skills in `.agents/skills`, so the same project-local skill set can be shared rather than duplicated.

If your GitHub CLI version supports Agent Skills, useful commands include searching, previewing, and installing skills. Always preview third-party skills before installing because a skill can contain instructions/scripts with security implications.

## Optional GitHub-focused skills
The repo includes two safe instruction-only skills:
- `github-pr-workflow`
- `ci-failure-triage`

They intentionally do not pre-approve shell execution.

## Why no `.agent/marketplace.json`?
That is not the current Codex project-skill convention. See `.agent/README.md` for the naming clarification.
