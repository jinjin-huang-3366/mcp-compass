# MCP Compass Reddit launch preparation v1

Status: prepared only. Nothing in this document authorizes or records a Reddit submission.

## Evidence boundary

These drafts promote the public [`v0.1.0` release](https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0)
at source commit `9ca1212a902639dd279cf90e7628974314135f6d`. The release commit passed
[CI run 34709603842](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709603842), and
[deployment run 34709693340](https://github.com/jinjin-huang-3366/mcp-compass/actions/runs/34709693340)
deployed and smoke-tested that exact source. The canonical demo is
<https://mcp-compass-iota.vercel.app/>.

Public copy below uses only deployed claims C1, C2, C4, and C5 from the
[launch-package evidence ledger](LAUNCH_PACKAGE_V1.md#release-notes-and-evidence-ledger). It does not claim complete
Registry coverage, hosted validation-worker availability, security certification, or measured adoption.

## Community and rules check

Rules and recent community formats were checked on 2026-09-13. They can change, so the operator must re-open the
linked community and rules immediately before submitting.

| Community | Why it is relevant | Observed constraints | Prepared format |
| --- | --- | --- | --- |
| [`r/mcp`](https://www.reddit.com/r/mcp/) | Focused on Model Context Protocol developers; current posts include detailed project showcases. | [Rules](https://www.reddit.com/r/mcp/about/rules/) allow a fully launched service with proper affiliation disclosure, reject astroturfing and AI-generated promotional slop, and require the `showcase` tag for work the author built. | One text post with `showcase` flair. |
| [`r/AI_Agents`](https://www.reddit.com/r/AI_Agents/) | Agent developers are the intended MCP Compass users. | [Rules](https://www.reddit.com/r/AI_Agents/about/rules/) direct project links to the weekly project display thread, ask that links not appear in standalone posts, limit self-promotion to roughly one in ten contributions, and reject low-effort traffic-driving posts. | One comment for the current weekly project display thread, not a standalone post. |

Do not submit both drafts as undifferentiated cross-posts. The maker must read and revise the language into their own
voice before posting, especially because `r/mcp` explicitly rejects AI-generated promotional slop. Check the posting
account's participation history against `r/AI_Agents`' self-promotion ratio; skip that community if it does not
qualify. Never automate votes, replies, or repeated posting.

## Package REDDIT-MCP revision 1

- **Intended action:** prepare for later manual submission; do not publish from this package.
- **Destination:** `r/mcp`.
- **Format/flair:** text post, `showcase`.
- **Account:** to be selected and verified by the operator.
- **Media and alt text:** none.
- **Schedule:** none; choose only after a fresh rules check and exact-copy approval.
- **Measurement:** preserve the final Reddit permalink and compare the `reddit.com` referrer domain in Vercel Web
  Analytics. Do not claim UTM-level attribution.

### Title

I built MCP Compass to rank MCP servers from capability requirements

### Body

Disclosure: I am the maker of MCP Compass, an open-source tool for developers choosing MCP integrations.

The discovery problem I kept running into was that a server name or directory category does not tell me whether the
server meets an agent's actual requirements. For example:

> Query PostgreSQL read-only; forbid inserts, updates, deletes, and schema writes.

MCP Compass parses required and forbidden capabilities, searches locally synchronized public Registry metadata,
ranks candidates, and shows the matched and missing capabilities behind the result. When the evidence is weak or a
hard restriction conflicts, it can return no strong match instead of padding the list with unrelated servers.

The workflow is deliberately reuse-first. If an existing server is not adequate, a separate path turns an OpenAPI
document into a proposed tool contract for review before exporting a TypeScript MCP server project.

The main limitation in the hosted demo is validation: generated-project jobs need a separately operated isolated
worker, so a queued job is not a security certification. Registry coverage is also a synchronized snapshot, not the
whole MCP ecosystem.

Demo: <https://mcp-compass-iota.vercel.app/>

Source and release: <https://github.com/jinjin-huang-3366/mcp-compass/releases/tag/v0.1.0>

I would value a real capability requirement that current MCP directories make difficult to evaluate, especially one
with a hard prohibition. I am interested in whether the ranking explanation makes the trade-off clear, not just
whether the first result looks plausible.

## Package REDDIT-AGENTS revision 1

- **Intended action:** prepare for later manual submission; do not publish from this package.
- **Destination:** the current `r/AI_Agents` weekly project display thread, whose exact URL must be selected at posting
  time.
- **Format/flair:** comment in that thread; no standalone post and no flair.
- **Account:** to be selected and checked for compliant participation history by the operator.
- **Media and alt text:** none.
- **Schedule:** none; choose only after locating the current thread, rechecking rules, and obtaining exact-copy
  approval.
- **Measurement:** preserve the final comment permalink and compare the `reddit.com` referrer domain in Vercel Web
  Analytics. Do not claim UTM-level attribution.

### Comment

I am the maker of MCP Compass, an open-source developer tool for a specific agent-building problem: choosing an MCP
server from the capabilities and restrictions an agent needs, rather than starting with a server name.

A concrete input is: `Query PostgreSQL read-only; forbid inserts, updates, deletes, and schema writes.` The tool
parses the positive and negative intent, searches a locally synchronized Registry index, ranks candidates, and shows
what matched or was missing. It can abstain when the evidence is too weak instead of returning an unrelated list.

If reuse is inadequate, the separate generation path starts with an OpenAPI-derived tool contract that the developer
reviews before code export. The hosted demo does not run the isolated validation worker, so queued validation is
bounded evidence rather than a safety guarantee.

Demo: <https://mcp-compass-iota.vercel.app/>

I would appreciate examples of agent requirements where a directory search gives plausible-looking but unusable MCP
servers. Hard restrictions are particularly useful for testing the explanations.

## Publication handoff

Preparation and publication are separate actions. Before submitting either item:

1. Recheck the destination's live rules, required flair or thread, and the posting account's eligibility.
2. Have the maker rewrite or affirm the copy in their own voice and verify every claim and URL.
3. Fill in the exact account, final destination or thread URL, exact copy, and an ISO 8601 publication time with UTC
   offset and named timezone.
4. Present the complete package under the repository's exact-copy approval gate and obtain explicit approval in the
   current conversation.
5. Submit manually, preserve the resulting permalink, and do not automate engagement.

Any change to the account, community, thread, title, body, link, media, or schedule invalidates earlier approval.
