# EVI-12: pricing, SLA, and quality comparison decision

Observed: 2026-11-12 UTC. Scope: public desk research for EVI-12, based on
`main` f3981947e5ea50b474f6ff727aa77e43f3b108ab. No interviews, outreach,
provider enrollment, billable tool calls, or production changes were performed.

## Decision

**No-go for shipping normalized optional price, SLA, or quality comparison fields
in this slice.** There is a useful selection problem, but this bounded sample does
not establish sufficiently comparable, authoritative data or demonstrated demand
for a particular UI. **Go for retaining source-linked evidence and unknowns in
research**, and for a later opt-in comparison prototype if the gates below pass.
This recommendation does not add an API, database column, ranking weight, badge,
provider scraper, new protocol requirement, or synchronous search dependency.

The evidence supports distinguishing billing conditions and task-specific failure
modes. It does not support a universal cheapest/best/reliable ordering. Missing
data means unknown, never zero cost, no SLA, or low quality.

## Method and traceability

Reviewed the existing [ecosystem review](2026-10-25-six-month-market-analysis.md),
its original selection-metadata proposal, and bounded GitHub searches for Tavily
cost, Brave free-tier changes, DuckDuckGo rate limits, and MCP SLA requests.
Read the selected issue bodies and relevant comments through GitHub's read-only
API; checked provider documentation and a versioned Registry JSON schema directly.
The search is purposive and search-ranked, concentrated on search/retrieval, and
cannot estimate prevalence. Only short paraphrases and public handles needed for
deduplication are retained. No raw discussions or private account data are copied.

### Selection cases

| ID / canonical source / public author / publication | Requirement and current workaround | Alternatives and evidence needed | Reported decision or blocker / confidence |
| --- | --- | --- | --- |
| C1: [deep-research #77](https://github.com/dzhng/deep-research/issues/77), GanQiao1990, 2025-02-12 | Reduce Firecrawl expense in a research workflow. Current dependency is the named service; workload and invoice are unspecified. | Thread participants suggest self-hosting, Crawl4AI, SearXNG, and a router. Need workload, operator cost, and current official billing rules to compare them. | Original author seeks alternatives; no verified replacement purchase or measured saving. Historical **adjacent API** case, not direct proof of MCP demand. Maker suggestions in comments do not add independent customer cases. |
| C2: [duckduckgo-mcp-server #63](https://github.com/nickclyde/duckduckgo-mcp-server/issues/63), gefsikatsinelou, 2026-07-30 | Claude Desktop user reports weak niche/regional freshness and intermittent empty results; workaround is a browser and waiting for limits. | Requests an optional keyed Google-result backend while preserving DDG. Need coverage on actual queries, privacy terms, budget, and failure behavior. | Proposal, not evidence the alternative was adopted or fixed quality. Issue is closed at observation; closure alone proves neither. Affiliation is not established; the recommendation could be promotional. Reported price/privacy claims are **not accepted as provider facts**. |
| C3: [local-search-mcp #258](https://github.com/Fooftilly/local-search-mcp/issues/258), Fooftilly, 2026-11-01 (updated 2026-11-05) | Maintainer requires recurring free use, prefers no card, excludes paid enrollment and Google engines, and preserves guarded local egress. | Evaluates keyless hosted MCP and credentialed recurring-credit alternatives. Needs renewal versus trial distinction, remaining quota, installed adapter support, quality and disclosure checks. | Chooses an attended default-off pilot; thread records merged keyless-pilot PR #295 and an Exa extension PR #296, but explicitly leaves real host/provider acceptance outstanding. Those statuses are author reports here, not separately audited adoption. Stronger concrete selection constraint; no observed MCP Compass use. |

Three distinct author/thread pairs are retained, across three repository roots on
one platform; only C2/C3 are directly MCP-specific. Repeated updates, comment
recommendations, and linked PRs are not extra independent cases. This is a **small
sample**, not three validated customers or proof that any feature changes a choice.
No case establishes a completed procurement decision based on a contractual MCP
SLA. That gap is a finding, not a reason to invent an SLA case.

### Original proposal (P1), separate from cases

[Discussion #2831](https://github.com/modelcontextprotocol/modelcontextprotocol/discussions/2831),
YE-YI7, 2026-06-01, proposes economic metadata and reports an audit/selection
experiment. Those measurements were not reproduced and are not counted as
independent developer demand. Its linked
[ASM project](https://github.com/YE-YI7/asm-spec/tree/200633997efac89a2b4eb78f8ab4b377ea4aa190)
contains a structured proposal and source-linked manifests. JSON availability is
real; authority over third-party contracts, current provider adoption, and
comparable quality are separate questions. Do not describe it as an adopted MCP
standard or require publishers to implement it.

## Authoritative machine-readable data assessment

| Source inspected | Structured data / authority | Coverage, freshness, and comparison limit | Result |
| --- | --- | --- | --- |
| [Registry server schema 2025-12-11](https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json) and [format reference](https://github.com/modelcontextprotocol/registry/blob/899011edf6cc6f7e7388e80d8f2dbba9679ef9c2/docs/reference/server-json/generic-server-json.md) | JSON Schema for installation/discovery. `ServerDetail` has no standardized pricing, SLA or quality properties. Publisher `_meta` can carry custom data. | Schema revision is pinned; not an audit of every published entry. Extensible JSON is not normalized contract semantics or independent verification. | Useful server/package linkage, insufficient economic comparison contract. |
| [Tavily credits documentation](https://docs.tavily.com/documentation/api-credits) and [MCP mapping](https://docs.tavily.com/documentation/mcp) | First-party billing documentation, available as text/Markdown rather than a verified public price-catalog JSON API. MCP docs connect the service to the tools. | At observation, basic search uses one credit and advanced two; plan, operation, success conditions, renewal and extra work matter. A billing credit is not a universal search unit. Published rules are not a quote for an unknown account. | Authoritative scoped documentation exists; automatic comparable monetary field remains no-go. |
| [Tavily usage endpoint](https://docs.tavily.com/documentation/api-reference/endpoint/usage) | Documented authenticated JSON: `key.usage`, `key.limit`, `account.current_plan`, `account.plan_usage`, `account.plan_limit`, `account.paygo_usage`, `account.paygo_limit`. | This is account-specific usage, not a public per-operation tariff or contract. Schema/example reviewed only; no key supplied or account response requested. Do not import private usage into a public catalog. | Machine-readable authoritative account usage exists, insufficient for public cross-server price comparison. |
| [Tavily rate limits](https://docs.tavily.com/documentation/rate-limits) | First-party plan/endpoint conditions and HTTP error guidance. | Throughput limits and capacity refusals describe service conditions, not a contractual uptime promise or measured task correctness. Anonymous and authenticated paths can differ. | Keep link and scope; do not map RPM into SLA or quality. |
| [Atlassian SLA, effective 2026-04-30](https://www.atlassian.com/legal/sla) | First-party legal HTML with plan, covered experience, exclusions and remedy; no normalized machine contract was verified. | Appendix A limits most coverage to browser experiences and expressly distinguishes integrations/API calls. A Jira/Confluence plan percentage cannot be inherited by an MCP wrapper or remote endpoint. | Authoritative terms exist, but **no established MCP SLA** from this document. This is a scope counterexample, not legal advice or a universal absence claim. |
| [ASM pinned proposal](https://github.com/YE-YI7/asm-spec/blob/200633997efac89a2b4eb78f8ab4b377ea4aa190/README.md) | Structured manifests and schemas can encode pricing/quality/SLA; primarily schema-author/curator assertions unless the service publisher endorses a specific record. | Same author/project as P1; curated examples do not establish current first-party coverage. A valid document can contain stale or incomparable claims. No third-party code was run. | Exists, worth watching; no-go as an authoritative default catalog feed. |

This is a bounded existence/fitness check, not a claim that no provider anywhere
offers a tariff API. Neither a status page nor a successful `tools/list` is an SLA.
No common first-party machine-readable task-quality feed was verified for the
case alternatives. Quality needs a workload, queries/dataset, model/client,
server/tool/version, parameters, region, time window, sample size, failures and
reproducible scoring. Stars, README adjectives and schema-valid responses cannot
substitute for that evidence.

## Optional fields: gates before implementation

| Candidate field | Recommendation now | Minimum evidence for reconsideration |
| --- | --- | --- |
| Price / free allowance / billing unit | No-go for normalized numeric display, filter or ranking; retain official links in research. | At least two comparable first-party records for the same operation and workload, explicit currency/unit/tier/fixed fees/renewal/overage, effective time and refresh owner; distinguish wrapper hosting, upstream API, model tokens and self-host operations. Unknown is explicit. |
| SLA | No-go for inherited percentages or generic availability badge. | Contract explicitly covers that endpoint, tool and customer plan; exclusions, remedy, measurement window and effective date retained. Manual review of applicability; promo text alone fails. |
| Quality / latency | No-go for a universal score or mixed benchmark ordering. | Reproducible task-specific measurements on comparable configurations, errors and missing results included, provenance and observation conditions shown. Do not mix one-region latency with contractual availability. |
| Source URL, source kind, scoped claim and checked time | Go for research records; product prototype only after a fresh selection walkthrough demonstrates usefulness. | Preserve publisher versus curator versus measured evidence, target server/package/version and unknown/stale/mismatched states. Refresh outside the search request path. |

Revisit after at least three independent, qualified MCP selection walkthroughs
across at least two threads/organizations show that these conditions change a real
choice, plus a two-provider data pilot passes the requirements above. This is a
future decision gate, not a claim of existing demand or permission to collect
private data. Stop/narrow if authoritative coverage stays sparse or maintenance
outweighs selection value. Keep capability compatibility ahead of economic
preferences; never soften permission requirements because a server appears cheap.

## Concrete desk example

Requirement: choose a search MCP for a recurring-free technical lookup workflow,
without enabling paid overage. Before: treating a credit as a search could imply
the same allowance for basic and advanced requests, and a product uptime claim
could be copied onto its MCP integration. After this evaluation: consult the
official billing link, distinguish `search_depth=basic` from `advanced`, keep
account remaining quota unknown, require actual-query quality checks, and keep MCP
contract coverage unknown unless explicitly documented. The outcome is **no
automated cheapest/best/SLA-backed recommendation**. This is a desk scenario, not
a deployed API/UI feature or a claim that a provider was selected successfully.

## Reproduce and desk-test

Run from repository root; GitHub CLI needs read access. No provider credentials
or MCP process execution is required.

1. Read C1-C3 using `gh api repos/dzhng/deep-research/issues/77`,
   `gh api repos/dzhng/deep-research/issues/77/comments`,
   `gh api repos/nickclyde/duckduckgo-mcp-server/issues/63`, and
   `gh api repos/Fooftilly/local-search-mcp/issues/258`. Check handles/dates,
   actual blockers and reported versus completed choices against the table.
   Expected: three author/thread pairs, no invented completed purchase or SLA.
2. Open P1 and the pinned ASM README. Expected: a proposal/curated data source,
   kept separate from independent demand and provider contract authority.
3. In PowerShell fetch and parse the pinned public schema:

   ```powershell
   $eviSchema = Invoke-RestMethod 'https://static.modelcontextprotocol.io/schemas/2025-12-11/server.schema.json'
   $eviSchema.definitions.ServerDetail.properties.PSObject.Properties.Name | Sort-Object
   ```

   Expected: discovery/installation properties and `_meta`; no standardized
   price/SLA/quality property. Custom extensions remain possible.
4. Open the first-party Tavily pricing, usage and rate-limit links above. Expected:
   operation-dependent credits, account-authenticated usage JSON, and throughput
   rules are distinguishable. Do not call `/usage` with a private key or infer
   account balance from its documentation example.
5. Open the SLA Appendix A and exclusions. Expected: browser-experience scope
   does not establish contractual coverage for a generic MCP/API integration.
6. Walk the concrete example and field gates. Expected: unknown stays unknown;
   no ranking/API/schema implementation is implied. Confirm EVI-12 remains
   unchecked in `PLANS.md` pending manual PR merge.
7. Run `git diff --check` and
   `python -m unittest discover -s .github/scripts -p 'test_*.py'`.
   Expected: no whitespace errors and passing workflow automation checks.

Steps 1-6 were performed on 2026-11-12. GitHub issue reads and source documents
were checked directly; the schema command was parsed locally. Provider-authenticated
calls, billable queries, live comparative workloads and interviews were not run:
they are unnecessary for this public-data no-go evaluation and would require
account/workload context. Backend/frontend/CLI local builds are not task-specific
for this documentation-only change; the task PR workflow performs its standard
full validation separately. Workflow and CI outcomes belong to the PR handoff.

Local step 7 result: `git diff --check` passed; all 76 workflow automation tests
passed with Python 3.10. These checks validate repository handoff mechanics, not
provider prices, legal coverage, or task quality.
