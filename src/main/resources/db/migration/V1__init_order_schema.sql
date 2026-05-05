CREATE TABLE IF NOT EXISTS orders (
    id VARCHAR(255) PRIMARY KEY,
    product_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    jastiper_id VARCHAR(255),
    jumlah INTEGER NOT NULL CHECK (jumlah > 0),
    alamat_pengiriman VARCHAR(255) NOT NULL,
    total_amount DOUBLE PRECISION NOT NULL CHECK (total_amount > 0),
    jastiper_rating INTEGER,
    product_rating INTEGER,
    rating_submitted BOOLEAN,
    status VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS order_idempotency (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    order_id VARCHAR(255) NOT NULL UNIQUE
);
