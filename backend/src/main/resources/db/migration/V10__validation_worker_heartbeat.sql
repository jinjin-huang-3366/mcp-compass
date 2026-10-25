CREATE TABLE validation_worker_heartbeat (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    last_seen_at TIMESTAMPTZ NOT NULL
);
