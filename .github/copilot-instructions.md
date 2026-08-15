# MCP Compass GitHub Copilot instructions

MCP Compass is a developer tool for finding/ranking existing MCP servers and later generating an MCP only when reuse is inadequate.

Read `AGENTS.md` and `PLANS.md` before broad changes. Follow nested module instructions when present.

Core rules:
- keep the architecture a modular monolith for V0.x;
- user search reads locally persisted Registry data, not the public Registry synchronously;
- reuse before generate;
- ranking logic must be deterministic/testable and return explanations;
- never execute untrusted MCP packages/generated code in the main backend process;
- database changes require Flyway migrations;
- generator work must be contract-first;
- never commit credentials or secrets.

Backend is Java 21/Spring Boot; frontend is Next.js/TypeScript; PostgreSQL is the source of truth. Keep controllers thin, use constructor injection, and keep API DTOs separate from JPA entities.

Run relevant tests/builds before presenting work and state anything not run.
