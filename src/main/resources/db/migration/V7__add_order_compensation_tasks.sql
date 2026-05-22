CREATE TABLE IF NOT EXISTS order_compensation_tasks (
    id BIGSERIAL PRIMARY KEY,
    task_key VARCHAR(255) NOT NULL UNIQUE,
    task_type VARCHAR(40) NOT NULL,
    status VARCHAR(32) NOT NULL,
    order_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255),
    product_id VARCHAR(255),
    quantity INTEGER,
    amount DOUBLE PRECISION,
    voucher_code VARCHAR(255),
    idempotency_key VARCHAR(255),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP NOT NULL,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_order_comp_tasks_status_next_retry
    ON order_compensation_tasks (status, next_retry_at);
