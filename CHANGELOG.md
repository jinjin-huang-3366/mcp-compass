# Changelog

Notable user-facing changes to MCP Compass are documented here. This is a curated release record, not a copy of the
internal milestone plan. The first public version and date will be chosen during the tagged-release task.

## Unreleased

### Added

- Requirement-first discovery across locally synchronized MCP Registry metadata.
- Structured intent with required and forbidden capabilities, hard-condition filtering, ranked matches, score
  explanations, and calibrated no-strong-match abstention.
- MCP detail pages, shareable search URLs, CLI search and generation commands, and IDE launch integration.
- OpenAPI ingestion followed by editable tool-contract review, TypeScript project generation, and GitHub-ready ZIP
  export with locked dependencies and CI.
- Separate queued validation worker with ephemeral container controls, MCP Inspector protocol checks, and conservative
  tool-risk reports.
- Contributor guidance, public bug and feature issue forms, and accessible launch screenshots.

### Changed

- Search documents combine server metadata, normalized capabilities, discoverable static tool metadata, and bounded
  repository enrichment. Optional vector retrieval retains a deterministic lexical fallback.
- Capability coverage remains the dominant ranking concern; maintenance, trust, and installability provide secondary
  evidence.

### Known limitations

- Registry data is a synchronized snapshot, not complete live ecosystem coverage.
- The stable deployed frontend loads publicly, but its search action currently targets a protected immutable backend
  deployment. MKT-14 must correct and verify the canonical public demo before external launch.
- Hosted validation requires a separately operated isolated worker; queued jobs and reports are not security
  certification.
- Generation currently targets the repository-owned TypeScript runtime pack.
- Optional LLM analysis, embeddings, and GitHub enrichment depend on separately configured providers.

See the [demo-quality report](docs/reports/DEMO_QUALITY_GATE_V1.md) for dated evaluation evidence and
[PLANS.md](PLANS.md) for implementation status.
