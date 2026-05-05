package id.ac.ui.cs.advprog.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "order_idempotency")
@NoArgsConstructor
@AllArgsConstructor
public class OrderIdempotency {
    private static final String COLUMN_IDEMPOTENCY_KEY = "idempotency_key";
    private static final String COLUMN_ORDER_ID = "order_id";

    @Id
    @Column(name = COLUMN_IDEMPOTENCY_KEY, nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = COLUMN_ORDER_ID, nullable = false, unique = true)
    private String orderId;
}
