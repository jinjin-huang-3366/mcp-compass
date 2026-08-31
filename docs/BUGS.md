# User-reported bugs

This log records bugs reported by the MCP Compass user during Codex development sessions. Add each new report with a stable ID, reproduction summary, expected behavior, status, and resolution reference.

## BUG-001 — Project export rejected approved OpenAPI contracts

- **Reported:** 2026-08-29
- **Status:** Resolved in pull request #52
- **Observed:** Project export returned `400 INVALID_APPROVED_CONTRACT` with `Every approved tool needs object input and declared output JSON schemas.` for an approved contract derived from an OpenAPI document that used schema references.
- **Expected:** A contract approved from a valid supported OpenAPI document should export a TypeScript MCP project.
- **Resolution:** Resolve bounded local OpenAPI schema references before approval/export and accept declared composition schemas.

## BUG-002 — Returning from MCP details discarded search results

- **Reported:** 2026-08-30
- **Status:** Fix pending review
- **Observed:** After opening an MCP result and pressing **Back to search**, the previous requirement, result page, and visible matches were gone.
- **Expected:** Returning from an MCP detail page should restore the same requirement and result page.
- **Resolution:** Carry the active `q` and `page` parameters into detail links and use them to construct the detail page's back link.

## BUG-003 — Discovered MCP servers did not link to their source repositories

- **Reported:** 2026-08-31
- **Status:** Fix pending review
- **Observed:** Search results could claim that a public source repository was declared, but neither the result card nor the MCP detail page exposed the persisted repository URL.
- **Expected:** When the Registry publisher supplies a valid HTTP(S) repository URL, developers should be able to open it from both the search result and MCP detail page.
- **Resolution:** Add `repositoryUrl` to the search and detail API responses and render guarded external source links. Missing or non-HTTP(S) values remain non-clickable.
