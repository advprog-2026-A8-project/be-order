package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.data.domain.Persistable;

@Data
@Entity
@Table(name = "orders")
public class Order implements Persistable<String> {
    private static final String MESSAGE_PRODUCT_ID_REQUIRED = "Product ID wajib diisi";
    private static final String MESSAGE_USER_ID_REQUIRED = "User ID wajib diisi";
    private static final String MESSAGE_QUANTITY_REQUIRED = "Jumlah wajib diisi";
    private static final String MESSAGE_QUANTITY_POSITIVE = "Jumlah harus lebih dari 0";
    private static final String MESSAGE_ADDRESS_REQUIRED = "Alamat pengiriman wajib diisi";
    private static final String MESSAGE_TOTAL_AMOUNT_REQUIRED = "Total amount wajib diisi";
    private static final String MESSAGE_TOTAL_AMOUNT_POSITIVE = "Total amount harus lebih dari 0";

    @Id
    private String id;

    @Transient
    private boolean newEntity = true;

    @NotBlank(message = MESSAGE_PRODUCT_ID_REQUIRED)
    @Column(nullable = false)
    private String productId;

    @NotBlank(message = MESSAGE_USER_ID_REQUIRED)
    @Column(nullable = false)
    private String userId;

    private String jastiperId;

    @NotNull(message = MESSAGE_QUANTITY_REQUIRED)
    @Positive(message = MESSAGE_QUANTITY_POSITIVE)
    @Column(nullable = false)
    private Integer jumlah;

    @NotBlank(message = MESSAGE_ADDRESS_REQUIRED)
    @Column(nullable = false)
    private String alamatPengiriman;
    private String voucherCode;
    private Boolean voucherApplied = false;

    @NotNull(message = MESSAGE_TOTAL_AMOUNT_REQUIRED)
    @Positive(message = MESSAGE_TOTAL_AMOUNT_POSITIVE)
    @Column(nullable = false)
    private Double totalAmount;
    private Integer jastiperRating;
    private Integer productRating;
    private Boolean ratingSubmitted = false;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }
}
