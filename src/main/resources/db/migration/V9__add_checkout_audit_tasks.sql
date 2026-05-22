CREATE TABLE IF NOT EXISTS checkout_audit_tasks (
    id BIGSERIAL PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    payload VARCHAR(1500) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_checkout_audit_tasks_status_next_retry
    ON checkout_audit_tasks (status, next_retry_at);
