CREATE TABLE mcp_server_search_document (
    server_id UUID PRIMARY KEY REFERENCES mcp_server(id) ON DELETE CASCADE,
    document_version INTEGER NOT NULL CHECK (document_version > 0),
    content TEXT NOT NULL,
    content_sha256 CHAR(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    built_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mcp_server_search_document_content_tsv
    ON mcp_server_search_document USING gin (to_tsvector('simple', content));

CREATE INDEX idx_mcp_server_search_document_content_trgm
    ON mcp_server_search_document USING gin (lower(content) gin_trgm_ops);
