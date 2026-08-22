ALTER TABLE mcp_server
    ADD COLUMN search_embedding vector(384),
    ADD COLUMN search_embedding_model VARCHAR(255);

CREATE INDEX idx_mcp_server_search_embedding_hnsw
    ON mcp_server USING hnsw (search_embedding vector_cosine_ops);
