package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.model.Order;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;

class Slf4jCheckoutAuditLoggerTest {

    @Test
    void allAuditMethodsShouldBeInvokable() {
        Slf4jCheckoutAuditLogger logger = new Slf4jCheckoutAuditLogger(mock(CheckoutAuditTaskDispatcher.class));
        Order order = new Order();
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(2);

        assertDoesNotThrow(() -> logger.logCheckoutStarted(order, "idem-1"));
        assertDoesNotThrow(() -> logger.logIdempotencyHit("idem-1", "order-1"));
        assertDoesNotThrow(() -> logger.logIdempotencyMismatch("idem-1", "order-1"));
        assertDoesNotThrow(() -> logger.logDebitSucceeded("u1", 10000.0));
        assertDoesNotThrow(() -> logger.logStockReductionSucceeded("p1", 2));
        assertDoesNotThrow(() -> logger.logRefundTriggered("u1", 10000.0, "inventory_reduce_failed"));
        assertDoesNotThrow(() -> logger.logValidationFailed("invalid_quantity"));
    }

    @Test
    void allAuditMethodsShouldSwallowDispatcherException() {
        CheckoutAuditTaskDispatcher dispatcher = mock(CheckoutAuditTaskDispatcher.class);
        doThrow(new IllegalStateException("queue unavailable")).when(dispatcher).enqueue(anyString(), anyString());
        Slf4jCheckoutAuditLogger logger = new Slf4jCheckoutAuditLogger(dispatcher);
        Order order = new Order();
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(2);

        assertDoesNotThrow(() -> logger.logCheckoutStarted(order, "idem-1"));
        assertDoesNotThrow(() -> logger.logIdempotencyHit("idem-1", "order-1"));
        assertDoesNotThrow(() -> logger.logIdempotencyMismatch("idem-1", "order-1"));
        assertDoesNotThrow(() -> logger.logDebitSucceeded("u1", 10000.0));
        assertDoesNotThrow(() -> logger.logStockReductionSucceeded("p1", 2));
        assertDoesNotThrow(() -> logger.logRefundTriggered("u1", 10000.0, "inventory_reduce_failed"));
        assertDoesNotThrow(() -> logger.logValidationFailed("invalid_quantity"));
    }
}
