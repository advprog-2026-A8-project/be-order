package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.model.Order;

public interface CheckoutAuditLogger {
    void logCheckoutStarted(Order order, String idempotencyKey);
    void logIdempotencyHit(String idempotencyKey, String orderId);
    void logIdempotencyMismatch(String idempotencyKey, String existingOrderId);
    void logDebitSucceeded(String userId, double amount);
    void logStockReductionSucceeded(String productId, int quantity);
    void logRefundTriggered(String userId, double amount, String reason);
    void logValidationFailed(String reason);
}
