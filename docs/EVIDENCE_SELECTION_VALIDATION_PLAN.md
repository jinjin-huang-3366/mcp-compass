# Evidence-backed selection: build, validate, then share

This is the execution guide for the `VLD-*` tasks in [PLANS.md](../PLANS.md). It is a four-to-six-week validation sprint, not approval to build every V0.8 candidate or to publish a new campaign. The [six-month ecosystem review](../feedback/reports/2026-10-25-six-month-market-analysis.md) is directional evidence about MCP selection problems, not evidence that developers already adopt MCP Compass. The [Reddit comment](https://www.reddit.com/r/mcp/comments/1wmrqiq/comment/pb9vjhh/) is one author's report that a self-described read-only server might still expose a write path.

## Build order and proof

1. **Correct the claim (`EVI-01`).** Separate Registry declarations, normalized tool descriptions, observed `tools/list`, and server-enforced permission evidence in API and UI. A forbidden write capability without enforcement evidence must remain `UNKNOWN/NOT_VERIFIED`, not “verified read-only.” Preserve conservative exclusions and deterministic counterexample tests.
2. **Add a useful observation (`EVI-04`).** A bounded, rate-limited background check records a time-stamped remote state: reachable, auth required, protocol mismatch, unreachable, or not checked. A 401/403 means gated, not dead. Never invoke `tools/call` or add a synchronous public endpoint dependency to search.
3. **Make validation availability truthful (`EVI-05`).** Show whether the separately isolated worker is actually available. A queued job is not an executed test. Worker deployment (`EVI-13`) is a later security/cost decision, not a prerequisite to share honest evidence.
4. **Prove one journey (`VLD-03`).** Use a real search requirement such as “find a PostgreSQL MCP server that cannot write.” Show how the product handles an advertised read-only candidate with an arbitrary-SQL path, and how it distinguishes an auth-gated remote from an unreachable one. Record the deployed commit, timestamps, fixture and production observations, and limitations. Never imply that a successful listing or finite probe proves a write path unreachable.

Run `VLD-01` measurement and `VLD-02` developer conversations alongside implementation. Do not wait for a social post to discover whether the selection workflow helps anyone.

## What to learn

Ask active MCP developers for a recent server-selection decision, not an opinion about a feature list. Capture the requirement, alternatives considered, how they checked reachability and permissions today, what evidence they trusted, and the actual choice or blocker. Aim for 5–10 opt-in conversations/cases and 3–5 guided uses on real requirements. These are practical targets, not statistically representative samples. Do not store credentials or unnecessary personal data in reports.

Measure a small funnel: relevant entry/referral, search, result/detail inspection, source or installation action, and opt-in confirmation that the evidence affected a decision. Existing web analytics establish referral sources, not adoption; add only privacy-conscious aggregate event measurement. Learn repeat use through opt-in follow-up rather than fingerprinting or raw-query logging. Record baseline and measurement gaps before interpreting change.

## Share gate

Only prepare an external technical update after `VLD-03` verifies an actual deployed behavior and `VLD-02` yields a useful, consented or public selection example. The update should demonstrate a before/after decision and explicitly say what remains unknown. Prefer a relevant, non-promotional follow-up where the problem was raised, subject to current community rules; then one channel-native X/Bluesky update if the example is useful. Do not recycle the original launch copy or re-launch on Product Hunt for an incremental change. “No discussion worth replying to” is a valid outcome.

`VLD-04` produces drafts and approval packages only. For every external post or reply, the operator must separately approve the exact account, destination URL, operation, final copy, links/media, and timing immediately before publication. One approval never covers another destination; a draft approval never authorizes publishing. Record URLs only after the platform confirms publication. Follow [marketing operations](MARKETING.md) and the [feedback-agent approval policy](FEEDBACK_AGENT.md) for their respective channels.

## Continue, narrow, or pause

At the end of the sprint, `VLD-05` reports both qualitative cases and aggregate actions. As a decision heuristic, continue focused investment if roughly five qualified developers describe the same painful selection job, at least three use MCP Compass for a real choice, and at least two return or make a concrete integration/contribution request. These small numbers are not proof of product-market fit. If the evidence is weaker, narrow to the most useful part of the workflow or pause broad feature work; do not compensate with more generic social posts. A credible correction to an overstrong safety claim remains worthwhile regardless of this gate.

Defer `EVI-02`/`EVI-03` and the other V0.8 ideas until a specific user decision and safety boundary justify them. In particular, static schema inspection can surface risk but not prove absence of writes, and runtime probes require an explicit threat model and isolated worker.
