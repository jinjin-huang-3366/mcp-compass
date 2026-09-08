---
name: mcp-compass-marketing
description: Prepare evidence-backed MCP Compass launch campaigns, channel-specific copy, approval packages, publishing through an available Typefully MCP connection, and feedback triage. Use for launch planning, release promotion, social drafts, or post-launch feedback; do not use for product implementation or unapproved external posting.
---

# MCP Compass marketing

Turn verified product changes into an accurate developer launch while keeping every external publication under human control.

## Evidence and positioning

Build claims from the smallest relevant primary sources: the selected GitHub release or commit range, merged pull requests, `PLANS.md`, deployed workflow evidence, and current product documentation. Distinguish shipped production behavior from merged-but-undeployed work and planned work. Never invent adoption, performance, security, compatibility, or benchmark claims.

Write for agent and MCP developers. Preserve MCP Compass's core positioning:

- describe a requirement, discover and compare existing MCP servers, and understand the ranking evidence;
- reuse an adequate existing server before generating one;
- generate from a reviewed tool contract only when reuse is inadequate;
- treat validation as bounded evidence, not a security certification.

Ask for a release, tag, commit range, or launch scope only when it cannot be inferred safely. For a full multi-channel campaign, read [references/channel-playbook.md](references/channel-playbook.md).

When generating launch content, create an evidence ledger before writing copy. Give each public claim a stable ID,
link it to a release note and at least one primary repository or workflow source, and record whether it is deployed,
merged but not deployed, or planned. Copy may use only deployed claim IDs. If there is no GitHub release, use an
exact deployed commit as the release boundary and say that explicitly; do not imply that a tag or release exists.
Keep time-varying measurements qualified with the date or workflow run that observed them.

## Campaign workflow

1. Verify the release boundary and whether it is deployed.
2. Produce a short brief containing audience, problem, shipped proof points, limitations, call to action, canonical link, and measurement plan.
3. Draft native copy for only the requested channels. Avoid identical cross-posts and unsupported superlatives.
4. Add consistent referral parameters when analytics support is verified; otherwise identify tracking as a dependency instead of fabricating it.
5. Present an approval package with exact copy, media/link references, account/channel, and proposed timing.
6. Publish or schedule only after the user explicitly approves that exact package. Any material copy, link, channel, account, or schedule change invalidates the prior approval.
7. Return publication URLs and a concise record of what was published. Never claim success from a draft or queued action.

The checked-in [launch package](../../../docs/marketing/LAUNCH_PACKAGE_V1.md) is the initial developer-launch fact
sheet and draft set. Re-verify its release boundary and evidence links before requesting approval; it is not a record
of approval or publication.

## Typefully boundary

Use Typefully MCP only when its tools are available and the requested account/channel is connected. Creating a remote draft, scheduling, editing a scheduled post, or publishing changes external state: state the intended action and obtain authorization for it. Publishing always requires approval of the exact final copy and schedule immediately beforehand.

If Typefully MCP is unavailable or does not support a requested channel, produce an import-ready draft package and report the missing integration. Do not silently switch to browser automation or post directly through another service.

## Community and feedback

Respect each community's current rules and disclose affiliation. Product Hunt, Hacker News, and Reddit material must be useful in the community's native format rather than disguised advertising. Do not automate replies, voting, engagement, or repeated posting.

Summarize feedback with its source URL, theme, reproducibility/evidence, and product impact. Deduplicate against existing GitHub issues. Draft proposed issues first; create them only with explicit user authorization, and avoid copying personal data that is not needed to act on the feedback.

## Completion

Report the evidence boundary, prepared artifacts, approvals obtained, external actions actually completed, publication URLs, measurement tags, and anything still awaiting a connector or human decision.
