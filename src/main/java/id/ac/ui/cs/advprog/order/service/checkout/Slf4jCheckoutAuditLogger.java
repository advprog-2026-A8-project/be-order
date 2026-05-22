package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.model.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Slf4jCheckoutAuditLogger implements CheckoutAuditLogger {
    private final CheckoutAuditTaskDispatcher checkoutAuditTaskDispatcher;

    @Override
    public void logCheckoutStarted(Order order, String idempotencyKey) {
        enqueue(
                "CHECKOUT_STARTED",
                "productId=" + order.getProductId()
                        + ", userId=" + order.getUserId()
                        + ", quantity=" + order.getJumlah()
                        + ", idempotencyKey=" + idempotencyKey
        );
    }

    @Override
    public void logIdempotencyHit(String idempotencyKey, String orderId) {
        enqueue("IDEMPOTENCY_HIT", "idempotencyKey=" + idempotencyKey + ", orderId=" + orderId);
    }

    @Override
    public void logIdempotencyMismatch(String idempotencyKey, String existingOrderId) {
        enqueue("IDEMPOTENCY_MISMATCH", "idempotencyKey=" + idempotencyKey + ", existingOrderId=" + existingOrderId);
    }

    @Override
    public void logDebitSucceeded(String userId, double amount) {
        enqueue("DEBIT_SUCCEEDED", "userId=" + userId + ", amount=" + amount);
    }

    @Override
    public void logStockReductionSucceeded(String productId, int quantity) {
        enqueue("STOCK_REDUCTION_SUCCEEDED", "productId=" + productId + ", quantity=" + quantity);
    }

    @Override
    public void logRefundTriggered(String userId, double amount, String reason) {
        enqueue("REFUND_TRIGGERED", "userId=" + userId + ", amount=" + amount + ", reason=" + reason);
    }

    @Override
    public void logValidationFailed(String reason) {
        enqueue("VALIDATION_FAILED", "reason=" + reason);
    }

    private void enqueue(String eventType, String payload) {
        try {
            checkoutAuditTaskDispatcher.enqueue(eventType, payload);
        } catch (RuntimeException ex) {
            log.warn("checkout_audit_enqueue_failed type={} payload={}", eventType, payload, ex);
        }
    }
}
