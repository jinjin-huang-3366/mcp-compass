# MCP Compass developer launch package v1

Status: draft only; not approved, scheduled, or published. This file is source material, not an approval record.

## Release boundary

No GitHub release or tag existed when this package was prepared on 2026-09-08. The release boundary is therefore
the exact production commit
[`9ab1c47b8e3885bc983103738f7ac54d3ba8d61d`](https://github.com/jinjin-huang-3366/mcp-compass/commit/9ab1c47b8e3885bc983103738f7ac54d3ba8d61d),
not an inferred version number. [CI run 34166079085](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166079085)
passed for that commit, and [deployment run 34166221323](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166221323)
staged, smoke-tested, and promoted its backend and frontend on 2026-09-07.

The canonical call to action for this package is the promoted frontend deployment:
<https://mcp-compass-69v2ogw2j-jinjin-huang-3366s-projects.vercel.app/>. A stable custom domain has not been verified.

## Release notes and evidence ledger

Only claim IDs marked **Deployed** may appear in public copy. The release-note wording is deliberately narrower than
the implementation details in the linked sources.

| ID | Release note / permitted claim | State | Primary evidence |
| --- | --- | --- | --- |
| C1 | Developers can describe an agent capability and receive ranked MCP server matches with explanations of parsed intent, constraints, and matched or missing capabilities. | **Deployed** | [README product flow](../../README.md#current-status), [search API](../API.md), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166221323) |
| C2 | MCP Compass searches locally persisted public Registry metadata rather than calling the public Registry in the user search path. | **Deployed** | [architecture](../ARCHITECTURE.md#product-flow), [ADR 0002](../DECISIONS/0002-local-registry-index.md), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166221323) |
| C3 | The fixed 53-judgement evaluation recorded 96.2% Recall@100, 0.8620 NDCG@10, 24/24 top-three acceptance, zero forbidden-result violations in the top three, and 8/8 correct abstentions. | **Deployed**; measured in the linked fixed gate | [quality report](../reports/DEMO_QUALITY_GATE_V1.md#exit-thresholds), [EXIT-05 PR](https://github.com/jinjin-huang-3366/mcp-compass/pull/71), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166221323) |
| C4 | When reuse is inadequate, developers can review an OpenAPI-derived tool contract before exporting a GitHub-ready TypeScript MCP server project. | **Deployed** | [README status](../../README.md#current-status), [contract review API](../API.md#review-and-approve-an-mcp-tool-contract), [ADR 0003](../DECISIONS/0003-reuse-before-generate.md), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166221323) |
| C5 | Generated-project validation is isolated from the backend, but submitted production jobs remain queued unless a separately hosted validation worker is running. Validation is bounded evidence, not a security certification. | **Deployed limitation** | [deployment limitation](../DEPLOYMENT.md#validation-worker), [security model](../SECURITY.md), [production deployment](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34166221323) |
| C6 | The activation run observed 7,571 indexed servers with complete search-document and embedding coverage on 2026-09-06. This is historical run evidence, not a live inventory claim. | **Deployed**; point-in-time measurement | [quality report](../reports/DEMO_QUALITY_GATE_V1.md#production-candidate), [activation run](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34058811869) |

Do not claim that MCP Compass covers every MCP server, guarantees safety, executes validation in the hosted web app,
or has unmeasured adoption. Do not turn C3 or C6 into an unqualified current-production claim.

## Campaign brief

- **Audience:** developers building agents or evaluating MCP integrations.
- **Problem:** finding an MCP server that actually meets required capabilities and restrictions takes manual catalog,
  README, and tool-schema comparison.
- **Shipped proof points:** C1, C2, C3, and C4.
- **Material limitations:** C5; Registry coverage is a synchronized snapshot, not the entire ecosystem; a stable custom
  domain is not yet verified.
- **Call to action:** try one real agent requirement in the promoted web app and inspect why each result matched or
  why MCP Compass abstained.
- **Measurement:** preserve each publication URL and compare initial-page referrer domains in Vercel Web Analytics.
  UTM dimensions are not exposed by the current Hobby dashboard, so the links below carry no fabricated attribution
  promise.

## Draft launch content

These drafts are inputs to the MKT-04 exact-copy approval gate. The account, final link, media, and publication time
must be filled in and the complete package approved before any remote draft, schedule, or publication is created.

### X

Finding an MCP server should start with what your agent needs, not a directory scroll.

MCP Compass turns a capability requirement into ranked matches, shows the matched and missing capabilities, and
abstains when the evidence is weak. If reuse is inadequate, you can review an OpenAPI-derived tool contract before
exporting a TypeScript server.

Try it: <https://mcp-compass-69v2ogw2j-jinjin-huang-3366s-projects.vercel.app/>

Claims used: C1, C4. Proposed format: standalone post. Media: none selected. Account and timing: awaiting approval.

### Bluesky

Describe what your agent needs. MCP Compass ranks matching MCP servers and explains the capabilities and constraints
behind each result. Weak evidence? It can say no strong match instead of filling the page with unrelated servers.

Reuse first; reviewed-contract generation second.
<https://mcp-compass-69v2ogw2j-jinjin-huang-3366s-projects.vercel.app/>

Claims used: C1, C4. Media: none selected. Account and timing: awaiting approval.

### Product Hunt preparation

- **Name:** MCP Compass
- **Tagline:** Find the right MCP server for your agent requirement
- **Short description:** Describe an agent capability, compare ranked MCP server matches with evidence, and move to
  reviewed-contract generation only when reuse is inadequate.
- **Maker comment:** We built MCP Compass for the point where “find me an MCP” stops being a keyword search. It parses
  capabilities and restrictions, searches a local Registry index, explains why candidates fit, and abstains when the
  evidence is weak. The generated-server path is deliberately contract-first. The hosted validation worker is not
  part of this deployment, so queued validation is not a security certification. We would value examples where the
  ranking or explanation still misses developer intent.
- **First comment:** Share one real requirement, the result you expected, and whether the explanation made the gap
  obvious. Please omit credentials and private tool schemas.
- **Assets still required:** verified logo/icon, gallery images, alt text, and final canonical-domain screenshot.

Claims used: C1, C2, C4, C5. Submission, account, assets, and timing: awaiting approval.

### Hacker News preparation

**Title:** Show HN: MCP Compass – rank MCP servers from an agent capability requirement

**Body:** I built MCP Compass to make MCP discovery start with an agent requirement rather than a server name. It
parses capabilities and restrictions, searches locally synchronized Registry data, ranks candidates, and explains
matched and missing capabilities. The fixed 53-judgement gate recorded 0.8620 NDCG@10, 24/24 top-three acceptance,
zero forbidden-result violations in the top three, and 8/8 correct abstentions.

The design is reuse-first. If no existing server is adequate, the generation path requires review of an
OpenAPI-derived tool contract before exporting a TypeScript MCP project. One rough edge: generated-project validation
needs a separately hosted isolated worker, so jobs can remain queued in the current hosted setup.

Demo: <https://mcp-compass-69v2ogw2j-jinjin-huang-3366s-projects.vercel.app/>

I would appreciate technical feedback on requirements that should abstain, ranking explanations that are not useful,
and MCP metadata that the index handles poorly.

Claims used: C1, C2, C3, C4, C5. Submission and timing: awaiting approval. Do not coordinate votes.

### Reddit source draft

**Working title:** I built a requirement-first search tool for MCP servers

**Body:** I am the maker of MCP Compass. It takes a natural-language agent capability requirement, searches locally
synchronized Registry metadata, and explains why its ranked MCP server candidates matched—or why there is no strong
match. The goal is to reuse an adequate server before reaching for generation; generation starts from a reviewed
OpenAPI-derived tool contract.

The hosted setup has an important limitation: generated-project validation requires a separate isolated worker, so
queued validation should not be read as a security certification.

Demo: <https://mcp-compass-69v2ogw2j-jinjin-huang-3366s-projects.vercel.app/>

I would value a concrete requirement that existing MCP directories make hard to evaluate.

Claims used: C1, C2, C4, C5. This is source material only. Select a community and verify its current self-promotion,
link, flair, and title rules before adapting it; disclose the maker relationship. Submission and timing: awaiting
approval.

## Approval handoff

MKT-04 must replace the immutable Vercel deployment URL if a verified stable canonical domain is available, select
the exact accounts and communities, verify current platform limits and community rules, attach final media and alt
text, and record an exact schedule. Any edit to copy, link, account, channel, media, or schedule requires approval of
the resulting package. No Typefully or platform action has been taken by this task.

For each channel, turn the selected material into a separate package using the
[exact-copy approval gate](../../.agents/skills/mcp-compass-marketing/references/approval-gate.md). The package must
name its intended action and revision and use an ISO 8601 timestamp with UTC offset plus a named timezone. Show the
entire package and obtain explicit human approval in the current conversation immediately before scheduling or
publishing. Neither this launch package nor approval of an unscheduled draft grants publishing authority.
