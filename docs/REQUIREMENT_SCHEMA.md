# Structured requirement schema

MCP Compass requirement analyzers return a versioned StructuredRequirement alongside the original
text and deterministic search keywords. The initial schema version is 1.0.

## Version 1.0

| Field | Meaning |
| --- | --- |
| schemaVersion | Required schema identifier. Version 1 uses 1.0. |
| domain | Broad problem area, such as source-control; an empty value means not identified. |
| service | Named service, such as github; an empty value means not identified. |
| requiredCapabilities | Canonical capabilities that a matching server must cover. |
| forbiddenCapabilities | Capabilities that make a candidate unsuitable. |
| constraints | Hard conditions expressed as a name, operator, and value. |

Capabilities must be non-blank, unique within each list, and cannot appear in both the required and
forbidden lists. Constraints support EQUALS, NOT_EQUALS, CONTAINS, AT_LEAST, and AT_MOST.

Example:

~~~json
{
  "schemaVersion": "1.0",
  "domain": "source-control",
  "service": "github",
  "requiredCapabilities": [
    "github.issue.read",
    "github.pull-request.create"
  ],
  "forbiddenCapabilities": [
    "github.repository.delete"
  ],
  "constraints": [
    {
      "name": "authentication",
      "operator": "EQUALS",
      "value": "oauth2"
    }
  ]
}
~~~

The heuristic analyzer leaves the structured fields empty because it only extracts lexical search
keywords. A later analyzer can populate the same result contract without changing search callers.
