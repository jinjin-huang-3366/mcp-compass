# Registry relevance baseline v1

## Scope

This report records the REL-01 baseline for the current heuristic analyzer, lexical retrieval, and deterministic
ranker against a scoped official Registry snapshot recorded on 2026-08-31. The snapshot contains 28 normalized
latest-version records selected from official list/detail responses for the evaluated cohorts. It retains publisher
name, title, description, version, status, provenance, repository, and install-option counts; it does not install or
execute any server.

The 32 hand-labelled requirements include six GitHub cases, five Twilio cases, seven PostgreSQL cases, eight web-docs
cases, and six no-match cases. Two hard-condition cases intentionally have relevant servers but no acceptable server:
GitHub with a no-delete boundary and Twilio SMS with voice unavailable. Labels were reviewed from the recorded
publisher metadata, not derived from ranker output.

## Results

| Metric | Baseline |
| --- | ---: |
| Recall@100 | 51/53 (96.2%) |
| NDCG@10 | 0.9433 |
| Acceptable server in top three | 24/24 (100.0%) |
| Forbidden-result violations in top three | 13 |
| Correct abstention when expected | 6/8 (75.0%) |
| No-match cases | 6 |
| In-memory evaluation latency guard | less than 2 seconds |

Recall@100 is micro-averaged across all graded relevant servers. NDCG@10 is macro-averaged across the 26 requirements
with graded relevance labels. Top-three acceptability excludes the two hard-condition gaps and six no-match cases,
because none has a known acceptable server. Forbidden-result violations count labelled hard-negative occurrences,
not merely queries containing one. Abstention uses the existing `0.25` strong-match threshold.

## Observed baseline failures

- The SSRF-safe fetch requirement retrieves the purpose-built safe-fetch server but misses the lower-grade generic
  webclaw label; lexical negative-term removal also drops one additional relevant candidate.
- The Twilio SMS-only requirement ranks all three voice-capable Twilio records above the threshold in this isolated
  lexical baseline. Production search now applies REL-02 eligibility filtering before ranking; this report remains a
  retrieval/ranker-only comparison.
- Seven other top-three positions contain labelled hard negatives from adjacent domains. Examples include a generic
  docs server for CUDA, a read-only PostgreSQL server for administrative CRUD, and a read-only docs server for
  Markdown publishing.
- The GitHub no-delete case does abstain, but for the wrong practical reason: generic lexical matches dilute the
  relevant GitHub records below the threshold. REL-02 represents and enforces negative intent in the search service.

These results establish the current baseline; REL-01 does not tune the analyzer, retrieval, ranker, threshold, or
labels. Later work should improve the metrics without hiding failures through relabelling.

## Limitations

This is a deterministic, scoped snapshot benchmark, not a full Registry export and not a production database latency
test. The in-memory retriever mirrors current lexical field matching and the 100-candidate cap while preserving fixture
order for repeatability; the production query currently has no explicit ordering before its limit. Publisher metadata
is untrusted and may omit tool-level behavior, so a label is evidence about advertised fit rather than a safety
certification. The recorded snapshot must be versioned again when corpus drift is intentionally evaluated.

## Reproduce

From the repository root:

```bash
./mvnw -pl backend -Dtest=RegistryRelevanceEvaluationTest test
```

The test validates snapshot provenance and label integrity, recreates every metric above, prints each requirement's
top three for desk review, pins the rounded metric values, and fails if the in-memory run takes two seconds or more.
