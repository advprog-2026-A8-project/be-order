CREATE TABLE IF NOT EXISTS rating_sync_tasks (
    id BIGSERIAL PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    user_id VARCHAR(255) NOT NULL,
    jastiper_id VARCHAR(255) NOT NULL,
    product_id VARCHAR(255) NOT NULL,
    jastiper_rating INTEGER NOT NULL,
    product_rating INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rating_sync_tasks_status_next_retry
    ON rating_sync_tasks (status, next_retry_at);

