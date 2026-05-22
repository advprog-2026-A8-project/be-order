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
                CompensationTaskValues.wallet(orderId, userId, amount, idempotencyKey)
        );
    }

    @Transactional
    public void enqueueWalletDebit(String orderId, String userId, double amount, String idempotencyKey) {
        upsert(
                createTaskKey(CompensationTaskType.WALLET_DEBIT, orderId, idempotencyKey),
                CompensationTaskType.WALLET_DEBIT,
                CompensationTaskValues.wallet(orderId, userId, amount, idempotencyKey)
        );
    }

    @Transactional
    public void enqueueInventoryReserve(String orderId, String productId, int quantity) {
        upsert(
                createTaskKey(CompensationTaskType.INVENTORY_RESERVE, orderId, productId + ":" + quantity),
                CompensationTaskType.INVENTORY_RESERVE,
                CompensationTaskValues.inventory(orderId, productId, quantity)
        );
    }

    @Transactional
    public void enqueueInventoryRelease(String orderId, String productId, int quantity) {
        upsert(
                createTaskKey(CompensationTaskType.INVENTORY_RELEASE, orderId, productId + ":" + quantity),
                CompensationTaskType.INVENTORY_RELEASE,
                CompensationTaskValues.inventory(orderId, productId, quantity)
        );
    }

    @Transactional
    public void enqueueVoucherUse(String orderId, String voucherCode) {
        upsert(
                createTaskKey(CompensationTaskType.VOUCHER_USE, orderId, voucherCode),
                CompensationTaskType.VOUCHER_USE,
                CompensationTaskValues.voucher(orderId, voucherCode, null)
        );
    }

    @Transactional
    public void enqueueVoucherRestore(String orderId, String voucherCode, String idempotencyKey) {
        upsert(
                createTaskKey(CompensationTaskType.VOUCHER_RESTORE, orderId, idempotencyKey),
                CompensationTaskType.VOUCHER_RESTORE,
                CompensationTaskValues.voucher(orderId, voucherCode, idempotencyKey)
        );
    }

    private void upsert(String taskKey, CompensationTaskType taskType, CompensationTaskValues values) {
        OrderCompensationTask task = orderCompensationTaskRepository.findByTaskKey(taskKey)
                .orElseGet(OrderCompensationTask::new);
        task.setTaskKey(taskKey);
        task.setTaskType(taskType);
        task.setOrderId(values.orderId());
        task.setUserId(values.userId());
        task.setProductId(values.productId());
        task.setQuantity(values.quantity());
        task.setAmount(values.amount());
        task.setVoucherCode(values.voucherCode());
        task.setIdempotencyKey(values.idempotencyKey());
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

    private record CompensationTaskValues(
            String orderId,
            String userId,
            String productId,
            Integer quantity,
            Double amount,
            String voucherCode,
            String idempotencyKey
    ) {
        private static CompensationTaskValues wallet(
                String orderId,
                String userId,
                Double amount,
                String idempotencyKey
        ) {
            return new CompensationTaskValues(orderId, userId, null, null, amount, null, idempotencyKey);
        }

        private static CompensationTaskValues inventory(String orderId, String productId, Integer quantity) {
            return new CompensationTaskValues(orderId, null, productId, quantity, null, null, null);
        }

        private static CompensationTaskValues voucher(String orderId, String voucherCode, String idempotencyKey) {
            return new CompensationTaskValues(orderId, null, null, null, null, voucherCode, idempotencyKey);
        }
    }
}
