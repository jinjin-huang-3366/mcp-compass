# MCP Compass developer launch package v1

Status: the X launch was published on 2026-09-11, and the Bluesky launch was published on 2026-09-13. The
remaining channel material is draft only.

## Release boundary

The launch boundary is the public
[`v0.1.0` release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0) at exact source commit
[`9ca1212a902639dd279cf90e7628974314135f6d`](https://github.com/jinjin-huang-3366/mcp-compass/commit/9ca1212a902639dd279cf90e7628974314135f6d).
[CI run 34709603842](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709603842) passed for that
commit, and [deployment run 34709693340](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340)
staged, smoke-tested, promoted, and rechecked its backend and frontend on 2026-09-12.

The canonical call to action for this package is the public production frontend:
<https://mcp-compass-iota.vercel.app/>. Anonymous access and a production search were verified on 2026-09-12. A
custom domain has not been configured.

## Release notes and evidence ledger

Only claim IDs marked **Deployed** may appear in public copy. The release-note wording is deliberately narrower than
the implementation details in the linked sources.

| ID | Release note / permitted claim | State | Primary evidence |
| --- | --- | --- | --- |
| C1 | Developers can describe an agent capability and receive ranked MCP server matches with explanations of parsed intent, constraints, and matched or missing capabilities. | **Deployed** | [README product flow](../../README.md#current-status), [search API](../API.md), [v0.1.0 release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340) |
| C2 | MCP Compass searches locally persisted public Registry metadata rather than calling the public Registry in the user search path. | **Deployed** | [architecture](../ARCHITECTURE.md#product-flow), [ADR 0002](../DECISIONS/0002-local-registry-index.md), [v0.1.0 release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340) |
| C3 | The fixed 53-judgement evaluation recorded 96.2% Recall@100, 0.8620 NDCG@10, 24/24 top-three acceptance, zero forbidden-result violations in the top three, and 8/8 correct abstentions. | **Deployed**; measured in the linked fixed gate | [quality report](../reports/DEMO_QUALITY_GATE_V1.md#exit-thresholds), [EXIT-05 PR](https://github.com/jinjin-huang-3366/mcp-compass/pull/71), [v0.1.0 release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340) |
| C4 | When reuse is inadequate, developers can review an OpenAPI-derived tool contract before exporting a GitHub-ready TypeScript MCP server project. | **Deployed** | [README status](../../README.md#current-status), [contract review API](../API.md#review-and-approve-an-mcp-tool-contract), [ADR 0003](../DECISIONS/0003-reuse-before-generate.md), [v0.1.0 release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340) |
| C5 | Generated-project validation is isolated from the backend, but submitted production jobs remain queued unless a separately hosted validation worker is running. Validation is bounded evidence, not a security certification. | **Deployed limitation** | [deployment limitation](../DEPLOYMENT.md#validation-worker), [security model](../SECURITY.md), [v0.1.0 release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340) |
| C6 | The activation run observed 7,571 indexed servers with complete search-document and embedding coverage on 2026-09-06. This is historical run evidence, not a live inventory claim. | **Deployed**; point-in-time measurement | [quality report](../reports/DEMO_QUALITY_GATE_V1.md#production-candidate), [activation run](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34058811869) |

Do not claim that MCP Compass covers every MCP server, guarantees safety, executes validation in the hosted web app,
or has unmeasured adoption. Do not turn C3 or C6 into an unqualified current-production claim.

## Campaign brief

- **Audience:** developers building agents or evaluating MCP integrations.
- **Problem:** finding an MCP server that actually meets required capabilities and restrictions takes manual catalog,
  README, and tool-schema comparison.
- **Shipped proof points:** C1, C2, C3, and C4.
- **Material limitations:** C5; Registry coverage is a synchronized snapshot, not the entire ecosystem; a custom domain
  is not configured.
- **Call to action:** try one real agent requirement in the promoted web app and inspect why each result matched or
  why MCP Compass abstained.
- **Measurement:** preserve each publication URL and compare initial-page referrer domains in Vercel Web Analytics.
  UTM dimensions are not exposed by the current Hobby dashboard, so the links below carry no fabricated attribution
  promise.

## Channel launch content

The X and Bluesky sections record their exact approved launch packages. The remaining drafts are inputs to the MKT-04
exact-copy approval gate: the account, final link, media, and publication time must be filled in and the complete
package approved before any remote draft, schedule, or publication is created.

### X

Describe what your agent needs—not a directory scroll.

MCP Compass ranks MCP servers, explains fit and gaps, and abstains when evidence is weak.

No strong match? Provide an OpenAPI document, review the tool contract, and generate a TypeScript MCP server.

Try it: <https://mcp-compass-iota.vercel.app/>

Claims used: C1, C4. Format: standalone X post. Media: none. Account: `@jhuang3366`. Approved as package
`X-LAUNCH` revision 6 and scheduled through Typefully draft `10716573` for `2026-09-11T01:30:00+01:00`
(Europe/London). Typefully reported publication at `2026-09-11T01:30:02+01:00`:
<https://x.com/jhuang3366/status/2098207401514299864>.

### Bluesky

Post 1:

Describe what your agent needs—not a directory scroll.

MCP Compass ranks MCP servers, explains fit and gaps, and abstains when evidence is weak.

No strong match? Provide an OpenAPI document, review the tool contract, and generate a TypeScript MCP server.

Try: <https://mcp-compass-iota.vercel.app/>

Post 2:

Repository:
<https://github.com/jinjin-huang-3366/mcp-compass>

Discussion:
<https://github.com/jinjin-huang-3366/mcp-compass/discussions/94>

Claims used: C1, C4. Format: two-post Bluesky thread. Media: none. Account: `@jhuang3366.bsky.social`.
Approved as package `BLUESKY-LAUNCH` revision 5 and published through Typefully draft `10749783` at
`2026-09-13T16:27:20.985+01:00` (Europe/London). Typefully and Bluesky's public API reported the root post and
thread reply at:

- <https://bsky.app/profile/jhuang3366.bsky.social/post/3mvfvu3d3ea2q>
- <https://bsky.app/profile/jhuang3366.bsky.social/post/3mvfvu3vfht2l>

### Product Hunt preparation

The complete import-ready field set, exact maker first comment, square thumbnail, ordered gallery with alt text,
current-platform checks, measurement plan, and launch-day response checklist are in the
[Product Hunt launch handoff](PRODUCT_HUNT_LAUNCH.md). It uses the verified canonical demo and release boundary.

Claims used: C1, C2, C4, C5. No Product Hunt draft, schedule, or submission has been created. The submitting personal
account, exact live tags, and launch time must be filled in and the complete resulting package explicitly approved
immediately before the manual external action.

### Hacker News preparation

Hacker News's official [Show HN guidelines](https://news.ycombinator.com/showhn.html) and
[site guidelines](https://news.ycombinator.com/newsguidelines.html) were rechecked on 2026-09-13. A Show HN must be
something the submitter made and is available to discuss, must be directly usable rather than a landing page, should
be easy to try without signup, and must not be supported by solicited votes or comments. The site guidelines also say
not to post generated or AI-edited text. Consequently, the earlier machine-drafted title and body have been removed:
the maker must write the final submission in their own words before it can enter the exact-copy approval gate.

**Maker writing packet (facts and prompts, not submission copy):**

- **Title requirement:** begin with `Show HN:` and describe the working project factually. Avoid superlatives,
  exclamation points, or release-version framing.
- **Maker context to cover:** why MCP server discovery based on names and directory entries was insufficient for the
  maker; what they personally built; and why requirement-first search and reuse-before-generation were chosen.
- **Technical path to explain:** natural-language requirement → parsed capabilities, prohibitions, and constraints →
  locally synchronized Registry search → deterministic ranked matches with evidence → abstention when evidence is
  weak. If reuse is inadequate, the separate generation path starts with an editable OpenAPI-derived tool contract.
- **Concrete try-it example:** open <https://mcp-compass-iota.vercel.app/> with no signup and search for
  `Query PostgreSQL read-only; forbid inserts, updates, deletes, and schema writes`. Inspect the parsed restrictions,
  ranked candidates, matched and missing capabilities, and whether the result abstains rather than recommending an
  unsafe fit. A production API check on 2026-09-13 parsed five forbidden write capabilities, excluded 99 candidates,
  and returned `capital.hove/read-only-local-postgres-mcp-server` first; this is dated evidence, not a promise that a
  changing Registry snapshot will always rank the same server first.
- **Evidence available if useful:** the fixed 53-judgement evaluation recorded 96.2% Recall@100, 0.8620 NDCG@10,
  24/24 top-three acceptance, zero forbidden-result violations in the top three, and 8/8 correct abstentions. Keep
  the fixed-gate qualification and link the [quality report](../reports/DEMO_QUALITY_GATE_V1.md); do not present these
  as universal or live-production guarantees.
- **Rough edges to state:** Registry coverage is a synchronized snapshot, not the whole ecosystem; the hosted
  generated-project validation worker is not deployed, so jobs can remain queued; validation is bounded evidence,
  not a security certification; and there is no custom domain.
- **Discussion prompts:** ask where the parser loses important negative intent, which ranking explanations fail to
  justify a result, which MCP metadata sources are missing, and which requirements should produce no strong match.
- **Final links:** live demo <https://mcp-compass-iota.vercel.app/>; source
  <https://github.com/jinjin-huang-3366/mcp-compass>; release
  <https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0>.

Claims available: C1, C2, C3, C4, C5. Intended destination: Hacker News `Show HN`, submitted by the maker's own HN
account. Media: none. Referral measurement: preserve the resulting HN item URL and compare the `news.ycombinator.com`
initial-page referrer in Vercel Web Analytics; no UTM-level measurement is claimed. Submission time and human-authored
copy: awaiting the maker. Do not create a remote draft, submit, coordinate votes/comments, delete and repost, or use
the material above as post copy.

### Reddit source draft

**Working title:** I built a requirement-first search tool for MCP servers

**Body:** I am the maker of MCP Compass. It takes a natural-language agent capability requirement, searches locally
synchronized Registry metadata, and explains why its ranked MCP server candidates matched—or why there is no strong
match. The goal is to reuse an adequate server before reaching for generation; generation starts from a reviewed
OpenAPI-derived tool contract.

The hosted setup has an important limitation: generated-project validation requires a separate isolated worker, so
queued validation should not be read as a security certification.

Demo: <https://mcp-compass-iota.vercel.app/>

I would value a concrete requirement that existing MCP directories make hard to evaluate.

Claims used: C1, C2, C4, C5. This is source material only. Select a community and verify its current self-promotion,
link, flair, and title rules before adapting it; disclose the maker relationship. Submission and timing: awaiting
approval.

## Approval handoff

For each remaining channel, select the exact account or community, verify current platform limits and community rules,
attach final media and alt text, and record an exact schedule. Any edit to copy, link, account, channel, media, or
schedule requires approval of the resulting package. The X approval and Typefully action do not authorize another
channel action.

For each channel, turn the selected material into a separate package using the
[exact-copy approval gate](../../.agents/skills/mcp-compass-marketing/references/approval-gate.md). The package must
name its intended action and revision and use an ISO 8601 timestamp with UTC offset plus a named timezone. Show the
entire package and obtain explicit human approval in the current conversation immediately before scheduling or
publishing. Neither this launch package nor approval of an unscheduled draft grants publishing authority.
