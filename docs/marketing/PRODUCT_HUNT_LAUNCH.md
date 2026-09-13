# Product Hunt launch handoff

Status: prepared locally; no Product Hunt draft, schedule, or submission has been created.

## Evidence boundary

This package promotes the public
[`v0.1.0` release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0) at
[`9ca1212a902639dd279cf90e7628974314135f6d`](https://github.com/jinjin-huang-3366/mcp-compass/commit/9ca1212a902639dd279cf90e7628974314135f6d).
CI run [34709603842](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709603842) and deployment run
[34709693340](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340) passed for that exact source.
The public demo and an `mt5` search were last verified on 2026-09-12. Public claims below use deployed claim IDs C1,
C2, C4, and C5 from the [launch package](LAUNCH_PACKAGE_V1.md#release-notes-and-evidence-ledger).

The current Product Hunt submission guidance was checked on 2026-09-13 against the
[launch preparation guide](https://www.producthunt.com/launch/preparing-for-launch) and
[posting help](https://help.producthunt.com/en/articles/479557-how-to-post-a-product). The two pages currently
disagree on whether the description limit is 500 or 260 characters, so this package uses the safer 260-character
limit. Recheck the live form before submission.

## Submission fields

| Field | Prepared value |
| --- | --- |
| Product URL | `https://mcp-compass-iota.vercel.app/` (direct, untracked canonical demo URL) |
| Name | `MCP Compass` |
| Tagline | `Find the right MCP server for your agent requirement` (52 characters) |
| Description | `Describe what your agent needs. MCP Compass ranks existing MCP servers, explains capability fit and gaps, and abstains when evidence is weak. If reuse falls short, review an OpenAPI-derived tool contract before generating a TypeScript server.` (242 characters) |
| Suggested launch tags | `AI Agents`, `Developer Tools`, `Search`; verify the exact live tag labels and select no more than three |
| Additional link | `https://github.com/jinjin-huang-3366/mcp-compass` |
| Product X account | None; do not substitute a maker's personal account |
| Pricing | Free |
| Availability | Launched; the public demo is usable now |
| Video / interactive demo | None |
| Promo | None |
| Shoutouts | None selected |
| Hunter | Self-submission by an eligible personal Product Hunt account |
| Makers | Add the operator's verified Product Hunt username in the live form |

The direct demo URL deliberately has no UTM parameters because Product Hunt does not accept tracking links. Measure
the launch through Product Hunt's publication URL and the `producthunt.com` initial-page referrer in Vercel Web
Analytics; the current Hobby dashboard does not expose UTM dimensions.

## Gallery assets

Upload in this order. Product Hunt currently requires at least two gallery images and recommends 1270 × 760; these
version-pinned product captures retain their native proportions so UI text stays legible and unaltered.

1. [`search-ranking.png`](../assets/launch/search-ranking.png) — 1440 × 1200, 73 KB. Alt text: “MCP Compass search results for a read-only PostgreSQL requirement, showing parsed constraints, a ranked server, capability coverage, and score contributions.”
2. [`search-abstention-mobile.png`](../assets/launch/search-abstention-mobile.png) — 390 × 844, 46 KB. Alt text: “Mobile MCP Compass result preserving GitHub delete prohibitions and explaining why no server is a strong match.”
3. [`contract-review.png`](../assets/launch/contract-review.png) — 1440 × 1100, 29 KB. Alt text: “MCP Compass contract review with selected OpenAPI operations, editable tool names and descriptions, and read-only and mutating risk labels.”

Use [`product-hunt-thumbnail.png`](../assets/launch/product-hunt-thumbnail.png) as the square thumbnail. It is a
1254 × 1254, 328 KB static PNG with a transparent background, safely below Product Hunt's current 3 MB limit. Alt
text if the form exposes it: “MCP Compass compass needle connected to three MCP nodes.” The asset was generated for
this launch with OpenAI's image-generation tool; it contains no text, third-party mark, animation, or user data.

Capture provenance and the production-data boundary are documented in the
[launch asset record](../assets/launch/README.md). No video or animated GIF is planned because the stills already
show the ranking, abstention, and contract-first generation story.

## Maker first comment

> Hi Product Hunt — I built MCP Compass for agent developers who need more than a keyword directory when choosing
> an MCP server.
>
> Describe the capabilities and restrictions your agent needs. MCP Compass searches a locally synchronized public
> Registry index, ranks existing servers, and explains matched and missing capabilities. When the evidence is weak,
> it can say there is no strong match. If reuse is inadequate, the separate generation path starts with review of an
> OpenAPI-derived tool contract before it exports a TypeScript server.
>
> One important limitation: generated-project validation needs a separately hosted isolated worker. Jobs can remain
> queued in this deployment, and validation is bounded evidence rather than a security certification.
>
> I would value one real requirement you find hard to evaluate today: what result did you expect, and did the ranking
> explanation make the match or gap clear? Please omit credentials and private tool schemas.

This comment uses C1, C2, C4, and C5. It asks for product feedback, not votes.

## Launch-day response plan

- The maker should be available from launch through the first active feedback window and answer as a person; do not
  automate comments, replies, votes, or engagement.
- Ask for reproducible requirements and expected results. Never ask users to paste credentials or private schemas.
- Record each useful report with its Product Hunt URL, theme, reproduction evidence, and product impact. Draft a
  GitHub issue only after deduplicating it; creating the issue still requires explicit authorization.
- Correct inaccurate capability, coverage, safety, or hosted-validation claims promptly. Do not describe validation
  as a certification or imply that the Registry snapshot covers the whole ecosystem.
- Preserve the final Product Hunt URL and compare its referrer-domain traffic in Vercel Web Analytics after 24 hours
  and again after 7 days.

## Operator and approval checklist

Before creating a Product Hunt draft or submitting:

- verify the personal account is eligible to submit and complete; company accounts cannot post products;
- confirm the product has not already been submitted and resolve any duplicate before continuing;
- verify the live demo, GitHub release, repository link, and every uploaded image from a signed-out session;
- confirm the live form limits, accepted tags, maker username, gallery previews, pricing, and availability;
- select an exact launch action and time; Product Hunt currently allows scheduling up to one month ahead;
- present the complete package—including account, fields, links, assets, alt text, and ISO 8601 time with UTC offset—
  for explicit approval under the [external publication gate](../../.agents/skills/mcp-compass-marketing/references/approval-gate.md).

Preparation is not authorization to create a remote draft, schedule, or submit. Any change to copy, account, link,
asset, alt text, or schedule invalidates earlier approval. Product Hunt submission is a manual operator action; do
not coordinate votes or promise homepage placement.
