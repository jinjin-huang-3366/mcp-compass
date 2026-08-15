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

## Metrics
Start with:
- top-1 acceptable match rate;
- top-3 acceptable match rate;
- mean reciprocal rank where labels are available;
- percentage of queries where the system should say "no strong match";
- latency for retrieval and ranking.

## Rule
Do not introduce embeddings/LLM reranking because they sound better. Add them only when the evaluation set shows a specific baseline failure and the new approach improves it without unacceptable cost/latency/regression.
