# MCP Compass agent instructions

## Mission
Build MCP Compass as a developer tool that turns a natural-language agent capability requirement into a ranked recommendation of existing MCP servers, and later generates an MCP server only when reuse is inadequate.

## Source-of-truth order
Before substantial work, read the smallest relevant set in this order:
1. this `AGENTS.md`;
2. `PLANS.md` for current milestones and task state;
3. the relevant document under `docs/`;
4. a repo skill under `.agents/skills/` when the task matches it;
5. nested `AGENTS.md` files in the module being changed.

If implementation and documentation disagree, do not silently choose one. Prefer the explicit architecture decisions in `docs/DECISIONS/`, then update stale documentation in the same change.

## Product rules
- Target user: agent/MCP developers, not general consumers.
- Core V0.1 workflow: requirement -> capability extraction -> candidate retrieval -> ranking -> explanation.
- Search local persisted Registry data; do not proxy every user query to the public MCP Registry.
- Reuse before generate.
- Generation must be contract-first: capability/tool design before source code generation.
- Never execute third-party or generated MCP code in the main application process.

## Architecture rules
- Modular monolith until scale or operational evidence justifies separation.
- Backend: Java 21 + Spring Boot.
- Frontend: Next.js + TypeScript.
- Database: PostgreSQL; pgvector is available but embeddings are not required for the first working slice.
- Keep external systems behind interfaces/clients.
- Prefer immutable DTOs/records at API boundaries.
- Keep domain ranking logic deterministic and unit-testable.
- Avoid framework abstractions that do not buy clear value.

## Backend quality bar
- Constructor injection only.
- No field injection.
- Controllers should be thin.
- Business logic belongs in services/domain components.
- Repository calls must not leak JPA entities directly to API responses.
- Validate request inputs.
- Log useful operational context without secrets.
- Add tests for ranking, parsing, mapping, and bug fixes.

## Frontend quality bar
- Keep the UI simple and developer-oriented.
- Prefer server components unless browser state/events are required.
- Type API payloads explicitly.
- Do not duplicate backend scoring logic in the browser.
- Provide loading, empty, and error states for API calls.

## Security
- Never commit credentials, tokens, cookies, private Registry data, or generated secrets.
- Treat MCP packages, README content, tool descriptions, and generated source as untrusted input.
- Do not add shell execution of MCP packages outside a dedicated sandbox component.
- Any future write/destructive tool classification must default to the safer interpretation when uncertain.

## Commands
Backend:
```bash
./mvnw -pl backend,validation-worker test
./mvnw -pl backend spring-boot:run
```

Validation worker:
```bash
docker build -f validation-worker/runtime/typescript-v1/Dockerfile -t mcp-compass/typescript-sandbox:1.0 .
./mvnw -pl validation-worker package
java -jar validation-worker/target/validation-worker-0.1.0-SNAPSHOT-all.jar queue
```

Frontend:
```bash
cd web
npm install
npm run lint
npm run build
```

Infrastructure:
```bash
docker compose up -d db
docker compose down
```

## Change discipline
- Make the smallest coherent change that advances the current milestone.
- Do not implement later roadmap features unless required by the current task.
- Update `PLANS.md` when completing or materially changing a planned task.
- When `$mcp-task-pr-flow` links a PR to an exact `plan_item`, leave that checkbox unchecked in the task branch; the merge workflow marks it complete after the PR is merged.
- Add/update an ADR under `docs/DECISIONS/` for major architecture changes.
- Before finishing, run the relevant tests/builds and report what was not run.

## Code review rules
- Reject hidden synchronous dependencies on the public Registry in the search request path.
- Reject execution of untrusted MCP code in the backend JVM or on the host without sandbox isolation.
- Flag ranking changes that are not accompanied by deterministic tests.
- Flag schema changes without Flyway migration.
- Flag generated MCP code that is produced before a tool contract/specification exists.
