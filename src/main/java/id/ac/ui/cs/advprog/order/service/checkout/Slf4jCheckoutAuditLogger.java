package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Slf4jCheckoutAuditLogger implements CheckoutAuditLogger {
    @Override
    public void logCheckoutStarted(Order order, String idempotencyKey) {
        log.debug(
                "checkout_started productId={} userId={} quantity={} idempotencyKey={}",
                order.getProductId(),
                order.getUserId(),
                order.getJumlah(),
                idempotencyKey
        );
    }

    @Override
    public void logIdempotencyHit(String idempotencyKey, String orderId) {
        log.debug("checkout_idempotency_hit idempotencyKey={} orderId={}", idempotencyKey, orderId);
    }

    @Override
    public void logIdempotencyMismatch(String idempotencyKey, String existingOrderId) {
        log.warn(
                "checkout_idempotency_mismatch idempotencyKey={} existingOrderId={}",
                idempotencyKey,
                existingOrderId
        );
    }

    @Override
    public void logDebitSucceeded(String userId, double amount) {
        log.debug("checkout_debit_succeeded userId={} amount={}", userId, amount);
    }

    @Override
    public void logStockReductionSucceeded(String productId, int quantity) {
        log.debug("checkout_stock_reduction_succeeded productId={} quantity={}", productId, quantity);
    }

    @Override
    public void logRefundTriggered(String userId, double amount, String reason) {
        log.warn("checkout_refund_triggered userId={} amount={} reason={}", userId, amount, reason);
    }

    @Override
    public void logValidationFailed(String reason) {
        log.warn("checkout_validation_failed reason={}", reason);
    }
}
