ALTER TABLE mcp_server
    ADD COLUMN tool_schema_status VARCHAR(32) NOT NULL DEFAULT 'NOT_DISCOVERABLE',
    ADD COLUMN tool_schema_inspected_at TIMESTAMPTZ;

ALTER TABLE mcp_tool
    ADD COLUMN schema_source VARCHAR(64);
