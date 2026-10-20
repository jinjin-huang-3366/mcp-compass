---
name: mcp-feedback-research
description: Research MCP community discussions, collect product evidence, identify MCP Compass users, analyse ecosystem pain points, and prepare approval-gated community replies. Use for requests such as find MCP feedback, research MCP discussions, cluster product evidence, or prepare useful GitHub/Hacker News participation; do not use for generic promotion or autonomous posting.
---

# MCP feedback research

Run the local feedback workflow as an evidence pipeline:

`COLLECT → FILTER → EXTRACT → CLUSTER → SCORE → REPORT → DRAFT → APPROVE → POST → RECORD`

Use `.agents/skills/mcp-feedback-research/scripts/feedback_agent.py` from the repository root. Collection, analysis,
reporting, and drafting are autonomous local actions. Prefer a high-signal empty result to low-value participation.

Read the references as needed:

- [evidence-model.md](references/evidence-model.md) before interpreting clusters, independence, trends, or roadmap candidates;
- [source-strategy.md](references/source-strategy.md) before collecting or adding a source;
- [engagement-policy.md](references/engagement-policy.md) before drafting, approving, or publishing any reply.

Never treat draft approval as publishing approval. Immediately before a GitHub mutation, show the exact destination,
URL, account when known, final text, link presence, and create/reply operation. Record approval only after the operator
explicitly approves that exact package. Any change invalidates approval. Publish one destination per approval and stop
after one mutation. Hacker News is always manual-post/read-only, Reddit is disabled by default, and Typefully remains a
separate marketing workflow.

Raw state belongs only under ignored `.local/feedback/`. Reports must retain canonical evidence URLs while excluding
credentials, cookies, tokens, unnecessary full discussion copies, and unnecessary personal data.
