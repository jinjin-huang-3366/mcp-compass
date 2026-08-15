# Backend-specific agent guidance

These rules extend the root `AGENTS.md` for `backend/`.

- Java 21 and Spring Boot only; do not introduce Kotlin unless explicitly decided by ADR.
- Use records for API DTOs/value objects where practical.
- Constructor injection only.
- Keep `registry`, `requirement`, `search`, `ranking`, and `server` package boundaries clear.
- External HTTP clients must set sensible connect/read timeouts and be mockable.
- Do not make Hibernate auto-create/update schema; Flyway owns schema evolution.
- Unit-test deterministic ranking/parsing without starting Spring.
- Do not add arbitrary process execution, Docker control, or MCP package execution to this module during V0.1.
