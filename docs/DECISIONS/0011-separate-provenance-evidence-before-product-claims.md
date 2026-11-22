# ADR 0011: Separate provenance evidence before product claims

## Status

Accepted as an evaluation decision for EVI-07; no production verifier is authorized by this ADR.

## Context

The official MCP Registry authenticates publisher namespaces and checks package ownership at publication. Its response also contains publisher-declared package and repository fields. MCP Compass persists the response but currently exposes only a coarse official-metadata boolean, repository URL, and package count. None of those fields proves an artifact digest or a build-from-source claim. Package integrity and attestation mechanisms vary by ecosystem.

## Decision

Treat (1) Registry namespace identity, (2) Registry/package marker and source declarations, and (3) exact artifact digest and verified build attestation as separate evidence axes. Missing, mismatched, or unverified inputs must never be collapsed into a positive provenance or safety badge. A package-side repository field is a second declaration, not a verified build link. A matching publisher-supplied hash is an integrity check against that declaration, not proof of benign contents or source origin.

Proceed only with a future declaration-level explanation from known official Registry data. Defer artifact and attestation verification pending a bounded, ecosystem-specific prototype with exact bytes and cryptographic verification. Do not require an unsettled attestation standard or change search ranking on this evaluation.

## Evidence and consequences

The [feasibility report](../reports/PROVENANCE_FEASIBILITY_V1.md) records upstream documentation, current field mapping, controlled missing/tampered/mismatched fixtures, and the go/no-go gate. Future UI/API work must carry origin, method, subject, and observation time per evidence item. The current product remains unchanged by this decision.
