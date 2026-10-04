# Source strategy

Configuration lives in `feedback/sources.json`; do not scatter repository, feed, or threshold constants through code.
All adapters emit the same bounded source-item shape with stable external ID, canonical URL/thread URL, source type,
author, timestamps, title, and short public text.

## GitHub Discussions

Use the authenticated local `gh` session and GraphQL Discussions API. Collect configured repositories in bounded
updated-at order, including bounded comments/replies. Canonical URLs come from GitHub responses. Publishing uses only
the official `addDiscussionComment` mutation after exact-copy approval. Never store a PAT or add delete, moderation,
answer, reaction, or voting operations.

Schema reference: <https://docs.github.com/en/graphql/reference/discussions#adddiscussioncomment>.

## Hacker News

Use `https://hacker-news.firebaseio.com/v0/` for configured `newstories`, `showstories`, and `askstories` feeds plus
bounded `item/{id}` and `user/{id}` reads. Filter locally and avoid repeatedly walking old trees. HN remains
`READ → ANALYSE → DRAFT`; link the operator to the exact item for manual participation.

## Reddit

Expose `ENABLED`, `DISABLED_NO_CREDENTIALS`, or `DISABLED_NOT_CONFIGURED`. V0.1 defaults to
`DISABLED_NOT_CONFIGURED`, uses no HTML scraping, and provides no write operation. Add later read support only through
an officially supported authenticated API and preserve community-rule metadata when available.

To add a source, implement the common adapter contract, bound network requests and retained content, define canonical
URLs/thread identities, add deterministic fixture tests, document authentication/terms constraints, and keep every
write capability separately approval-gated or absent.
