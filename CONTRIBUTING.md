# Contributing to MCP Compass

MCP Compass is built for agent and MCP developers. Contributions should advance its requirement-to-recommendation
workflow or its reuse-first, contract-first generation path without weakening the security boundary.

## Before opening an issue

Use [GitHub Discussions](https://github.com/jinjin-huang-3366/mcp-compass/discussions) for community conversations:

- **Q&A** for usage and setup questions;
- **Ideas** for early proposals that are not ready to become scoped work;
- **Show and tell** for MCP Compass integrations, experiments, and demos;
- **General** for project-relevant conversation that does not fit those categories;
- **Polls** only for focused community choices; and
- **Announcements** for maintainer-authored project updates.

Use an issue instead when there is a reproducible defect or a focused, actionable feature request. Repository owner
[`@jinjin-huang-3366`](https://github.com/jinjin-huang-3366) owns launch moderation and will recategorize, lock, or
remove conversations that expose secrets, duplicate tracked work, or fall outside the developer-tool scope.

- Use the [bug report form](https://github.com/jinjin-huang-3366/mcp-compass/issues/new?template=bug_report.yml)
  for reproducible defects.
- Use the [feature request form](https://github.com/jinjin-huang-3366/mcp-compass/issues/new?template=feature_request.yml)
  for one focused developer outcome and its use case.
- Search [existing issues](https://github.com/jinjin-huang-3366/mcp-compass/issues) before filing a duplicate.
- Never include tokens, credentials, private Registry data, proprietary API specifications, or private tool schemas.

Security vulnerabilities do not belong in public issues. Submit them through GitHub's
[private security advisory flow](https://github.com/jinjin-huang-3366/mcp-compass/security/advisories/new). Include the
affected component, impact, and minimal reproduction, but no live credentials or sensitive user data.

## Development setup

Prerequisites are Java 21, Node.js 22+, Docker, and Git.

```bash
git clone https://github.com/jinjin-huang-3366/mcp-compass.git
cd mcp-compass
docker compose up -d db
./mvnw -pl backend spring-boot:run
```

In a second terminal:

```bash
cd web
cp .env.local.example .env.local
npm install
npm run dev
```

Open <http://localhost:3000>. The [development guide](docs/DEVELOPMENT.md) includes PowerShell commands, Registry
seeding, environment variables, CLI setup, and validation-worker setup.

## Make a focused change

1. Read [AGENTS.md](AGENTS.md), [PLANS.md](PLANS.md), the smallest relevant document under `docs/`, and any nested
   module instructions.
2. Keep one coherent behavior per pull request. Do not pull later-roadmap work forward.
3. Add deterministic tests for ranking, parsing, mapping, and bug fixes.
4. Preserve the core invariants: search persisted Registry data, reuse before generate, review a contract before
   generation, and execute untrusted code only in the isolated worker.
5. Update user-facing documentation and `CHANGELOG.md` when behavior changes. Add a decision record only for a durable
   architecture choice.

## Validate

Run the checks relevant to your change and report anything you could not run:

```bash
./mvnw -pl backend,validation-worker test
cd web && npm ci && npm run lint && npm run build
cd cli && npm ci && npm run lint && npm test
python -m unittest discover -s .github/scripts -p 'test_*.py'
```

Documentation-only changes should at minimum check Markdown links, rendered images, issue-form YAML, and
`git diff --check`. The full commands above run in baseline CI.

## Pull requests

Explain the developer-visible outcome, include a concrete before/after or request/response example, and provide
ordered desk-testing steps with expected results. Link the issue or exact `PLANS.md` task when applicable. Keep
generated files, credentials, and unrelated formatting changes out of the branch.

Maintainers may use the repository's one-task `$mcp-task-pr-flow`; its pull requests are still reviewed and merged
manually.
