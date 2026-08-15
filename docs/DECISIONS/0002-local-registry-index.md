# ADR 0002: Persist Registry data locally

## Status
Accepted.

## Context
The official MCP Registry is designed for downstream aggregators and does not promise search-path uptime/data durability.

## Decision
Periodically ingest Registry metadata into MCP Compass PostgreSQL. User searches query local persisted data.

## Consequences
We own freshness, checkpointing, schema compatibility, and storage; user search remains available independently of Registry request-time availability.
