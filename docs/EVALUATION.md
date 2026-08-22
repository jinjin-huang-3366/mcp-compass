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

The unit test uses a generous two-second guard for the in-memory fixture. This catches gross regressions
without treating workstation-dependent nanosecond timings as a stable benchmark. Database retrieval latency
is out of scope for this fixture and should be measured separately when a repeatable seeded database benchmark
is available.

## Rule
Do not introduce embeddings/LLM reranking because they sound better. Add them only when the evaluation set shows a specific baseline failure and the new approach improves it without unacceptable cost/latency/regression.

## Vector retrieval gate

SRCH-06 adds the hybrid retrieval mechanism only after `lexical-relevance-v1` and its report exist. It remains disabled
by default because the current labels identify constraint/capability ranking failures rather than proving a particular
embedding provider improves relevance. Before enabling vector retrieval by default, record a provider/model-specific
comparison against this fixture (plus a seeded database latency benchmark), including top-1, top-3, MRR, no-strong-match
accuracy, bad matches in the top three, embedding cost, and end-to-end latency. Do not compare vectors from different
models; stored rows are filtered by the configured model identifier.
