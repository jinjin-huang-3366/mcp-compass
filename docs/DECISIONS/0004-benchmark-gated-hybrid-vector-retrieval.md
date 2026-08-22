# ADR 0004: Gate hybrid vector retrieval behind the lexical baseline

## Status
Accepted.

## Context

The versioned `lexical-relevance-v1` fixture and checked-in report establish the V0.1 relevance baseline. That report
shows constraint and capability-ranking failures but does not, by itself, prove that embeddings improve search. MCP
Compass already persists Registry metadata locally and its PostgreSQL service includes pgvector.

## Decision

Add vector candidate retrieval as an opt-in extension to lexical retrieval, disabled by default. Use the OpenAI
embeddings HTTP API behind a local interface, request 384 dimensions, persist the configured model identifier beside
each server vector, and compare only vectors from the same model. Batch server embeddings after each Registry page is
persisted. Retrieve bounded cosine-nearest candidates through an HNSW index, exclude candidates below a configurable
similarity floor, merge them with lexical candidates, and keep deterministic capability-aware ranking as the final
stage. Provider, indexing, or vector-query failures fall back to lexical retrieval.

## Consequences

Vector retrieval can be evaluated without making user search depend on the embedding provider or changing the default
baseline. Existing Registry rows receive embeddings on a subsequent sync. A model change requires re-embedding rows;
the model filter prevents mixed-space comparisons during that transition. Enabling vector retrieval by default needs a
provider/model-specific relevance, latency, and cost report that demonstrates improvement over the checked-in lexical
baseline.
