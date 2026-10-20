# Feedback & Community Research Agent

## Purpose and boundary

The local feedback agent finds high-signal MCP ecosystem discussions, extracts traceable product problems, clusters
independent evidence, identifies likely MCP Compass users, and prepares technically useful participation. It is not a
social publishing bot. Collection, analysis, local state updates, reports, and drafts are autonomous; each public
GitHub Discussion comment requires approval of the exact final package immediately before one mutation.

Typefully remains the separate managed outbound marketing path. This agent never publishes to Hacker News, has no
Reddit write path, sends no DMs, votes on nothing, and does not edit roadmap commitments.

## Architecture and local state

One Python standard-library CLI implements source adapters, deterministic relevance/extraction, clustering, scoring,
reports, proposals, approval validation, and GitHub publishing. Source settings are in `feedback/sources.json`.
Generated state is ignored by Git under `.local/feedback/`:

```text
inbox.jsonl       bounded collected source items
evidence.jsonl    structured evidence
clusters.json     aggregate problem clusters and trends
proposals.jsonl   exact reply proposals and hashes
approvals.jsonl   consumed/active exact-copy approvals
engagements.jsonl publication attempts and returned URLs/IDs
state.json        source checkpoints and status
```

Records use stable SHA-256-derived IDs, so reruns are idempotent. Only bounded excerpts and summaries are retained;
canonical URLs preserve traceability without copying entire discussions. Reports redact credential-like material and
omit unnecessary raw bodies. Delete `.local/feedback/` to discard local research state.

## Sources

- **GitHub:** configured public Discussions via `gh api graphql`, including bounded comments/replies and canonical
  metadata. The local `gh` credential store supplies authentication; no token is written by the agent.
- **Hacker News:** official Firebase API feeds/items, bounded by configured item/comment limits, strictly read-only.
- **Reddit:** adapter state only in V0.1. The default is `DISABLED_NOT_CONFIGURED`; no HTML fallback or write method
  exists. A future official authenticated read adapter can report `ENABLED` or `DISABLED_NO_CREDENTIALS`.

## Evidence, clustering, and scoring

Cheap deterministic concept matching runs before evidence extraction. Default collection and engagement thresholds
are 0.60 and 0.75. Evidence stores an underlying problem separately from possible feature solutions. Known problem
families cover discovery/selection, forbidden capability verification, trust/security, dead endpoints, unclear auth,
schema/compatibility, validation, and OpenAPI-to-MCP generation.

Clusters count unique authors, threads, and platforms conservatively; repetitions by one author do not inflate
independent evidence. A normal roadmap candidate needs at least three authors and two threads by default. Severe
single reports become `URGENT_REVIEW`, never automatic work. Potential-user scores (0–100) prioritize relevant
conversation only and must not drive unsolicited outreach. Seven- and thirty-day comparisons are labelled as small
samples when evidence is sparse.

## Commands

Run from the repository root with Python 3.10+:

```powershell
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py status
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py collect --since 24h
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py analyse
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py report
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py opportunities --limit 5
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py draft <evidence-id>
```

`report` writes a dated intelligence report plus `feedback/reports/roadmap-candidates.md` under local state by default.
Use `--output` only when deliberately producing a sanitized commit-ready aggregate. No command contacts an LLM.

## GitHub approval and publishing

First verify the local identity:

```powershell
gh auth status
```

`draft` prints a `COMMUNITY REPLY PROPOSAL` containing the exact destination, URL, known account, create/reply action,
text, product/link flags, rationale, and content hash. Stop and show that package to the operator. Approval of a draft
or general permission to engage is insufficient.

Only after the operator approves that exact package in the current conversation:

```powershell
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py approve <proposal-id> --hash <displayed-hash>
python .agents/skills/mcp-feedback-research/scripts/feedback_agent.py publish <proposal-id>
```

`publish` re-hashes the stored destination, operation, parent ID, and final text; checks that approval is active and
unexpired; runs `gh auth status`; makes one `addDiscussionComment` mutation; records the returned comment URL/ID; and
consumes approval. Editing any field invalidates it. Top-level comments omit `replyToId`; replies include the exact
target comment node ID. A failed authentication or mutation preserves the proposal and records no success. Run no
second publish implicitly.

Authentication failure occurs before approval consumption. Once a mutation is attempted, the approval is consumed.
An error or ambiguous response is recorded as `PUBLISH_FAILED_OR_UNCERTAIN`; reconcile the target manually and do not
retry the same proposal, because the first mutation may have succeeded even if its response was lost.

## Reports and roadmap recommendations

The intelligence report answers recurring/increasing problems, likely target users, useful participation locations,
problems below the evidence threshold, and qualified roadmap candidates. `roadmap-candidates.md` is a recommendation
artifact only. An operator decides whether to add a separate `PLANS.md` task.

## Troubleshooting

- `PUBLISHING_UNAVAILABLE`: run `gh auth status`, then `gh auth login` outside the repository if needed. Drafting and
  HN collection continue without GitHub publishing.
- GitHub repository has Discussions disabled: collection records a source error and continues with other sources.
- Network/API failure: malformed or failed items are skipped independently; prior local evidence is retained.
- Empty report: lower thresholds only after inspecting false negatives. “No discussions worth responding to” is a
  valid result.
- Reddit disabled: this is expected until an official authenticated API is explicitly configured.

## Adding another source

Implement the common adapter output, canonical URL/thread identity, bounded retention, and explicit auth/status
handling. Add fixture-based tests for malformed input, time filtering, idempotence, and URLs. Keep read and write
capabilities separate, document platform rules, and require an exact single-destination approval immediately before
any future public mutation.
