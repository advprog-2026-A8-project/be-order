CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    jastiper_id VARCHAR(255),
    jumlah INTEGER NOT NULL,
    alamat_pengiriman VARCHAR(255) NOT NULL,
    total_amount DOUBLE PRECISION NOT NULL,
    jastiper_rating INTEGER,
    product_rating INTEGER,
    rating_submitted BOOLEAN,
    status VARCHAR(50),
    CONSTRAINT chk_orders_jumlah_positive CHECK (jumlah > 0),
    CONSTRAINT chk_orders_total_amount_positive CHECK (total_amount > 0)
);

CREATE TABLE IF NOT EXISTS order_idempotency (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders (user_id);
CREATE INDEX IF NOT EXISTS idx_orders_jastiper_id ON orders (jastiper_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);
