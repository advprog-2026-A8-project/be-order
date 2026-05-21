ALTER TABLE order_idempotency
    ADD CONSTRAINT fk_order_idempotency_order_id
    FOREIGN KEY (order_id) REFERENCES orders (id);
