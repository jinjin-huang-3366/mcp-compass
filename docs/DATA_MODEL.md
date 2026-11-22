# Data model

## V0.1 tables

### `mcp_server`
Normalized latest server metadata for search/recommendation. Keeps `raw_metadata` for forward compatibility while Registry schemas are evolving. Also records official Registry provenance, a declared source repository, and counts of usable package and remote install options as bounded secondary ranking signals. Repository activity and deeper trust data remain future enrichment.

Optional vector retrieval adds a 384-dimensional `search_embedding` and its `search_embedding_model`. The model
identifier prevents comparisons between incompatible embedding spaces. A cosine HNSW index excludes null vectors,
so servers remain lexically searchable while embeddings are disabled, unavailable, or still being refreshed.

### `mcp_server_search_document`

The versioned, deterministic retrieval document for a server. It combines the Registry service/domain text, declared
tools and capabilities, repository URL, and bounded REL-04 repository enrichment (README/static tool metadata). The
document hash prevents unnecessary rewrites; sync refreshes affected documents after enrichment and the local-only
backfill endpoint repairs documents for existing catalog rows. Lexical retrieval uses this document when present and
retains the previous normalized server/tool/capability path for rows that have not yet been backfilled. Embeddings use
the same document, so lexical and pgvector retrieval share one evidence surface.

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

### `repository_enrichment_artifact` and `repository_enrichment_tool`
Bounded README text and tool metadata from a small allow-list of static JSON files in a declared GitHub repository
are stored as untrusted enrichment. Every artifact records its API source URL, repository blob revision, SHA-256
content hash, media type, and fetch time; replacement is transactional so a repeated refresh is idempotent and files
removed upstream do not remain searchable. Parsed tools retain their parent artifact as provenance. Ingestion reads
GitHub's contents API only, enforces response/content/tool/schema bounds, and never clones repositories, downloads or
installs packages, runs lifecycle scripts, imports source, or starts an MCP server.

### `validation_result`
Future build/protocol/security validation history.

### `validation_job`
Durable FIFO-oriented validation work. A job begins in `QUEUED` state and stores the generated project name,
generator/contract versions, submission time, and the exact deterministic TypeScript project manifest as JSONB. The
`status, queued_at, id` index supports atomic `FOR UPDATE SKIP LOCKED` worker claiming. The separate validation worker
moves a job through `RUNNING` to `EXECUTED` only when the isolated MCP Inspector process completes initialization and
returns a valid `tools/list` response, or to `FAILED` with a bounded diagnostic when the probe fails or times out. A
successful job stores the structured Inspector response, validator version, and method in `protocol_result`; start and
finish timestamps provide lifecycle evidence. It also stores a versioned `security_report` with conservative
per-tool risk classifications, inventory discrepancies, effective sandbox controls, and explicit limitations.
Unsupported, missing, or undeclared tool classifications default to `DESTRUCTIVE`. The main backend never
materializes or executes the manifest.

### `registry_sync_state`
Checkpoint and last-success metadata for incremental ingestion. A partial run retains its next cursor and
the prior `updated_since` boundary; completing pagination clears the cursor and advances the boundary to
the run start time so updates arriving during the run are included by the next sync.

### Remote connection observation

`mcp_server` stores the latest `remote_connection_state`, `remote_connection_checked_at`, and a bounded
`remote_connection_reason`. `NOT_CHECKED` is the default and also describes skipped endpoints. The
timestamp is the time of the observation or skip, not a claim that the remote remains in that state.
Registry sync keeps an observation until its 24-hour refresh is due; any changed Registry payload or
a server that loses all remote install options resets to `NOT_CHECKED`. Search and detail read only
these persisted fields.

The opt-in background checker selects one Registry-declared `streamable-http` URL through a source
interface. It sends only JSON-RPC `initialize`; it never calls `tools/call`, follows redirects, sends
credentials, or executes a package. Each run handles at most ten due servers, spaces requests by at
least one second, and limits connection/read time and response bytes. Hosts require an exact operator
allowlist. `MCP_COMPASS_REMOTE_CHECK_ENABLED=true` turns on the hourly scheduler;
`MCP_COMPASS_REMOTE_CHECK_ALLOWED_HOSTS` is a comma-separated host list, and
`MCP_COMPASS_REMOTE_CHECK_OPT_OUT_NAMES` excludes comma-separated Registry names. Checks are disabled
by default. A 401 or 403 means `AUTH_REQUIRED`, never `UNREACHABLE`; unsupported transports and
unallowlisted URLs stay `NOT_CHECKED`. A successful initialize proves only that the endpoint responded
to that handshake at that time, not tool safety or future availability.

## Identity
Use Registry `name` as the stable public identity and an internal UUID primary key. Version history can be added separately when product requirements need it; V0.1 stores the latest seen version.

## Search
V0.1 uses trigram-friendly normalized textual fields by default. After the versioned lexical baseline was checked in,
SRCH-06 added opt-in hybrid retrieval using 384-dimensional OpenAI embeddings and pgvector cosine distance. REL-05
builds the same versioned document for both retrieval modes after persisted metadata and enrichment; existing rows can
be repaired with a bounded local backfill. The embedding
model is configurable, but it must support the fixed 384-dimension database contract.
