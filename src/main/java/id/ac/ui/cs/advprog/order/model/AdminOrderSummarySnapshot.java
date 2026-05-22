package id.ac.ui.cs.advprog.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "admin_order_summary_snapshot")
public class AdminOrderSummarySnapshot {
    @Id
    private Integer id;

    @Column(nullable = false)
    private Long totalOrders;

    @Column(nullable = false)
    private Long activeOrders;

    @Column(nullable = false)
    private Long completedOrders;

    @Column(nullable = false)
    private Long cancelledOrders;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
        if (id == null) {
            id = 1;
        }
    }
}
