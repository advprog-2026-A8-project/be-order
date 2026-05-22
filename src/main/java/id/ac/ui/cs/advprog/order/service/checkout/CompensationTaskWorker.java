package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CompensationTaskStatus;
import id.ac.ui.cs.advprog.order.enums.CompensationTaskType;
import id.ac.ui.cs.advprog.order.model.OrderCompensationTask;
import id.ac.ui.cs.advprog.order.repository.OrderCompensationTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationTaskWorker {
    private static final Set<CompensationTaskStatus> READY_STATUSES =
            Set.of(CompensationTaskStatus.PENDING, CompensationTaskStatus.RETRY);
    private static final int MAX_ERROR_MESSAGE_LENGTH = 900;

    private final OrderCompensationTaskRepository orderCompensationTaskRepository;
    private final WalletGateway walletGateway;
    private final InventoryGateway inventoryGateway;
    private final VoucherGateway voucherGateway;

    @Value("${order.compensation.batch-size:20}")
    private int batchSize;

    @Value("${order.compensation.retry.max-attempts:8}")
    private int maxRetryAttempts;

    @Value("${order.compensation.retry.base-delay-ms:1000}")
    private long baseRetryDelayMs;

    @Scheduled(fixedDelayString = "${order.compensation.worker-delay-ms:3000}")
    @Transactional
    public void processPendingTasks() {
        int effectiveBatchSize = Math.max(1, batchSize);
        LocalDateTime now = LocalDateTime.now();
        List<OrderCompensationTask> tasks =
                orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                        READY_STATUSES,
                        now,
                        PageRequest.of(0, effectiveBatchSize)
                );
        for (OrderCompensationTask task : tasks) {
            processTask(task, now);
        }
    }

    private void processTask(OrderCompensationTask task, LocalDateTime now) {
        try {
            executeTask(task);
            task.setStatus(CompensationTaskStatus.SUCCEEDED);
            task.setLastError(null);
            task.setNextRetryAt(now);
            orderCompensationTaskRepository.save(task);
        } catch (RuntimeException ex) {
            markFailure(task, now, ex);
        }
    }

    private void executeTask(OrderCompensationTask task) {
        CompensationTaskType taskType = task.getTaskType();
        switch (taskType) {
            case WALLET_REFUND -> walletGateway.refund(
                    task.getUserId(),
                    task.getOrderId(),
                    safeAmount(task),
                    safeIdempotencyKey(task)
            );
            case WALLET_DEBIT -> walletGateway.debit(
                    task.getUserId(),
                    task.getOrderId(),
                    safeAmount(task),
                    safeIdempotencyKey(task)
            );
            case INVENTORY_RESERVE -> inventoryGateway.reserveStock(
                    task.getProductId(),
                    safeQuantity(task)
            );
            case INVENTORY_RELEASE -> inventoryGateway.releaseStock(
                    task.getProductId(),
                    safeQuantity(task)
            );
            case VOUCHER_USE -> voucherGateway.useVoucher(task.getVoucherCode());
            case VOUCHER_RESTORE -> voucherGateway.restoreVoucher(
                    task.getVoucherCode(),
                    safeIdempotencyKey(task)
            );
            default -> throw new IllegalStateException("Compensation task type tidak dikenali: " + taskType);
        }
    }

    private void markFailure(OrderCompensationTask task, LocalDateTime now, RuntimeException ex) {
        int nextAttempt = task.getAttemptCount() + 1;
        task.setAttemptCount(nextAttempt);
        task.setLastError(compactErrorMessage(ex));
        if (nextAttempt >= Math.max(1, maxRetryAttempts)) {
            task.setStatus(CompensationTaskStatus.FAILED);
            task.setNextRetryAt(now);
            log.error("compensation_task_failed_permanently taskId={} taskType={} orderId={} attempts={}",
                    task.getId(), task.getTaskType(), task.getOrderId(), nextAttempt, ex);
        } else {
            task.setStatus(CompensationTaskStatus.RETRY);
            task.setNextRetryAt(now.plusNanos(computeBackoffMillis(nextAttempt) * 1_000_000));
            log.warn("compensation_task_retry_scheduled taskId={} taskType={} orderId={} attempt={} nextRetryAt={}",
                    task.getId(), task.getTaskType(), task.getOrderId(), nextAttempt, task.getNextRetryAt(), ex);
        }
        orderCompensationTaskRepository.save(task);
    }

    private long computeBackoffMillis(int attempt) {
        long safeBaseDelayMs = Math.max(100L, baseRetryDelayMs);
        long multiplier = 1L << Math.min(6, Math.max(0, attempt - 1));
        return safeBaseDelayMs * multiplier;
    }

    private String compactErrorMessage(Throwable throwable) {
        String rawMessage = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        if (rawMessage.length() <= MAX_ERROR_MESSAGE_LENGTH) {
            return rawMessage;
        }
        return rawMessage.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }

    private double safeAmount(OrderCompensationTask task) {
        if (task.getAmount() == null) {
            throw new IllegalStateException("Amount wajib diisi untuk task type " + task.getTaskType());
        }
        return task.getAmount();
    }

    private int safeQuantity(OrderCompensationTask task) {
        if (task.getQuantity() == null) {
            throw new IllegalStateException("Quantity wajib diisi untuk task type " + task.getTaskType());
        }
        return task.getQuantity();
    }

    private String safeIdempotencyKey(OrderCompensationTask task) {
        if (task.getIdempotencyKey() == null || task.getIdempotencyKey().isBlank()) {
            throw new IllegalStateException("Idempotency key wajib diisi untuk task type " + task.getTaskType());
        }
        return task.getIdempotencyKey();
    }
}
