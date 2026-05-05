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
    @Id
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;
}
