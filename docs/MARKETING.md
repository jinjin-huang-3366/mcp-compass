# Marketing operations

MCP Compass uses Typefully's remote MCP server as the managed path for social drafts, scheduling, and publishing. The
project-scoped Codex configuration names the server `typefully`, uses Typefully's shared Streamable HTTP endpoint, and
keeps authentication in Codex's OAuth credential store rather than this repository.

## Connect Typefully

Prerequisites:

- trust this repository so Codex loads `.codex/config.toml`;
- have access to the intended Typefully workspace and social set;
- connect the required social accounts in Typefully itself.

From the repository root:

```powershell
codex mcp get typefully
codex mcp login typefully
codex mcp list
```

The login command opens Typefully's OAuth approval flow. After approving it, restart the Codex client if the tools do
not appear, then use `/mcp` to confirm that `typefully` is connected. Do not paste an API key into
`.codex/config.toml`, a prompt, a commit, or a GitHub secret for this integration.

The server is optional (`required = false`), so contributors who are not launch operators can still start Codex. Its
tool approval mode is `prompt`: listing accounts or drafts and any mutation require an operator confirmation in the
client. That tool-level prompt supplements the marketing approval rules; it does not replace them.

## Managed draft and publishing boundary

Before any Typefully tool call, name the intended action, social set/account, and channel. Treat each of these as a
separate external-state change requiring explicit authorization:

1. create a remote draft;
2. edit or delete a remote draft;
3. schedule or reschedule content;
4. publish content.

Preparing local copy or an approval package does not authorize creating a remote draft. Publishing requires approval
of the exact final copy, destinations, links or media, and schedule immediately beforehand. If any of those details
change, obtain fresh approval. Never publish merely because a draft was approved.

### Exact-copy approval gate

Before a scheduling or publishing call, present one final, independently reviewable package for each post. Include a
stable package label and revision, intended action, exact social set/account and destination, complete copy, every
link and media/alt-text value, and the schedule as an ISO 8601 timestamp with UTC offset plus a named timezone. For a
publish-now action, use `immediately after approval` instead of a scheduled timestamp. State `none` rather than
leaving optional media or links ambiguous.

Ask the human to approve that exact revision for the stated action. Approval must be explicit and occur in the
current conversation after the complete package is shown. An approved local draft, an approved unscheduled remote
draft, a checked-in package, silence, or a general earlier instruction to launch does not authorize scheduling or
publishing.

Immediately before the external call, compare the account, channel, action, copy, links, media, alt text, and schedule
with the approved package. Any change, including a new time or destination, invalidates approval and requires the
revised complete package to be shown again. Stop for fresh approval if the scheduled time has passed or the platform
would materially transform the content. The operational checklist and reusable approval prompt are in
[`approval-gate.md`](../.agents/skills/mcp-compass-marketing/references/approval-gate.md).

Example final gate (illustrative only; it is not an approval):

```text
Package: X-LAUNCH revision 3
Action: schedule
Account/channel: MCP Compass social set / X
Copy: <the complete final post>
Links: https://example.test/final
Media and alt text: none
Schedule: 2026-09-15T16:00:00+01:00 (Europe/London)

Approve this exact revision for the stated account, copy, media, and schedule? Reply with explicit approval or
request changes. No external action occurs without it.
```

Example safe handoff:

```text
Request: Prepare an X launch post for review.
Response: Return the proposed copy and link locally; do not call Typefully.

Request: Create that exact copy as an unscheduled Typefully draft in the MCP Compass social set.
Response: State the account, X destination, and unscheduled draft action; after explicit confirmation, create only the
draft and return its Typefully URL. Do not schedule or publish it.
```

If `typefully` is unavailable, unauthenticated, or missing the requested connected channel, return an import-ready
draft package and report the missing prerequisite. Do not fall back to browser automation or another publishing API.

## Disconnect or recover

Run `codex mcp logout typefully` to remove locally stored OAuth credentials. Use `codex mcp login typefully` to repeat
authentication after access changes. Typefully's health endpoint is `https://mcp.typefully.com/health`; checking it is
read-only and does not verify account authorization.

Configuration details were verified on 2026-09-08 against the official
[Typefully MCP guide](https://support.typefully.com/en/articles/13128440-typefully-mcp-server) and
[OpenAI MCP configuration documentation](https://developers.openai.com/codex/mcp).
