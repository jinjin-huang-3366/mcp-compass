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
      "reasons": ["title contains github", "description matches issues"]
    }
  ]
}
```

## MCP detail

`GET /mcp/{id}`

Returns normalized server detail and basic metadata.

## Local Registry sync

Only with Spring `local` profile:

`POST /dev/registry/sync?maxPages=1`

This endpoint is deliberately not intended for production exposure.
