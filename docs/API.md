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
      "score": 0.576,
      "qualityScore": 0.2,
      "capabilityCoverage": 0.5,
      "matchedCapabilities": ["github.issue.read"],
      "missingCapabilities": ["github.pull-request.create"],
      "rankingExplanation": {
        "contributions": [
          {"feature": "capabilityCoverage", "featureScore": 0.5, "weight": 0.8, "contribution": 0.4},
          {"feature": "retrievalRelevance", "featureScore": 1.0, "weight": 0.17, "contribution": 0.17},
          {"feature": "quality", "featureScore": 0.2, "weight": 0.03, "contribution": 0.006}
        ],
        "preAdjustmentScore": 0.576,
        "statusMultiplier": 1.0
      },
      "reasons": ["title matches github", "active Registry status"]
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

Coverage matching is deterministic and normalizes common Registry/tool naming variants. For
example, a discovered `github.create_pull_requests` capability covers the requested
`github.pull-request.create` capability; the response reports the canonical requested name.

`rankingExplanation` shows the exact deterministic arithmetic behind `score`. Each contribution is
the feature's normalized signal multiplied by its effective weight. Effective weights reflect whether
structured capability coverage is available: coverage receives 80% when present, while retrieval and
quality share the remaining 20%; otherwise retrieval and quality use 85% and 15%. The contributions
sum to `preAdjustmentScore`, then `statusMultiplier` makes the final deprecated-status adjustment
explicit (`0.5` for deprecated servers and `1.0` otherwise).

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

## Accept an OpenAPI source

`POST /generation/sources/openapi`

Upload an OpenAPI 3.x JSON or YAML file as multipart form data:

```bash
curl -F "file=@petstore.yaml" http://localhost:8080/api/v1/generation/sources/openapi
```

Or submit a public HTTPS URL as JSON:

```json
{"url":"https://developer.example.com/openapi.json"}
```

The backend fetches URL sources with redirects disabled, rejects credentials, non-default ports, and
hosts that resolve to private/local addresses, and limits both source types to 2 MiB. It parses the
document without executing source content and returns a summary for the later contract-design stage:

```json
{
  "sourceType": "FILE",
  "sourceLocation": "petstore.yaml",
  "openApiVersion": "3.1.0",
  "title": "Pet Store",
  "apiVersion": "1.0.0",
  "pathCount": 1,
  "operationCount": 2
}
```

This endpoint only accepts and validates the source. It does not select endpoints, propose MCP tools,
or generate code.

## Propose an MCP tool contract

`POST /generation/contracts/openapi`

Submit the same multipart `file` or JSON `url` request supported by the OpenAPI source endpoint. The
backend returns a versioned, reviewable contract with one proposed tool per OpenAPI operation. The
proposal includes each tool's name, description, JSON input/output schemas, source HTTP operation,
declared authentication requirements, and conservative risk classification.

For example, a `GET /pets` operation with `operationId: listPets` produces:

```json
{
  "contractVersion": "1.0",
  "status": "PROPOSED",
  "source": {
    "type": "FILE",
    "location": "petstore.yaml",
    "openApiVersion": "3.1.0",
    "title": "Pet Store",
    "apiVersion": "1.0.0"
  },
  "tools": [{
    "name": "list_pets",
    "description": "List pets",
    "inputSchema": {"type": "object", "properties": {}, "additionalProperties": false},
    "outputSchema": {"type": "array", "items": {"type": "object"}},
    "sourceOperation": {"method": "GET", "path": "/pets", "operationId": "listPets"},
    "authenticationRequirements": ["apiKey"],
    "risk": "READ_ONLY"
  }]
}
```

The contract is a proposal: this endpoint does not approve endpoint selection, persist developer
edits, generate source code, or execute API/MCP code. Those remain later generation stages.
