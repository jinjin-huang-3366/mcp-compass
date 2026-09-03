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

The heuristic analyzer extracts lexical keywords plus deterministic negative intent and hard
constraints for supported patterns. A later analyzer can populate the same result contract without
changing search callers.

## Runtime LLM analyzer

The OpenAI-backed analyzer is disabled by default. When enabled, it sends the user's requirement to
the configured OpenAI Responses API and requests strict JSON-schema output matching version 1.0.
Deterministic keywords are still extracted locally. When the model returns no forbidden
capabilities, deterministic forbidden capabilities are retained and override a semantically
conflicting required capability. This keeps the safer interpretation when the model completely
omits explicit negative intent without broadening a model result that already captured it. If the
provider request or structured response fails validation, MCP Compass logs the failure type without
the requirement text and returns the heuristic analysis instead.

Enable it locally with environment variables before starting the backend:

~~~powershell
$env:MCP_COMPASS_LLM_ENABLED = "true"
$env:OPENAI_API_KEY = "<your OpenAI API key>"
$env:OPENAI_MODEL = "gpt-5.6-luna"
.\mvnw.cmd -pl backend spring-boot:run
~~~

OPENAI_BASE_URL can point to a different OpenAI Responses API endpoint. Never commit the API key.
Enabling the analyzer sends search requirements to that endpoint and consumes model tokens. The task
workflow and automated tests do not enable the analyzer or use a real API key; client tests use a
local fake HTTP server.

## Golden requirement corpus

`backend/src/test/resources/fixtures/requirements/structured-requirement-golden-v1.json` contains
the versioned, reviewable expectations for representative developer requirements. The golden test
passes each case through the OpenAI client and requirement analyzer using a local fake HTTP server,
so CI checks request wiring, strict-response parsing, schema validation, and analyzer integration
without making provider calls or requiring an API key.
