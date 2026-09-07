# Node.js generation link change

## Status

The change is implemented on `task/clarify-mcp-generation-link` and is awaiting manual review and merge in
[pull request 79](https://github.com/jinjin-huang-3366/mcp-compass/pull/79). This is a small unplanned UX correction, so
the pull request intentionally has no `PLANS.md` completion marker.

## User-facing change

- The home-page link now reads **Generate a Node.js MCP server from OpenAPI** and routes to `/generate`.
- The generation page explains that the contract-first review ends with a downloadable, GitHub-ready
  Node.js/TypeScript MCP server.
- The obsolete home-page note claiming that MCP generation would come later has been removed.
- `docs/API.md` records the same review-and-export workflow.

## Verification recorded before this report

- Local frontend tests: 8 files and 15 tests passed, including the new home-page regression test.
- Local frontend lint and production build passed; `/` and `/generate` were statically generated.
- [Task workflow run 34060795979](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34060795979)
  passed full repository validation, opened the pull request, started baseline CI, and sent the email summary.
- [Baseline CI run 34060894843](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34060894843)
  passed backend, web, CLI, and workflow-automation jobs for implementation commit
  `5d5c8f2b5807b2477a3f44c8788658d963ec408b`.

The documentation-only commit that adds this report still requires final-head CI before the pull request handoff is
complete.
