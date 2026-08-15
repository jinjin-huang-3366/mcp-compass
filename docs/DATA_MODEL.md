# Data model

## V0.1 tables

### `mcp_server`
Normalized latest server metadata for search/recommendation. Keeps `raw_metadata` for forward compatibility while Registry schemas are evolving.

### `mcp_tool`
Prepared for later tool inspection. Tool rows are not yet populated by V0.1 Registry ingestion.

### `capability`
Canonical capability concepts such as `github.issue.read` or `trading.order.cancel`.

### `mcp_tool_capability`
Many-to-many mapping with confidence and source of normalization.

### `repo_metrics`
Future GitHub maintenance/quality enrichment.

### `validation_result`
Future build/protocol/security validation history.

### `registry_sync_state`
Checkpoint and last-success metadata for incremental ingestion.

## Identity
Use Registry `name` as the stable public identity and an internal UUID primary key. Version history can be added separately when product requirements need it; V0.1 stores the latest seen version.

## Search
V0.1 uses trigram-friendly normalized textual fields. pgvector is enabled in infrastructure for future embeddings but vector dimensions/model choice are deliberately deferred until an evaluation baseline exists.
