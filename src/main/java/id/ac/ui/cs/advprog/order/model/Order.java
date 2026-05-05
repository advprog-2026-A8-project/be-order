package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String productId;

    @NotBlank
    @Column(nullable = false)
    private String userId;

    private String jastiperId;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Integer jumlah;

    @NotBlank
    @Column(nullable = false)
    private String alamatPengiriman;

    @NotNull
    @Positive
    @Column(nullable = false)
    private Double totalAmount;
    private Integer jastiperRating;
    private Integer productRating;
    private Boolean ratingSubmitted = false;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;
}
