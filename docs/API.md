# REST API

Base path: `/api/v1`

## Search MCP servers

`POST /mcp/search`

Request:
```json
{
  "requirement": "Read GitHub issues, comment on them and create pull requests"
}
```

Response shape:
```json
{
  "requirement": "...",
  "keywords": ["github", "issues", "comment", "pull", "requests"],
  "matches": [
    {
      "id": "uuid",
      "registryName": "io.github.example/server",
      "title": "Example MCP",
      "description": "...",
      "version": "1.0.0",
      "score": 0.82,
      "capabilityCoverage": 0.5,
      "matchedCapabilities": ["github.issue.read"],
      "missingCapabilities": ["github.pull-request.create"],
      "reasons": ["title contains github", "description matches issues"]
    }
  ]
}
```

When structured required capabilities are available, `capabilityCoverage` is the fraction matched
by the server's normalized capability metadata. Coverage contributes 80% of the score and text
overlap contributes 20%. The matched and missing lists make that contribution explicit. For
heuristic-only requirements with no structured capabilities, `capabilityCoverage` is `null` and
ranking falls back to deterministic text overlap.

## MCP detail

`GET /mcp/{id}`

Returns normalized server detail and basic metadata.

## Local Registry sync

Only with Spring `local` profile:

`POST /dev/registry/sync?maxPages=1`

This endpoint is deliberately not intended for production exposure.
