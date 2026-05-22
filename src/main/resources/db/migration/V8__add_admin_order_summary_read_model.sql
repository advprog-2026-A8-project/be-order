CREATE TABLE IF NOT EXISTS admin_order_summary_snapshot (
    id INTEGER PRIMARY KEY,
    total_orders BIGINT NOT NULL,
    active_orders BIGINT NOT NULL,
    completed_orders BIGINT NOT NULL,
    cancelled_orders BIGINT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS admin_order_status_count (
    status VARCHAR(50) PRIMARY KEY,
    total BIGINT NOT NULL
);
