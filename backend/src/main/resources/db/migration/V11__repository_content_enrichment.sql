CREATE TABLE repository_enrichment_artifact (
    id UUID PRIMARY KEY,
    server_id UUID NOT NULL REFERENCES mcp_server(id) ON DELETE CASCADE,
    artifact_type VARCHAR(32) NOT NULL CHECK (artifact_type IN ('README', 'STATIC_TOOL_METADATA')),
    source_path TEXT,
    source_url TEXT NOT NULL,
    source_revision VARCHAR(128),
    media_type VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    fetched_at TIMESTAMPTZ NOT NULL,
    UNIQUE (server_id, artifact_type, source_url)
);

CREATE INDEX idx_repository_enrichment_artifact_server_id
    ON repository_enrichment_artifact(server_id);

CREATE TABLE repository_enrichment_tool (
    id UUID PRIMARY KEY,
    artifact_id UUID NOT NULL REFERENCES repository_enrichment_artifact(id) ON DELETE CASCADE,
    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    input_schema JSONB,
    UNIQUE (artifact_id, ordinal)
);

CREATE INDEX idx_repository_enrichment_tool_artifact_id
    ON repository_enrichment_tool(artifact_id);
