# Search and ranking evaluation

Search quality must be measured before adding complexity such as vector retrieval or LLM reranking.

## Initial evaluation dataset
Create a versioned JSON/JSONL fixture with at least 20 representative developer queries. For each query record:
- natural-language requirement;
- required capabilities;
- forbidden/important constraints;
- known acceptable MCP server(s), if any;
- known clearly bad matches;
- notes explaining relevance.

The V0.1 lexical baseline uses
`backend/src/test/resources/fixtures/ranking/relevance-evaluation-v1.json`. Its labels are intentionally
small and reviewable: each query names the bounded candidate set, acceptable servers, and clearly bad
matches. `RankingEvaluationTest` validates the structured labels and evaluates the production heuristic
analyzer and lexical ranker. Update labels by review rather than deriving them from ranker output.

## Metrics
Start with:
- top-1 acceptable match rate;
- top-3 acceptable match rate;
- mean reciprocal rank where labels are available;
- percentage of queries where the system should say "no strong match";
- latency for retrieval and ranking.

The checked-in baseline report is `docs/reports/LEXICAL_RANKING_BASELINE_V1.md`. Reproduce its metrics with:

```bash
./mvnw -pl backend -Dtest=RankingEvaluationTest test
```

The V0.1 top-three exit acceptance review is recorded in
`docs/reports/V01_TOP3_ACCEPTANCE.md`. It fixes ten manually selected requirements from the versioned fixture
and requires every one to return a human-labelled acceptable server within its first three ranked results.
The same `RankingEvaluationTest` command reproduces that 10/10 acceptance check.

The unit test uses a generous two-second guard for the in-memory fixture. This catches gross regressions
without treating workstation-dependent nanosecond timings as a stable benchmark. Database retrieval latency
is out of scope for this fixture and should be measured separately when a repeatable seeded database benchmark
is available.

## V0.6 Registry baseline

REL-01 adds a separate production-grounded corpus rather than changing the deliberately synthetic V0.1 fixture:

- `backend/src/test/resources/fixtures/ranking/registry-snapshot-2026-08-31.json` is a normalized, scoped recording
  of publisher metadata returned by the official Registry's latest-version list/detail endpoints on 2026-08-31;
- `backend/src/test/resources/fixtures/ranking/registry-relevance-v1.json` contains 32 independently reviewable
  requirements and graded relevance, acceptable-result, forbidden-result, constraint, and abstention labels; and
- `docs/reports/REGISTRY_RELEVANCE_BASELINE_V1.md` records the current metrics and known failures.

The corpus covers GitHub no-delete, Twilio SMS, read-only PostgreSQL, web documentation, related hard negatives,
and six no-match requirements. `RegistryRelevanceEvaluationTest` reproduces the production heuristic analysis,
lexical field matching, 100-candidate cap, and deterministic ranker in memory. The snapshot order makes the fixture
repeatable; it is not a claim about unspecified database row order or full-production latency.

Metrics are defined as follows:

- **Recall@100** is micro-averaged labelled relevant servers retrieved in the first 100 lexical candidates.
- **NDCG@10** is macro-averaged over requirements with one or more graded relevance labels, using gains `2^grade-1`.
- **Top-three acceptability** is the share of requirements with a known constraint-satisfying server that place one
  in the first three ranked results.
- **Forbidden-result violations** count explicitly labelled hard-negative result occurrences in the first three.
- **Abstention** uses the current score threshold and is reported over requirements labelled to abstain, including
  hard-condition gaps as well as no-match cases.

Reproduce the V0.6 baseline with:

```bash
./mvnw -pl backend -Dtest=RegistryRelevanceEvaluationTest test
```

Registry descriptions are untrusted publisher claims. Labels describe what the recorded metadata supports; they do
not certify implementation behavior, safety, or completeness. Ranking changes must update the report and exact metric
assertions, but must never relabel the corpus merely to agree with new output.

## Rule
Do not introduce embeddings/LLM reranking because they sound better. Add them only when the evaluation set shows a specific baseline failure and the new approach improves it without unacceptable cost/latency/regression.

## Vector retrieval gate

SRCH-06 adds the hybrid retrieval mechanism only after `lexical-relevance-v1` and its report exist. It remains disabled
by default because the current labels identify constraint/capability ranking failures rather than proving a particular
embedding provider improves relevance. Before enabling vector retrieval by default, record a provider/model-specific
comparison against this fixture (plus a seeded database latency benchmark), including top-1, top-3, MRR, no-strong-match
accuracy, bad matches in the top three, embedding cost, and end-to-end latency. Do not compare vectors from different
models; stored rows are filtered by the configured model identifier.
