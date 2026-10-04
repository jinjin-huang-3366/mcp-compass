# Evidence model

Evidence represents an underlying developer problem, not sentiment or a commitment to a requested implementation.
Each item retains a stable ID, canonical URL, bounded excerpt/summary, author, publication time, source/thread identity,
problem key and statement, product area, relevance/confidence, potential-user score, possible solutions, and status.

The CLI stores only enough public context for traceability. Stable source IDs and canonical URLs make reruns idempotent.
Exact URL duplicates, repeated normalized content, direct quote copies, and one author repeating the same problem in one
thread do not increase independent evidence.

Cluster independence is conservative:

- `evidenceCount` is the number of retained evidence items;
- `uniqueAuthors` counts normalized authors once;
- `uniqueThreads` counts canonical discussion/story roots once;
- `uniquePlatforms` counts source adapters once;
- `independentEvidenceCount` counts distinct author/thread pairs, excluding duplicates.

A normal roadmap candidate needs the configured minimum independent authors and independent threads (defaults: 3 and
2). A single security, data-loss, false-safety, broken-core, or legal/compliance report is marked `URGENT_REVIEW`; it
does not become an automatic feature. Recommendations state the problem first and list feature candidates only as
possible solutions. They never edit `PLANS.md`.

Trend fields compare items seen in the last 7/30 days with the immediately preceding equal window. Label conclusions
from fewer than five independent items `small sample`; do not claim statistical significance.
