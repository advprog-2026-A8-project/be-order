package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String productId;
    private String userId;

    private Integer jumlah;
    private String alamatPengiriman;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;
}