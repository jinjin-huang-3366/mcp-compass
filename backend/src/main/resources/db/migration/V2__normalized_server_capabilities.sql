CREATE TABLE mcp_server_capability (
    server_id UUID NOT NULL REFERENCES mcp_server(id) ON DELETE CASCADE,
    capability_id UUID NOT NULL REFERENCES capability(id) ON DELETE CASCADE,
    confidence DOUBLE PRECISION NOT NULL CHECK (confidence >= 0.0 AND confidence <= 1.0),
    source VARCHAR(64) NOT NULL,
    PRIMARY KEY(server_id, capability_id)
);

CREATE INDEX idx_mcp_server_capability_capability_id
    ON mcp_server_capability(capability_id);
CREATE INDEX idx_mcp_tool_server_id ON mcp_tool(server_id);
CREATE INDEX idx_mcp_tool_capability_capability_id
    ON mcp_tool_capability(capability_id);
