package id.ac.ui.cs.advprog.order.service.rating;

import id.ac.ui.cs.advprog.order.enums.RatingSyncStatus;
import id.ac.ui.cs.advprog.order.model.RatingSyncTask;
import id.ac.ui.cs.advprog.order.repository.RatingSyncTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class RatingSyncWorker {
    private static final Set<RatingSyncStatus> READY_STATUSES = Set.of(RatingSyncStatus.PENDING, RatingSyncStatus.RETRY);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 900;

    private final RatingSyncTaskRepository ratingSyncTaskRepository;
    private final ProfileGateway profileGateway;

    @Value("${order.rating-sync.batch-size:20}")
    private int batchSize;

    @Value("${order.rating-sync.retry.max-attempts:8}")
    private int maxRetryAttempts;

    @Value("${order.rating-sync.retry.base-delay-ms:1000}")
    private long baseRetryDelayMs;

    @Scheduled(fixedDelayString = "${order.rating-sync.worker-delay-ms:3000}")
    @Transactional
    public void processPendingTasks() {
        int effectiveBatchSize = Math.max(1, batchSize);
        LocalDateTime now = LocalDateTime.now();
        List<RatingSyncTask> tasks = ratingSyncTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                READY_STATUSES,
                now,
                PageRequest.of(0, effectiveBatchSize)
        );

        for (RatingSyncTask task : tasks) {
            processTask(task, now);
        }
    }

    private void processTask(RatingSyncTask task, LocalDateTime now) {
        try {
            profileGateway.submitRating(
                    task.getOrderId(),
                    task.getUserId(),
                    task.getJastiperId(),
                    task.getProductId(),
                    task.getJastiperRating(),
                    task.getProductRating()
            );
            task.setStatus(RatingSyncStatus.SUCCEEDED);
            task.setLastError(null);
            task.setNextRetryAt(now);
            ratingSyncTaskRepository.save(task);
        } catch (RuntimeException ex) {
            markFailure(task, now, ex);
        }
    }

    private void markFailure(RatingSyncTask task, LocalDateTime now, RuntimeException ex) {
        int nextAttempt = task.getAttemptCount() + 1;
        task.setAttemptCount(nextAttempt);
        task.setLastError(compactErrorMessage(ex));
        if (nextAttempt >= Math.max(1, maxRetryAttempts)) {
            task.setStatus(RatingSyncStatus.FAILED);
            task.setNextRetryAt(now);
            log.error("rating_sync_failed_permanently orderId={} attempts={}", task.getOrderId(), nextAttempt, ex);
        } else {
            task.setStatus(RatingSyncStatus.RETRY);
            task.setNextRetryAt(now.plusNanos(computeBackoffMillis(nextAttempt) * 1_000_000));
            log.warn("rating_sync_retry_scheduled orderId={} attempt={} nextRetryAt={}",
                    task.getOrderId(), nextAttempt, task.getNextRetryAt(), ex);
        }
        ratingSyncTaskRepository.save(task);
    }

    private long computeBackoffMillis(int attempt) {
        long safeBase = Math.max(100L, baseRetryDelayMs);
        long multiplier = 1L << Math.min(6, Math.max(0, attempt - 1));
        return safeBase * multiplier;
    }

    private String compactErrorMessage(Throwable throwable) {
        String message = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        if (message.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}

