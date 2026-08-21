# ADR 0002: Persist Registry data locally

## Status
Accepted.

## Context
The official MCP Registry is designed for downstream aggregators and does not promise search-path uptime/data durability.

## Decision
Periodically ingest Registry metadata into MCP Compass PostgreSQL. User searches query local persisted data.

## Consequences
We own freshness, checkpointing, schema compatibility, and storage; user search remains available independently of Registry request-time availability.

## Verification
`SearchRegistryIndependenceTest` exercises search against persisted candidates and guards the user-search
controller and service against dependencies on Registry HTTP or synchronization components. Registry
availability is therefore relevant to background freshness, not request-time search availability.
