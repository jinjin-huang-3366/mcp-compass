---
name: ci-failure-triage
description: Diagnose and fix MCP Compass CI or GitHub Actions failures, including Maven tests, frontend lint/build, and workflow configuration.
---

# CI failure triage

1. Identify the first causal failure rather than fixing downstream noise.
2. Reproduce locally with the same module command where possible.
3. For backend failures run `./mvnw -pl backend test` first.
4. For frontend failures run `npm ci`, `npm run lint`, then `npm run build` from `web/`.
5. Do not weaken tests, linting, or security checks merely to make CI green.
6. If a dependency/runtime version differs between CI and local, make the version explicit in project configuration.
7. After the fix, rerun the narrow failing command and then the relevant full module checks.
