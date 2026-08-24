# MCP Compass

MCP Compass is a developer-focused MCP intelligence tool:

> Describe the capability your agent needs. MCP Compass finds and ranks the best existing MCP server. If no server is good enough, it can turn an approved API/OpenAPI tool contract into a TypeScript MCP server project.

## Current status

This repository has a working discovery vertical slice plus contract-first generation foundations:

1. ingest public MCP Registry metadata into PostgreSQL;
2. normalize searchable MCP server metadata;
3. analyse a developer's natural-language requirement;
4. retrieve candidate MCP servers;
5. rank and explain matches;
6. expose the result through a REST API and a simple Next.js UI;
7. accept OpenAPI sources, review an MCP tool contract, and generate an in-memory TypeScript project manifest from a
   versioned, data-driven runtime pack with locked dependencies and generated unit tests verified in CI;
8. download the approved project as a GitHub-ready ZIP with repository ignores and its own CI workflow.

Runtime inspection and security sandboxing remain planned. Vector candidate retrieval is
available as an opt-in, benchmark-gated extension to the default lexical search path.

## Stack

- Java 21
- Spring Boot 4.1
- Maven
- PostgreSQL 18 + pgvector
- Flyway
- Next.js 16 / React 19 / TypeScript
- Docker Compose

## Open in IntelliJ IDEA

1. Unzip/open the repository root in IntelliJ IDEA.
2. IntelliJ should detect the root `pom.xml` and the `backend` Maven module.
3. Use Java 21 as the project SDK.
4. Start infrastructure:

   ```bash
   docker compose up -d db
   ```

5. Run the backend from IntelliJ using `McpCompassApplication`, or:

   ```bash
   ./mvnw -pl backend spring-boot:run
   ```

   The Maven development runner activates the `local` Spring profile automatically. For an IntelliJ run configuration, add `local` under **Active profiles** so the local-only Registry sync endpoint is registered.

6. In another terminal:

   ```bash
   cd web
   npm install
   npm run dev
   ```

7. Open `http://localhost:3000`.

For local Registry sync, confirm the startup log lists `local` as active, then POST to:

```bash
curl -X POST "http://localhost:8080/api/v1/dev/registry/sync?maxPages=1"
```

## AI-agent setup

Start with:

- `AGENTS.md` — repository-wide instructions for Codex and other coding agents.
- `PLANS.md` — living implementation plan and V0.1 backlog.
- `.agents/skills/` — repo-scoped Agent Skills. This is the current Codex-supported location and is also supported by GitHub Copilot agent skills.
- `.codex/config.toml` — conservative project-level Codex settings.
- `.github/copilot-instructions.md` — GitHub Copilot repository instructions.
- `.github/instructions/` — path-specific Copilot guidance.
- `docs/AI_AGENT_SETUP.md` — how to use the supplied skills.

The `.agent/` directory exists only to explain the older/singular naming. Do not put Codex project skills there.

## Automated task pull requests

The `Codex task pull request` workflow validates one task branch already implemented and pushed by the active local Codex session, opens a pull request without merging it, and emails the local Codex summary. It never calls the OpenAI API, selects another backlog task, or starts follow-up work automatically.

From Codex, invoke `$mcp-task-pr-flow` with the task description. Codex creates a branch from the requested base, implements the task, synchronizes with the latest base, validates the combined result, commits and pushes only the intended changes, then dispatches and monitors this workflow.

For concurrent delivery, use the canonical [parallel delivery groups](PLANS.md#parallel-delivery-groups). Invoke `$mcp-task-batch-flow` with one `PG-*` ID to coordinate the whole ready group, or start one isolated `$mcp-task-pr-flow` session per task ID. The batch skill still gives every task its own branch, workflow run, email, plan marker, and pull request.

1. Add `GMAIL_ADDRESS` and `GMAIL_APP_PASSWORD` repository secrets under **Settings > Secrets and variables > Actions**. Use a Google App Password rather than the Gmail account password. No OpenAI API key is required.
2. Under **Settings > Actions > General**, allow GitHub Actions to create pull requests and grant workflows read/write permissions.
3. Open **Actions > Codex task pull request > Run workflow**.
4. Enter the task instructions, the existing pushed task branch, the base branch, the pull request title, the local Codex summary, one concrete before/after or request/response example, complete task-specific desk-testing guidance, and—when the task matches one unchecked plan entry—the exact `PLANS.md` item text. Desk testing should include ordered commands or actions, expected results, and any steps that could not be run with the reason.
5. Review the generated pull request and email summary, then merge the pull request manually when it is ready.
6. Start another workflow run only when the next task should begin.

The local Codex session uses the developer's existing GitHub authentication to push the task branch. GitHub supplies the short-lived `GITHUB_TOKEN` used to open the pull request and start baseline CI; no personal access token or OpenAI API key is stored as a repository secret. The workflow checks that the task branch differs from the base and contains the latest fetched base commit both before validation and immediately before pull request creation. It runs the backend tests plus frontend lint and build, and copies the concrete example and complete desk-testing guidance into both the pull request and email summary. On failure, Codex may retry twice after diagnosing a transient cause or applying an in-scope correction. Retry runs update the existing matching pull request instead of opening duplicates. Codex completes the handoff only after GitHub reports the final pull request head mergeable against the current base and CI passes on that head. It automatically prepends steps to start PostgreSQL, the backend, and the frontend; check both services are ready; and stop them after testing.

Conflict freedom is verified at handoff, not guaranteed forever. If the base changes later—for example, after another batch PR merges—the affected task branch must be synchronized and revalidated again before manual merge.

When an exact plan item is supplied, the task workflow validates that it is currently unchecked and adds a machine-readable marker to the pull request. After that pull request is merged, `Mark merged plan item and group complete` changes only the matching `- [ ]` entry to `- [x]` on the base branch and derives the parallel delivery group's `Status` count from all of its canonical task checkboxes. The group is marked complete only after every listed task pull request has been merged. Pull requests without the marker are ignored, and ambiguous or unknown items fail without modifying the plan.

## Important design constraints

- **Reuse before generate.** Never generate a new MCP if a strong existing server satisfies the requirement.
- Keep V0.x as a **modular monolith**.
- Do not execute untrusted MCP code in the main backend process.
- The public Registry is an ingestion source, not a synchronous dependency for end-user searches.
- LLMs should perform semantic tasks; deterministic code should perform scoring, validation, persistence, and policy enforcement.

Vector retrieval remains disabled by default. When enabled, Registry sync batches server name/title/description
embeddings into PostgreSQL and search merges cosine-nearest candidates with lexical candidates. Provider or vector
query failures fall back to lexical retrieval. See `docs/DEVELOPMENT.md` for configuration and
`docs/reports/LEXICAL_RANKING_BASELINE_V1.md` for the baseline that must be preserved when evaluating a provider.

## Documentation

- `docs/ARCHITECTURE.md`
- `docs/PROJECT_PLAN.md`
- `docs/DATA_MODEL.md`
- `docs/API.md`
- `docs/SECURITY.md`
- `docs/DEVELOPMENT.md`
- `docs/AI_AGENT_SETUP.md`
- `docs/REFERENCES.md`

## First Codex prompt

From the repo root, a good first task is:

```text
$mcp-compass-development
Read AGENTS.md, PLANS.md and docs/ARCHITECTURE.md. Implement the next unchecked V0.1 task in PLANS.md. Keep the change small, run the relevant tests, and update PLANS.md with what changed.
```
