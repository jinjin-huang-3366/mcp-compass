# Product and technical plan

## Persona
Agent/MCP developer who knows the capability they need but does not want to manually inspect dozens of registries/repos.

## Primary job
"Given this agent requirement, tell me which MCP server I should use and why."

## Secondary job (later)
"If no suitable MCP exists, turn my underlying API/OpenAPI source into a reviewable MCP server project."

## V0.1 UX
1. Developer enters a requirement.
2. UI shows extracted search/capability intent.
3. API returns top matches.
4. Each match shows score, match reasons, and eventually missing capabilities.
5. Developer can open MCP detail metadata.

## Success measures
- Search usefulness on a fixed evaluation set.
- Percentage of queries with a clearly acceptable top-3 match.
- Search latency against local data.
- Registry freshness.
- Eventually: percentage of "no match" queries successfully converted into generated MCP contracts.

## What creates defensibility
Not the raw Registry list. The valuable layer is accumulated normalized capabilities, tool metadata, quality/trust data, validation history, and relevance feedback/evaluation.
