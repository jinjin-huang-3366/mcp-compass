# Engagement policy

Draft only when participation adds specific technical value. Internally answer: why participate, what technical value
can be added, whether MCP Compass needs mentioning, and whether the reply remains useful without its name or URL.
Prefer a useful distinction, reproducible technique, limitation, or clarifying question. Reject generic praise plus a
link. Disclose the maker relationship when relevant, and never automate promotion, voting, DMs, mass comments, or rule
circumvention.

An approval package must show one source, repository when applicable, discussion title, exact canonical target URL,
reason to participate, evidence cluster, potential-user level, exact final reply, whether MCP Compass is mentioned,
whether a product link is present, known account, and exact `CREATE` or `REPLY` action.

Drafting creates a proposal, not authorization. After explicit conversational approval, use `approve` with the
proposal ID and displayed content hash. Immediately run `publish` without editing the proposal. Publishing checks the
approved hash, target, operation, GitHub authentication, and proposal state, performs one official
`addDiscussionComment` mutation, records the returned comment ID/URL, and consumes the approval. A changed body,
target, operation, expired approval, failed auth, or prior publication stops safely with the draft intact.

Consume approval and record a local attempt immediately before the mutation. If the mutation result is failed or
uncertain, mark the attempt `PUBLISH_FAILED_OR_UNCERTAIN` and do not retry or approve the same proposal again; the
operator must reconcile the target manually to avoid a duplicate comment.

Never publish Hacker News or Reddit content through this skill. HN proposals are manual-post packets. Reddit write
support is deliberately absent in V0.1 even when read configuration is added later.
