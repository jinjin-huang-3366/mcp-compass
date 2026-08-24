# Data model

## V0.1 tables

### `mcp_server`
Normalized latest server metadata for search/recommendation. Keeps `raw_metadata` for forward compatibility while Registry schemas are evolving. Also records official Registry provenance, a declared source repository, and counts of usable package and remote install options as bounded secondary ranking signals. Repository activity and deeper trust data remain future enrichment.

Optional vector retrieval adds a 384-dimensional `search_embedding` and its `search_embedding_model`. The model
identifier prevents comparisons between incompatible embedding spaces. A cosine HNSW index excludes null vectors,
so servers remain lexically searchable while embeddings are disabled, unavailable, or still being refreshed.

### `mcp_tool`
Normalized tool metadata associated with a server. Registry ingestion populates rows when a server
declares tools directly or through publisher-provided extension metadata. Standard Registry entries
that do not publish tools remain valid and retain their raw metadata for later enrichment. Declared
input schemas are accepted only when they are bounded JSON objects and retain their untrusted metadata
source. Each server records a tool-schema inspection status (`DISCOVERED`, `PARTIAL`, `INVALID`, or
`NOT_DISCOVERABLE`) and timestamp.

Schema inspection is static: it never installs or starts packages and never calls a server tool. A
`NOT_DISCOVERABLE` result means no safe schema was present in ingested metadata; protocol inspection
of remote or packaged servers remains deferred until an allow-listed or sandboxed execution boundary
exists.

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

Coverage comparison applies the same deterministic normalization to required and persisted
capabilities. It treats punctuation variants uniformly, singularizes common plural resource names,
and rotates action-first tool names (for example `github.create_pull_requests`) into the same
comparison key as contract-style capabilities (`github.pull-request.create`). This normalization is
bounded and lexical; it does not infer capabilities from prose or call an LLM at scoring time.

### `repo_metrics`
Latest GitHub repository enrichment for a Registry server: repository URL, last push and release timestamps,
archived status, SPDX license identifier, and collection time. Registry sync refreshes these values only when
GitHub enrichment is enabled; a GitHub failure does not discard Registry metadata or make search depend on GitHub.
Search bulk-loads these persisted values to calculate a deterministic trust/quality score alongside
Registry provenance, installability, and declared-schema status. Missing metrics receive no inferred credit.

### `validation_result`
Future build/protocol/security validation history.

### `validation_job`
Durable FIFO-oriented validation work. A job begins in `QUEUED` state and stores the generated project name,
generator/contract versions, submission time, and the exact deterministic TypeScript project manifest as JSONB. The
`status, queued_at, id` index supports stable worker claiming later. The manifest is an inert snapshot; the main
backend does not materialize or execute it, and VAL-01 adds no job consumer.

### `registry_sync_state`
Checkpoint and last-success metadata for incremental ingestion. A partial run retains its next cursor and
the prior `updated_since` boundary; completing pagination clears the cursor and advances the boundary to
the run start time so updates arriving during the run are included by the next sync.

## Identity
Use Registry `name` as the stable public identity and an internal UUID primary key. Version history can be added separately when product requirements need it; V0.1 stores the latest seen version.

## Search
V0.1 uses trigram-friendly normalized textual fields by default. After the versioned lexical baseline was checked in,
SRCH-06 added opt-in hybrid retrieval using 384-dimensional OpenAI embeddings and pgvector cosine distance. Registry
sync writes embeddings after persisted metadata commits; existing rows gain vectors on their next sync. The embedding
model is configurable, but it must support the fixed 384-dimension database contract.
