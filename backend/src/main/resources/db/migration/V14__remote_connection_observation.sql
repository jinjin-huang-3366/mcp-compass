ALTER TABLE mcp_server
    ADD COLUMN remote_connection_state varchar(32) NOT NULL DEFAULT 'NOT_CHECKED',
    ADD COLUMN remote_connection_checked_at timestamptz,
    ADD COLUMN remote_connection_reason varchar(160);

CREATE INDEX mcp_server_remote_check_due_idx
    ON mcp_server (remote_connection_checked_at, registry_name)
    WHERE remote_count > 0 AND status <> 'deleted';
