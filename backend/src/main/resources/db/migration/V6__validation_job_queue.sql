CREATE TABLE validation_job (
    id UUID PRIMARY KEY,
    status VARCHAR(32) NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    generator_version VARCHAR(32) NOT NULL,
    contract_version VARCHAR(32) NOT NULL,
    project_manifest JSONB NOT NULL,
    queued_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_validation_job_queue
    ON validation_job(status, queued_at, id);
