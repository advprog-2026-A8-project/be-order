package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.CheckoutAuditTaskStatus;
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
@Table(name = "checkout_audit_tasks")
public class CheckoutAuditTask extends AbstractRetryableTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 1500)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CheckoutAuditTaskStatus status;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = CheckoutAuditTaskStatus.PENDING;
        }
        initializeRetryableFields();
    }

    @PreUpdate
    void onUpdate() {
        touchRetryableFields();
    }
}
