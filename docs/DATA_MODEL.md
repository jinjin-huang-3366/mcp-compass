# Data model

## V0.1 tables

### `mcp_server`
Normalized latest server metadata for search/recommendation. Keeps `raw_metadata` for forward compatibility while Registry schemas are evolving.

### `mcp_tool`
Normalized tool metadata associated with a server. Registry ingestion populates rows when a server
declares tools directly or through publisher-provided extension metadata. Standard Registry entries
that do not publish tools remain valid and retain their raw metadata for later enrichment.

### `capability`
Canonical capability concepts such as `github.issue.read` or `trading.order.cancel`.

### `mcp_tool_capability`
Many-to-many mapping with confidence and source of normalization.

### `mcp_server_capability`
Many-to-many mapping from a server to its normalized capability set. It contains explicit
server-declared capabilities plus capabilities aggregated from declared tools, allowing later
coverage ranking without duplicating tool traversal in the search path.

## Capability ingestion
Capability names are normalized deterministically to lowercase dotted identifiers. Explicit
server/tool capability declarations use confidence `1.0`; a tool without explicit declarations
contributes a capability derived from the stable Registry server-name suffix plus its tool name at
confidence `0.7`. Prose descriptions are not treated as semantic capability claims. Each successful
Registry upsert replaces that server's tool and capability mappings so repeated syncs are idempotent
and removed upstream metadata does not remain searchable.

### `repo_metrics`
Future GitHub maintenance/quality enrichment.

### `validation_result`
Future build/protocol/security validation history.

### `registry_sync_state`
Checkpoint and last-success metadata for incremental ingestion. A partial run retains its next cursor and
the prior `updated_since` boundary; completing pagination clears the cursor and advances the boundary to
the run start time so updates arriving during the run are included by the next sync.

## Identity
Use Registry `name` as the stable public identity and an internal UUID primary key. Version history can be added separately when product requirements need it; V0.1 stores the latest seen version.

## Search
V0.1 uses trigram-friendly normalized textual fields. pgvector is enabled in infrastructure for future embeddings but vector dimensions/model choice are deliberately deferred until an evaluation baseline exists.
