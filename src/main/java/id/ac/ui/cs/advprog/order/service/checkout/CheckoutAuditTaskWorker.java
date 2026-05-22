package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CheckoutAuditTaskStatus;
import id.ac.ui.cs.advprog.order.model.CheckoutAuditTask;
import id.ac.ui.cs.advprog.order.repository.CheckoutAuditTaskRepository;
import id.ac.ui.cs.advprog.order.service.common.RetryTaskWorkerSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckoutAuditTaskWorker {
    private static final Set<CheckoutAuditTaskStatus> READY_STATUSES =
            Set.of(CheckoutAuditTaskStatus.PENDING, CheckoutAuditTaskStatus.RETRY);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 900;

    private final CheckoutAuditTaskRepository checkoutAuditTaskRepository;
    private final TransactionTemplate transactionTemplate;
    @Qualifier("checkoutAuditTaskExecutor")
    private final Executor checkoutAuditTaskExecutor;

    @Value("${order.audit.batch-size:40}")
    private int batchSize;

    @Value("${order.audit.retry.max-attempts:5}")
    private int maxRetryAttempts;

    @Value("${order.audit.retry.base-delay-ms:500}")
    private long baseRetryDelayMs;

    @Scheduled(fixedDelayString = "${order.audit.worker-delay-ms:2000}")
    public void processPendingTasks() {
        int effectiveBatchSize = Math.max(1, batchSize);
        LocalDateTime now = LocalDateTime.now();
        List<CheckoutAuditTask> tasks =
                checkoutAuditTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        READY_STATUSES,
                        now,
                        PageRequest.of(0, effectiveBatchSize)
                );
        List<CompletableFuture<Void>> futures = new ArrayList<>(tasks.size());
        for (CheckoutAuditTask task : tasks) {
            if (task.getId() == null) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(
                    () -> processTaskById(task.getId()),
                    checkoutAuditTaskExecutor
            ));
        }
        for (CompletableFuture<Void> future : futures) {
            try {
                future.join();
            } catch (RuntimeException ex) {
                log.warn("checkout_audit_future_failed reason={}", ex.getMessage(), ex);
            }
        }
    }

    private void processTaskById(Long taskId) {
        transactionTemplate.executeWithoutResult(status -> {
            CheckoutAuditTask task = checkoutAuditTaskRepository.findById(taskId).orElse(null);
            if (task == null) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            if (!READY_STATUSES.contains(task.getStatus())) {
                return;
            }
            if (task.getNextRetryAt() != null && task.getNextRetryAt().isAfter(now)) {
                return;
            }
            try {
                log.info("checkout_audit_event type={} payload={}", task.getEventType(), task.getPayload());
                task.setStatus(CheckoutAuditTaskStatus.SUCCEEDED);
                task.setLastError(null);
                task.setNextRetryAt(now);
                checkoutAuditTaskRepository.save(task);
            } catch (RuntimeException ex) {
                markFailure(task, now, ex);
            }
        });
    }

    private void markFailure(CheckoutAuditTask task, LocalDateTime now, RuntimeException ex) {
        int nextAttempt = task.getAttemptCount() + 1;
        task.setAttemptCount(nextAttempt);
        task.setLastError(RetryTaskWorkerSupport.compactErrorMessage(ex, MAX_ERROR_MESSAGE_LENGTH));
        if (nextAttempt >= Math.max(1, maxRetryAttempts)) {
            task.setStatus(CheckoutAuditTaskStatus.FAILED);
            task.setNextRetryAt(now);
            log.error("checkout_audit_failed_permanently id={} type={} attempts={}",
                    task.getId(), task.getEventType(), nextAttempt, ex);
        } else {
            task.setStatus(CheckoutAuditTaskStatus.RETRY);
            task.setNextRetryAt(now.plusNanos(
                    RetryTaskWorkerSupport.computeBackoffMillis(baseRetryDelayMs, nextAttempt) * 1_000_000
            ));
            log.warn("checkout_audit_retry_scheduled id={} type={} attempt={} nextRetryAt={}",
                    task.getId(), task.getEventType(), nextAttempt, task.getNextRetryAt(), ex);
        }
        checkoutAuditTaskRepository.save(task);
    }
}
