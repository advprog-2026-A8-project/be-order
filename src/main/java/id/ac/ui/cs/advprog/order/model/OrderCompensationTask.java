package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.CompensationTaskStatus;
import id.ac.ui.cs.advprog.order.enums.CompensationTaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "order_compensation_tasks")
public class OrderCompensationTask extends AbstractRetryableTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String taskKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private CompensationTaskType taskType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompensationTaskStatus status;

    @Column(nullable = false)
    private String orderId;

    @Column
    private String userId;

    @Column
    private String productId;

    @Column
    private Integer quantity;

    @Column
    private Double amount;

    @Column
    private String voucherCode;

    @Column
    private String idempotencyKey;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = CompensationTaskStatus.PENDING;
        }
        initializeRetryableFields();
    }

    @PreUpdate
    void onUpdate() {
        touchRetryableFields();
    }
}

