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

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "rating_sync_tasks")
public class RatingSyncTask {
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

    @Column(nullable = false)
    private Integer attemptCount;

    @Column(nullable = false)
    private LocalDateTime nextRetryAt;

    @Column(length = 1000)
    private String lastError;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = RatingSyncStatus.PENDING;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (nextRetryAt == null) {
            nextRetryAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    public void touch() {
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        touch();
    }
}
