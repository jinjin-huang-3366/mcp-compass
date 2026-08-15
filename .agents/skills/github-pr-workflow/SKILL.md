---
name: github-pr-workflow
description: Prepare MCP Compass changes for GitHub: review scope, tests, commit readiness, pull-request summary, risk notes, and reviewer guidance.
---

# GitHub PR workflow

1. Read `/AGENTS.md` and inspect the current diff.
2. Confirm the change is limited to one coherent concern; call out unrelated edits.
3. Run relevant tests/builds for touched modules.
4. Check for secrets, generated build output, accidental `.idea` files, and large artifacts.
5. Confirm schema changes have Flyway migrations and ranking changes have tests.
6. Summarize:
   - problem;
   - implementation;
   - tests run;
   - risks/trade-offs;
   - follow-up work explicitly left out.
7. Keep commit/PR messages focused on behavior rather than implementation trivia.
8. Never claim tests passed if they were not executed.
