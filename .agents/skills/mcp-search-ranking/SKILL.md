---
name: mcp-search-ranking
description: Work on requirement parsing, MCP candidate retrieval, capability normalization, ranking, scoring explanations, relevance evaluation, embeddings, or search quality for MCP Compass.
---

# Search/ranking workflow

1. Read `/AGENTS.md`, `/docs/ARCHITECTURE.md`, and `/docs/PROJECT_PLAN.md`.
2. Express the user's request as structured requirements before complex ranking.
3. Capability coverage is the intended dominant feature once capability data exists.
4. Keep hard constraints separate from soft ranking features.
5. Retrieval and ranking are separate stages: retrieve broadly, rerank narrowly.
6. Deterministic features (coverage, status, recency, installability) must be calculated in code, not guessed by an LLM.
7. LLM reranking, if introduced, must operate on a bounded candidate set and have a fallback.
8. Every ranking change requires evaluation against a fixed query/result dataset or unit/golden tests.
9. Return human-readable reasons and missing capabilities; never expose only an unexplained score.
