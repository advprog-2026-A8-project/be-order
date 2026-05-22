package id.ac.ui.cs.advprog.order.model;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
public abstract class AbstractRetryableTask {
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

    protected void initializeRetryableFields() {
        LocalDateTime now = LocalDateTime.now();
        if (attemptCount == null) {
            attemptCount = 0;
        }
        if (nextRetryAt == null) {
            nextRetryAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    protected void touchRetryableFields() {
        updatedAt = LocalDateTime.now();
    }
}
