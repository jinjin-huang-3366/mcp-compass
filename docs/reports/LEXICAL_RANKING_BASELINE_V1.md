# Lexical ranking baseline v1

## Scope

This report records the deterministic V0.1 baseline for the heuristic requirement analyzer and text-overlap
ranker. It uses `lexical-relevance-v1`, a hand-labelled fixture of 20 developer requirements, 27 synthetic MCP
server metadata records, and a bounded four-candidate set per query. Eighteen queries have an acceptable match;
two intentionally have no acceptable candidate.

The fixture includes structured required capabilities, forbidden capabilities, and constraints for label review.
The current lexical ranker does not score those structured fields. That limitation is visible in the results and
must not be hidden by changing labels to agree with the implementation.

## Results

| Metric | Baseline |
| --- | ---: |
| Top-1 acceptable match rate (labelled queries) | 16/18 (88.9%) |
| Top-3 acceptable match rate (labelled queries) | 18/18 (100.0%) |
| Mean reciprocal rank | 0.9444 |
| Queries expected to say no strong match | 2/20 (10.0%) |
| Correct strong/no-strong-match decisions at score 0.25 | 20/20 (100.0%) |
| Queries with a clearly bad match in the top 3 | 16/20 (80.0%) |
| In-memory analysis/ranking latency guard | less than 2 seconds for all 20 queries |

The current run includes the V0.1 maintenance/status secondary feature, while this fixture does not supply
normalized capability metadata to `RankingService`. Active status can therefore give otherwise zero-overlap
candidates a positive score and bring them into a top three. This integration moved the clearly bad top-three
count from the original lexical result of 13/20 to 16/20; the regression remains visible rather than being
hidden by relabelling the fixture.

## Interpretation

The baseline gets an acceptable server into every labelled top three, but it misses top one twice. The PostgreSQL
read-only query ties with the clearly unsafe admin server because forbidden operation words are rewarded as
lexical matches, and the Twilio SMS-only query ranks a voice-capable server first for the same reason. Clearly bad
candidates also remain common within the top three. These are concrete baseline failures for capability and
constraint-aware ranking work; they do not justify embeddings by themselves.

The latency guard covers only deterministic analysis and ranking over the fixed in-memory candidate sets. It is
not a database retrieval benchmark and should not be presented as end-to-end search latency.

## Reproduce

From the repository root:

```bash
./mvnw -pl backend -Dtest=RankingEvaluationTest test
```

The test validates fixture integrity, recreates the metrics above, and fails if the baseline changes or takes at
least two seconds. When ranking behavior changes intentionally, review the per-query labels first, explain the
movement in this report, and update the metric assertions in the same change.
