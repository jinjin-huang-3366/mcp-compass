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
| NDCG@10 | 0.8620 |
| Acceptable server in top three | 24/24 (100.0%) |
| Forbidden-result violations in top three | 0 |
| Correct abstention when expected | 8/8 (100.0%) |
| No-match cases | 6 |
| In-memory evaluation latency guard | less than 2 seconds |

Recall@100 is micro-averaged across all graded relevant servers. NDCG@10 is macro-averaged across the 26 requirements
with graded relevance labels. Top-three acceptability excludes the two hard-condition gaps and six no-match cases,
because none has a known acceptable server. Forbidden-result violations count labelled hard-negative occurrences,
not merely queries containing one. The calibrated result view applies hard-condition eligibility before ranking output
and returns only candidates at or above the `0.30` strong-match threshold. Retrieval recall remains measured before
those filters. NDCG is measured over returned candidates, so it decreases when lower-confidence relevant candidates
are deliberately omitted.

## Calibrated behavior and remaining failures

- The SSRF-safe fetch requirement retrieves the purpose-built safe-fetch server but misses the lower-grade generic
  webclaw label; lexical negative-term removal also drops one additional relevant candidate.
- The Twilio SMS-only requirement excludes every voice-capable Twilio record before ranking and abstains.
- The GitHub no-delete requirement excludes the GitHub management records and the remaining unrelated candidates fall
  below the calibrated threshold, so it abstains instead of recommending one.
- All six no-match requirements abstain and labelled forbidden results are absent from the returned top three.

REL-06 changes the deterministic result policy and threshold without changing the REL-01 labels. Later work should
improve retrieval and ranking without hiding failures through relabelling.

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
