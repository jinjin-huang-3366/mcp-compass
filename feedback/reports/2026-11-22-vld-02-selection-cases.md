# VLD-02 public MCP server-selection cases

Reviewed on 2026-11-22 UTC. Eight independent public cases meet the
5–10-case target. There were zero opt-in conversations, zero guided
walkthroughs, and zero confirmations that MCP Compass changed a developer's
decision. The 3–5 guided real selection walkthroughs in [VLD-02](../../PLANS.md)
remain outstanding. With outreach outside this evidence pass, the number of
developers willing to participate is unknown.

## Public cases

Each case records a first-party report of a choice, blocker, or merged change
in a real MCP project or integration. A merge establishes the repository
change. It leaves deployment, reliability, and continued use open unless the
source reports them. The public records establish no role for MCP Compass in
any of these decisions, so the product's contribution is **unknown** in all
eight cases.

| Case and source | Requirement and existing approach | Evidence needed for the decision | Reported choice or blocker |
| --- | --- | --- | --- |
| C1 · Aug 2025 · [MCP servers issue and developer follow-up](https://github.com/modelcontextprotocol/servers/issues/2517#issuecomment-3218203700) | Fetch web pages in LM Studio. Docker Fetch would disconnect on repeated reloads or while processing a link. | Repeated reloads and representative page reads in the target client, including how it handles session interruptions. | The reporter switched to DuckDuckGo MCP and said the problem did not occur in their use. Page-fetch parity and later reliability remain unknown. |
| C2 · Mar 2026 · [LearnFlow AI PR #41](https://github.com/Bbar0n234/learnflow-ai/pull/41) | Keep remote web search and page extraction after Firecrawl credits ran out. The project already had remote MCP integration code. | Coverage of both operations, compatibility with the project's HTTP and bearer-auth setup, and available credits for the workload. | The PR author reports equivalent search/extract operations using Tavily. The switch merged. No post-merge usage or savings are established. |
| C3 · Nov 2025 · [Operations Center PR #13](https://github.com/ArchieOS-org/operations-center/pull/13) | Look up public library documentation in Claude Code while removing local process management. Context7 previously ran through `npx`. | A hosted HTTP configuration for the client and evidence that the public lookup works with the required authentication arrangement. | Merged the hosted HTTP Context7 configuration. The author reported that public docs could be accessed without a key at the time. |
| C4 · Nov 2026 · [local-search-mcp issue #258](https://github.com/Fooftilly/local-search-mcp/issues/258) and [pilot PR #295](https://github.com/Fooftilly/local-search-mcp/pull/295) | Compare recurring-free web search options, prefer no card, rule out paid overages and Google engines, and preserve the existing local egress guard. Existing local search and crawl remained in place. | Provider terms distinguishing recurring access from trials, installed-client compatibility, egress and data handling, and quota-exhaustion behavior. | Merged a separate, default-off You.com/Firecrawl pilot. The issue records that the installed host had yet to be updated or query a provider after the pilot merged. This case also appears in EVI-12 and counts once here. |
| C5 · Aug 2026 · [Panenco claude-review PR #85](https://github.com/Panenco/claude-review/pull/85) | Keep functional browser UI checks and console/snapshot support while reducing tool-schema context and maintaining a reproducible runner. Playwright MCP relied on multiple separately pinned runner artifacts. | Feature parity and measured schema cost on the required tool set, plus runner dependencies and rollout order. | Merged a switch from Playwright MCP to the agent-browser CLI. The author reported about 4.6k schema tokens for Playwright MCP and about 32k for the agent-browser MCP tool set needed for console support. These are project observations. |
| C6 · Mar 2026 · [Workstation PR #103](https://github.com/johnnymo87/workstation/pull/103) | Let a person watch and interact during browser QA while the agent retains browser tools. Playwright MCP previously launched headless. | An MCP configuration that attaches to the existing browser and can remain disabled outside attended QA. | Merged a Playwright MCP configuration that attaches to Chrome through CDP, with enablement off by default. No subsequent QA outcome was published in the PR. |
| C7 · Oct 2026 · [Agent Router PR #2797](https://github.com/theagentrouter/agent-router/pull/2797) | Keep a stable public MCP endpoint for end-to-end tests of connection, tool listing, and tool calls. Kiwi had temporarily replaced Context7 after a TLS problem and later became flaky. | Current connection and workflow behavior through the project's gateway, including TLS and the expected tool names and responses. | Re-enabled Context7 and switched the test assertions after the author reported the certificate issue resolved. Long-term availability remains unknown. |
| C8 · Jul 2026 · [Agent Teams PR #92](https://github.com/MGT-Insurance/agent-teams/pull/92) | Preserve interactive and background browser-based UI verification. The existing Playwright MCP setup required process and client wiring. | Real-browser parity for cross-command session persistence, snapshots, screenshots, storage state, console/network visibility, and cleanup. | Merged Playwright CLI after the PR reported live parity checks. This was a separate team and repository from C5, with a different CLI and acceptance criteria. |

## Sample and interpretation

The eight sources represent eight distinct public contributor accounts and
eight repository roots. The source links supply provenance and a way to
check independence without repeating handles here. C1's issue and follow-up
are one report. C4's issue and pilot belong to one choice. C3 and C7 share
Context7 but concern different teams and decisions. C5 and C8 both leave
Playwright MCP and remain separate observations of that same solution family.

Sources came from bounded public GitHub searches for merged MCP replacements
and transport migrations, the existing EVI-12 research, and a public search
for reported server switches. Issue reports, comments, and merged PR metadata
were checked directly. This purposive, GitHub-only sample favors decisions
with a public implementation trail. It cannot establish market frequency,
participant availability or current activity, successful deployments, or
adoption of MCP Compass.

The public cases suggest three questions for guided sessions. Will client
and transport evidence change a shortlist before setup (C1–C3, C6–C7)?
When does a usage limit turn a match into an unusable option (C2, C4)?
When do parity and operational overhead favor a CLI instead of any MCP
server (C5, C8)? These are prompts to test with a participant who has a live
decision.

## Desk reconstructions

The researcher submitted public paraphrases to MCP Compass search. These
checks examine what a prospective user could see. They count as zero guided
walkthroughs and establish no effect on the original decisions. No provider
credentials, signups, installations, or third-party MCP tool calls were used.

The following responses came from the [public search API](../../docs/API.md#search-mcp-servers)
with `page=1` and `pageSize=3`. The deployment commit and index snapshot
were unverified. Some requests timed out during the initial batch. The
observations below come from completed individual requests.
The C1 fetch and C6 existing-browser queries also timed out on retry and
yielded no usable response in this pass.

| Case and exact search text | Observed response | Researcher's decision |
| --- | --- | --- |
| C2 · Find a remote MCP server for web search and page extraction that works over HTTP with bearer authentication. | `strongMatch=false`, `totalMatches=0`. The API parsed HTTP and bearer authentication as hard constraints and reported all retrieved candidates excluded. | The exclusion explains the evidence gap. The original PR and Tavily's README identify a concrete fallback to investigate. Actual client compatibility, access and credits remain to be checked. [Tavily remote MCP documentation](https://github.com/tavily-ai/tavily-mcp#remote-mcp-server) |
| C3 · Find a hosted MCP server for public programming library documentation lookup without a local Node process. | `strongMatch=true`, `totalMatches=64`, and the parsed hard-constraint list was empty. The first three results did not include Context7 or return a recorded remote connection state. | Treat the returned ranking as leads. Check the intended client, hosting and auth before selecting. Context7's current docs confirm direct remote use without local Node and describe sign-in and key options. [Context7 client docs](https://context7.com/docs/resources/all-clients) |
| C4 · Find a web search MCP server with recurring free access, no credit card, and no paid overage. | `strongMatch=false`, `totalMatches=0`. The API parsed the three billing conditions as hard constraints and reported all retrieved candidates excluded. | Keep spend and overage unresolved until the relevant provider terms and local controls are checked. You.com currently documents a bounded keyless endpoint, which can be considered for an attended trial on the public team's terms. [You.com MCP docs](https://you.com/docs/agents/mcp-server) |
| C5 · Find an MCP server for browser-based UI testing with console logs and accessibility snapshots that minimizes model tool-schema context. | `strongMatch=true`, `totalMatches=43`. The API identified UI testing, console logs and accessibility snapshots. The parsed hard-constraint list was empty and the response gave no measurement of tool-schema context. | Use the results as a capability shortlist and compare the runner requirements and measured schema cost against the CLI that Panenco selected. |

For example, run the C2 query in PowerShell from the repository root.
Repeat with the other search texts. Inspect the constraints, abstention
reasons and first three candidates against the dated observations.

```powershell
$requirement = 'Find a remote MCP server for web search and page extraction that works over HTTP with bearer authentication.'
$request = @{requirement=$requirement;page=1;pageSize=3} | ConvertTo-Json -Compress
$response = $request | curl.exe -fsS --max-time 72 -H 'Content-Type: application/json' --data-binary '@-' 'https://mcp-compass-api.vercel.app/api/v1/mcp/search'
if ($LASTEXITCODE -eq 0) {
    $result = $response | ConvertFrom-Json
    $result | Select-Object strongMatch,totalMatches,abstentionReasons
    $result.parsedIntent.constraints
    $result.matches | Select-Object registryName,remoteConnectionState
}
```

## Review and remaining evidence

1. Open each case source and confirm the constraint and choice against the
   author report or merged diff. For C1, the linked comment is from the
   issue author. For C4, use the issue's updated requirement and the pilot PR's
   merge status. For C7, check the replacement in
   `tests/data-plane-mcp/publicmcp_test.go`. Expect eight distinct
   contributor/repository pairs and no confirmed Compass use.
2. Re-run the public search checks using the exact requirements above. The
   public index and deployment can change. Compare the fields and their
   interpretation to the dated observations.
3. Inspect the unchecked VLD-02 entry in `PLANS.md`. Merging the evidence
   report leaves the item open for 3–5 opt-in guided walkthroughs using real
   selection requirements.

For each remaining session, obtain explicit opt-in, capture the requirement
and existing workaround first, then observe the developer using MCP Compass
for a real decision. Record candidates considered, evidence they use or
cannot find, the actual decision or blocker, and their own account of
whether Compass changed it. Keep only consented, minimally identifying notes
and distinguish a follow-up by the same developer from a new participant.
