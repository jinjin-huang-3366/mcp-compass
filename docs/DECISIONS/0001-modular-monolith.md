# ADR 0001: Start as a modular monolith

## Status
Accepted.

## Context
The product has multiple logical responsibilities but a single developer/product team and rapidly evolving boundaries.

## Decision
Use one Spring Boot deployable backend with package/module boundaries. Do not split search, registry, ranking, generation, or validation into network microservices during V0.x.

## Consequences
Faster iteration and simpler local development. Components must still communicate through clear interfaces so a later extraction is possible if operational evidence justifies it.
