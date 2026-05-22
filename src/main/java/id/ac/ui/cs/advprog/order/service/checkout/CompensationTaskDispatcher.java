package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CompensationTaskStatus;
import id.ac.ui.cs.advprog.order.enums.CompensationTaskType;
import id.ac.ui.cs.advprog.order.model.OrderCompensationTask;
import id.ac.ui.cs.advprog.order.repository.OrderCompensationTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CompensationTaskDispatcher {
    private final OrderCompensationTaskRepository orderCompensationTaskRepository;

    @Transactional
    public void enqueueWalletRefund(String orderId, String userId, double amount, String idempotencyKey) {
        upsert(
                createTaskKey(CompensationTaskType.WALLET_REFUND, orderId, idempotencyKey),
                CompensationTaskType.WALLET_REFUND,
                orderId,
                userId,
                null,
                null,
                amount,
                null,
                idempotencyKey
        );
    }

    @Transactional
    public void enqueueWalletDebit(String orderId, String userId, double amount, String idempotencyKey) {
        upsert(
                createTaskKey(CompensationTaskType.WALLET_DEBIT, orderId, idempotencyKey),
                CompensationTaskType.WALLET_DEBIT,
                orderId,
                userId,
                null,
                null,
                amount,
                null,
                idempotencyKey
        );
    }

    @Transactional
    public void enqueueInventoryReserve(String orderId, String productId, int quantity) {
        upsert(
                createTaskKey(CompensationTaskType.INVENTORY_RESERVE, orderId, productId + ":" + quantity),
                CompensationTaskType.INVENTORY_RESERVE,
                orderId,
                null,
                productId,
                quantity,
                null,
                null,
                null
        );
    }

    @Transactional
    public void enqueueInventoryRelease(String orderId, String productId, int quantity) {
        upsert(
                createTaskKey(CompensationTaskType.INVENTORY_RELEASE, orderId, productId + ":" + quantity),
                CompensationTaskType.INVENTORY_RELEASE,
                orderId,
                null,
                productId,
                quantity,
                null,
                null,
                null
        );
    }

    @Transactional
    public void enqueueVoucherUse(String orderId, String voucherCode) {
        upsert(
                createTaskKey(CompensationTaskType.VOUCHER_USE, orderId, voucherCode),
                CompensationTaskType.VOUCHER_USE,
                orderId,
                null,
                null,
                null,
                null,
                voucherCode,
                null
        );
    }

    @Transactional
    public void enqueueVoucherRestore(String orderId, String voucherCode, String idempotencyKey) {
        upsert(
                createTaskKey(CompensationTaskType.VOUCHER_RESTORE, orderId, idempotencyKey),
                CompensationTaskType.VOUCHER_RESTORE,
                orderId,
                null,
                null,
                null,
                null,
                voucherCode,
                idempotencyKey
        );
    }

    private void upsert(
            String taskKey,
            CompensationTaskType taskType,
            String orderId,
            String userId,
            String productId,
            Integer quantity,
            Double amount,
            String voucherCode,
            String idempotencyKey
    ) {
        OrderCompensationTask task = orderCompensationTaskRepository.findByTaskKey(taskKey)
                .orElseGet(OrderCompensationTask::new);
        task.setTaskKey(taskKey);
        task.setTaskType(taskType);
        task.setOrderId(orderId);
        task.setUserId(userId);
        task.setProductId(productId);
        task.setQuantity(quantity);
        task.setAmount(amount);
        task.setVoucherCode(voucherCode);
        task.setIdempotencyKey(idempotencyKey);
        task.setStatus(CompensationTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setLastError(null);
        task.setNextRetryAt(LocalDateTime.now());
        orderCompensationTaskRepository.save(task);
    }

    private String createTaskKey(CompensationTaskType type, String orderId, String discriminator) {
        String safeOrderId = orderId == null ? "unknown-order" : orderId.trim();
        String safeDiscriminator = discriminator == null ? "none" : discriminator.trim();
        return type.name() + ":" + safeOrderId + ":" + safeDiscriminator;
    }
}
