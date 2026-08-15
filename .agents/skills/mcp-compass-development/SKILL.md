---
name: mcp-compass-development
description: Implement or plan MCP Compass product work. Use for repository features, architecture changes, backlog tasks, refactors, or when asked to continue building the MCP discovery/generation platform.
---

# MCP Compass development workflow

1. Read `/AGENTS.md` and `/PLANS.md`.
2. Read the relevant module `AGENTS.md` and the smallest relevant design document under `/docs`.
3. Identify the current milestone and avoid pulling later-roadmap features forward without need.
4. Before editing, state the smallest vertical change that will satisfy the task.
5. Preserve the product invariants:
   - search persisted Registry data rather than synchronously proxying the Registry;
   - reuse before generate;
   - deterministic ranking logic must be testable;
   - never execute untrusted MCP code in the application process;
   - generator work is contract-first.
6. Implement the change with tests.
7. Run the narrowest relevant build/test commands.
8. Update `/PLANS.md` if task state or sequencing changed.
9. Add/update an ADR for a durable architecture choice.

For architecture reminders, read `references/architecture-summary.md` in this skill directory.
