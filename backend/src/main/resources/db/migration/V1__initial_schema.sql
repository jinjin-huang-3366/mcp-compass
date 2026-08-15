CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE mcp_server (
    id UUID PRIMARY KEY,
    registry_name VARCHAR(512) NOT NULL UNIQUE,
    title VARCHAR(512),
    description TEXT,
    version VARCHAR(128),
    status VARCHAR(64),
    raw_metadata TEXT,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mcp_server_title_trgm ON mcp_server USING gin (lower(title) gin_trgm_ops);
CREATE INDEX idx_mcp_server_description_trgm ON mcp_server USING gin (lower(description) gin_trgm_ops);
CREATE INDEX idx_mcp_server_registry_name_trgm ON mcp_server USING gin (lower(registry_name) gin_trgm_ops);
CREATE INDEX idx_mcp_server_status ON mcp_server(status);

CREATE TABLE mcp_tool (
    id UUID PRIMARY KEY,
    server_id UUID NOT NULL REFERENCES mcp_server(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    input_schema TEXT,
    risk_level VARCHAR(32),
    embedding vector,
    UNIQUE(server_id, name)
);

CREATE TABLE capability (
    id UUID PRIMARY KEY,
    canonical_name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    embedding vector
);

CREATE TABLE mcp_tool_capability (
    tool_id UUID NOT NULL REFERENCES mcp_tool(id) ON DELETE CASCADE,
    capability_id UUID NOT NULL REFERENCES capability(id) ON DELETE CASCADE,
    confidence DOUBLE PRECISION NOT NULL,
    source VARCHAR(64) NOT NULL,
    PRIMARY KEY(tool_id, capability_id)
);

CREATE TABLE repo_metrics (
    server_id UUID PRIMARY KEY REFERENCES mcp_server(id) ON DELETE CASCADE,
    repository_url TEXT,
    stars BIGINT,
    forks BIGINT,
    archived BOOLEAN,
    license VARCHAR(128),
    last_commit_at TIMESTAMPTZ,
    last_release_at TIMESTAMPTZ,
    collected_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE validation_result (
    id UUID PRIMARY KEY,
    server_id UUID REFERENCES mcp_server(id) ON DELETE CASCADE,
    version VARCHAR(128),
    build_status VARCHAR(32),
    connection_status VARCHAR(32),
    tools_list_status VARCHAR(32),
    security_status VARCHAR(32),
    details TEXT,
    validated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE registry_sync_state (
    source VARCHAR(128) PRIMARY KEY,
    next_cursor TEXT,
    updated_since TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    last_error TEXT
);
