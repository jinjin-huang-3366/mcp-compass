CREATE INDEX idx_mcp_server_search_document_tsv
    ON mcp_server USING gin (
        to_tsvector(
            'simple',
            coalesce(registry_name, '') || ' '
                || coalesce(title, '') || ' '
                || coalesce(description, '')
        )
    );

CREATE INDEX idx_mcp_server_search_document_trgm
    ON mcp_server USING gin (
        lower(
            coalesce(registry_name, '') || ' '
                || coalesce(title, '') || ' '
                || coalesce(description, '')
        ) gin_trgm_ops
    );

CREATE INDEX idx_mcp_tool_search_document_tsv
    ON mcp_tool USING gin (
        to_tsvector(
            'simple',
            coalesce(name, '') || ' ' || coalesce(description, '')
        )
    );

CREATE INDEX idx_mcp_tool_search_document_trgm
    ON mcp_tool USING gin (
        lower(coalesce(name, '') || ' ' || coalesce(description, '')) gin_trgm_ops
    );

CREATE INDEX idx_capability_canonical_name_tsv
    ON capability USING gin (to_tsvector('simple', canonical_name));

CREATE INDEX idx_capability_canonical_name_trgm
    ON capability USING gin (lower(canonical_name) gin_trgm_ops);
