ALTER TABLE registry_sync_state
    ADD COLUMN sync_started_at TIMESTAMPTZ;

INSERT INTO registry_sync_state (source)
VALUES ('official-mcp-registry')
ON CONFLICT (source) DO NOTHING;
