# ADR 0005: Generate TypeScript projects from a data-driven runtime pack

## Status
Accepted.

## Context

The first GEN-04 implementation emitted TypeScript by assembling Java text blocks and per-tool source fragments. That
proved the contract-to-project boundary, but it coupled TypeScript maintenance to Java code, made generated-source
review difficult, and required careful source-language escaping for every reviewed contract value. General-purpose
templates would separate files from Java, but would still mix untrusted contract data with source rendering and add a
second language for generator behavior.

## Decision

Bundle a versioned TypeScript runtime pack under backend classpath resources. The runtime pack contains reviewed,
static project sources and configuration. Java validates the approved contract, derives the project name, updates the
structured `package.json`, serializes the approved contract into `contract.json`, and copies the static assets into the
in-memory project manifest. The TypeScript runtime loads `contract.json` as data and registers tools dynamically.

Runtime packs are application-owned and classpath-only. MCP Compass does not accept user-supplied templates or runtime
packs, and generation does not write, install, compile, or execute the resulting project.

## Consequences

TypeScript behavior can be reviewed and changed as ordinary `.ts` resource files without editing Java string
literals. Contract text cannot become TypeScript syntax, reducing injection and escaping risk. A runtime-pack change
affects every generated project for that version, so each pack needs deterministic fixture tests and an explicit
version boundary. Contract-specific source specialization is intentionally deferred until evidence shows the generic
runtime cannot meet a supported integration need.
