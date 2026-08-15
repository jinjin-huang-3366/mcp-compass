---
name: mcp-registry-ingestion
description: Work on MCP Registry ingestion, pagination, metadata normalization, status handling, synchronization, or Registry-related persistence for MCP Compass.
---

# Registry ingestion workflow

1. Read `/AGENTS.md`, `/docs/ARCHITECTURE.md`, and `/docs/DATA_MODEL.md`.
2. Treat the official MCP Registry as an eventually consistent ingestion source, not a high-availability runtime dependency.
3. Preserve raw source metadata alongside normalized fields where useful for forward compatibility.
4. Handle pagination/cursors and Registry preview-era schema changes defensively.
5. Respect `deprecated` and `deleted` server status; deleted items must not be recommended.
6. Keep sync idempotent: use stable server identity and upsert semantics.
7. Do not lose previously stored data merely because one upstream page fails.
8. Add fixture-driven mapper/client tests for every response-shape bug fixed.
9. Add Flyway migrations for persistence changes; never rely on Hibernate schema mutation.
