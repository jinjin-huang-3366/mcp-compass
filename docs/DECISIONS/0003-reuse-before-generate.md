# ADR 0003: Reuse before generate

## Status
Accepted.

## Decision
MCP generation is a fallback only after search/ranking shows no sufficiently suitable existing server. Generation is contract-first rather than one-shot source-code prompting.

## Consequences
The product remains focused on reducing developer effort rather than increasing duplicate MCP implementations. Search quality becomes a core asset.
