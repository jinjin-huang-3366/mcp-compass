ALTER TABLE validation_job
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ,
    ADD COLUMN failure_reason VARCHAR(2000);
