# REST API

Base path: `/api/v1`

## Search MCP servers

`POST /mcp/search`

Request:
```json
{
  "requirement": "Read GitHub issues, comment on them and create pull requests",
  "page": 1,
  "pageSize": 10
}
```

`page` is one-based and defaults to `1`. `pageSize` defaults to `10` and is limited to `25`.
The web UI mirrors the requirement and current page in `q` and `page` URL parameters so a search
can be bookmarked or shared. Page one omits the optional `page` parameter.

Response shape:
```json
{
  "requirement": "...",
  "keywords": ["github", "issues", "comment", "pull", "requests"],
  "page": 1,
  "pageSize": 10,
  "totalMatches": 18,
  "totalPages": 2,
  "matches": [
    {
      "id": "uuid",
      "registryName": "io.github.example/server",
      "title": "Example MCP",
      "description": "...",
      "version": "1.0.0",
      "score": 0.82,
      "qualityScore": 0.75,
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

`qualityScore` is a deterministic value from 0 to 1 built from persisted Registry provenance,
installability, declared tool-schema coverage, and GitHub enrichment (archive status, license, and
activity). It contributes a bounded 15% of secondary ranking so capability coverage remains dominant.
Missing enrichment adds no credit; it is never guessed and search does not call GitHub or an MCP server.

## MCP detail

`GET /mcp/{id}`

Returns normalized server detail and basic metadata. `toolSchemaStatus` reports whether bounded,
statically declared MCP input schemas were `DISCOVERED`, `PARTIAL`, `INVALID`, or
`NOT_DISCOVERABLE`; `toolSchemaInspectedAt` identifies the Registry ingestion that last inspected
the metadata. These fields do not imply that MCP code or a server tool was executed.

## Local Registry sync

Only with Spring `local` profile:

`POST /dev/registry/sync?maxPages=1`

This endpoint is deliberately not intended for production exposure.
