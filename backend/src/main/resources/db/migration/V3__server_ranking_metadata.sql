ALTER TABLE mcp_server
    ADD COLUMN official_registry_provenance BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN repository_url TEXT,
    ADD COLUMN package_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN remote_count INTEGER NOT NULL DEFAULT 0;
