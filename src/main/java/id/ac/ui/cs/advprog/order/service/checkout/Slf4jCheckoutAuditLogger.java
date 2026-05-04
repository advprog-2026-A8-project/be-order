package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.model.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class Slf4jCheckoutAuditLogger implements CheckoutAuditLogger {
    @Override
    public void logCheckoutStarted(Order order, String idempotencyKey) {
        log.info(
                "checkout_started productId={} userId={} quantity={} idempotencyKey={}",
                order.getProductId(),
                order.getUserId(),
                order.getJumlah(),
                idempotencyKey
        );
    }

    @Override
    public void logIdempotencyHit(String idempotencyKey, String orderId) {
        log.info("checkout_idempotency_hit idempotencyKey={} orderId={}", idempotencyKey, orderId);
    }

    @Override
    public void logDebitSucceeded(String userId, double amount) {
        log.info("checkout_debit_succeeded userId={} amount={}", userId, amount);
    }

    @Override
    public void logStockReductionSucceeded(String productId, int quantity) {
        log.info("checkout_stock_reduction_succeeded productId={} quantity={}", productId, quantity);
    }

    @Override
    public void logRefundTriggered(String userId, double amount, String reason) {
        log.warn("checkout_refund_triggered userId={} amount={} reason={}", userId, amount, reason);
    }
}
