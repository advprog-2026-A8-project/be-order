package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.RatingSyncStatus;
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
@Table(name = "rating_sync_tasks")
public class RatingSyncTask extends AbstractRetryableTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String orderId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String jastiperId;

    @Column(nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer jastiperRating;

    @Column(nullable = false)
    private Integer productRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RatingSyncStatus status;

    @PrePersist
    void onCreate() {
        if (status == null) {
            status = RatingSyncStatus.PENDING;
        }
        initializeRetryableFields();
    }

    public void touch() {
        touchRetryableFields();
    }

    @PreUpdate
    void onUpdate() {
        touch();
    }
}
