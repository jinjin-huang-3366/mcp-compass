# About this directory

This singular `.agent/` directory is intentionally **not** used for Codex repo skills.

Current Codex repository conventions are:

- `AGENTS.md` for persistent project instructions;
- `.codex/config.toml` for project-scoped Codex configuration;
- `.agents/skills/<skill-name>/SKILL.md` for repository Agent Skills.

The similar-looking `.agent/marketplace.json` pattern belongs to other/older agent tooling and should not be treated as the Codex project skill location.

This file is kept because developers often look for `.agent/` after seeing older examples. The actual project skills live in `../.agents/skills/`.
