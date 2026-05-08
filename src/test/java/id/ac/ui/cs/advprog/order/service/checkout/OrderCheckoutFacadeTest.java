package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.OrderIdempotency;
import id.ac.ui.cs.advprog.order.repository.OrderIdempotencyRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutFacadeTest {

    @Mock
    private InventoryGateway inventoryGateway;

    @Mock
    private WalletGateway walletGateway;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderIdempotencyRepository orderIdempotencyRepository;

    @Mock
    private CheckoutLockManager checkoutLockManager;

    @Mock
    private CheckoutAuditLogger checkoutAuditLogger;

    @InjectMocks
    private OrderCheckoutFacade checkoutFacade;

    private Order order;
    private InventoryResponse product;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(2);

        product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductQuantity(10);
        product.setPrice(5000.0);
    }

    @Test
    void checkoutSuccessShouldSetPaidAndPersist() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = checkoutFacade.checkout(order);

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(walletGateway).ensureSufficientBalance("u1", 10000.0);
        verify(walletGateway).debit(anyString(), anyString(), anyDouble(), anyString());
        verify(inventoryGateway).reduceStock("p1", 2);
        verify(checkoutAuditLogger).logCheckoutStarted(order, null);
        verify(checkoutAuditLogger).logDebitSucceeded("u1", 10000.0);
        verify(checkoutAuditLogger).logStockReductionSucceeded("p1", 2);
    }

    @Test
    void checkoutShouldRejectWhenProductIdOrUserIdMissing() {
        order.setProductId(null);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_MISSING_PRODUCT_OR_USER);

        order.setProductId("p1");
        order.setUserId(null);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(checkoutAuditLogger, times(2))
                .logValidationFailed(CheckoutAuditReason.VALIDATION_MISSING_PRODUCT_OR_USER);
    }

    @Test
    void checkoutShouldRejectWhenProductIdOrUserIdBlank() {
        order.setProductId(" ");
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));

        order.setProductId("p1");
        order.setUserId(" ");
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
    }

    @Test
    void checkoutShouldRejectWhenOrderNull() {
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(null));
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_ORDER_NULL);
    }

    @Test
    void checkoutShouldRejectWhenJumlahInvalid() {
        order.setJumlah(0);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_QUANTITY);

        order.setJumlah(-1);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(checkoutAuditLogger, times(2)).logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_QUANTITY);

        order.setJumlah(null);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(checkoutAuditLogger, times(3)).logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_QUANTITY);
    }

    @Test
    void checkoutShouldRejectWhenInventoryProductMissingOrInsufficient() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_INSUFFICIENT_STOCK);

        product.setProductQuantity(1);
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(checkoutAuditLogger, times(2)).logValidationFailed(CheckoutAuditReason.VALIDATION_INSUFFICIENT_STOCK);
    }

    @Test
    void checkoutShouldRejectWhenInventoryPriceMissingOrInvalid() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());

        product.setPrice(null);
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));

        product.setPrice(0.0);
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));

        product.setPrice(-100.0);
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
    }

    @Test
    void checkoutShouldPropagateWalletFailureAndNotReduceStock() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalArgumentException("wallet error"))
                .when(walletGateway).ensureSufficientBalance("u1", 10000.0);

        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(inventoryGateway, never()).reduceStock(any(), any(Integer.class));
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_WALLET_DEBIT_FAILED);
    }

    @Test
    void checkoutShouldRefundWhenStockReductionFailsAfterDebit() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("inventory down")).when(inventoryGateway).reduceStock("p1", 2);

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));
        verify(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());
        verify(checkoutAuditLogger).logRefundTriggered("u1", 10000.0, CheckoutAuditReason.REFUND_INVENTORY_REDUCE_FAILED);
    }

    @Test
    void checkoutShouldWrapWhenRefundAlsoFailsAfterStockReductionFailure() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("inventory down")).when(inventoryGateway).reduceStock("p1", 2);
        doThrow(new IllegalStateException("refund down"))
                .when(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));

        assertTrue(ex.getMessage().contains("Dana direfund"));
        verify(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());
        verify(checkoutAuditLogger).logRefundTriggered(
                "u1",
                10000.0,
                CheckoutAuditReason.REFUND_INVENTORY_REDUCE_FAILED
        );
        verify(checkoutAuditLogger).logRefundTriggered(
                "u1",
                10000.0,
                CheckoutAuditReason.REFUND_COMPENSATION_FAILED
        );
    }

    @Test
    void checkoutWithIdempotencyKeyShouldStoreKeyOnFirstRequest() {
        when(checkoutLockManager.getLockForIdempotencyKey("idem-1")).thenReturn(new ReentrantLock());
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(orderIdempotencyRepository.findById("idem-1")).thenReturn(java.util.Optional.empty());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId("order-100");
            return saved;
        });

        Order result = checkoutFacade.checkout(order, "idem-1");

        assertEquals("order-100", result.getId());
        verify(orderIdempotencyRepository).save(any(OrderIdempotency.class));
    }

    @Test
    void checkoutWithExistingIdempotencyKeyShouldReturnExistingOrderWithoutChargingAgain() {
        Order existingOrder = new Order();
        existingOrder.setId("order-100");
        existingOrder.setProductId("p1");
        existingOrder.setUserId("u1");
        existingOrder.setJumlah(2);
        existingOrder.setJastiperId(null);
        existingOrder.setAlamatPengiriman(null);

        when(checkoutLockManager.getLockForIdempotencyKey("idem-1")).thenReturn(new ReentrantLock());
        when(orderIdempotencyRepository.findById("idem-1"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-1", "order-100")));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        Order result = checkoutFacade.checkout(order, "idem-1");

        assertEquals("order-100", result.getId());
        verify(walletGateway, never()).debit(anyString(), anyString(), anyDouble(), anyString());
        verify(inventoryGateway, never()).reduceStock(any(), any(Integer.class));
        verify(orderRepository, times(0)).save(any(Order.class));
        verify(checkoutAuditLogger).logIdempotencyHit("idem-1", "order-100");
    }

    @Test
    void checkoutWithExistingIdempotencyKeyButMissingOrderShouldThrow() {
        when(checkoutLockManager.getLockForIdempotencyKey("idem-1")).thenReturn(new ReentrantLock());
        when(orderIdempotencyRepository.findById("idem-1"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-1", "order-404")));
        when(orderRepository.findById("order-404")).thenReturn(java.util.Optional.empty());

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order, "idem-1"));
    }

    @Test
    void checkoutWithExistingIdempotencyKeyAndDifferentPayloadShouldThrow() {
        Order existingOrder = new Order();
        existingOrder.setId("order-100");
        existingOrder.setProductId("p1");
        existingOrder.setUserId("u1");
        existingOrder.setJumlah(2);
        existingOrder.setJastiperId("j1");
        existingOrder.setAlamatPengiriman("alamat lama");

        Order newPayload = new Order();
        newPayload.setProductId("p1");
        newPayload.setUserId("u1");
        newPayload.setJumlah(3);
        newPayload.setJastiperId("j1");
        newPayload.setAlamatPengiriman("alamat lama");

        when(checkoutLockManager.getLockForIdempotencyKey("idem-1")).thenReturn(new ReentrantLock());
        when(orderIdempotencyRepository.findById("idem-1"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-1", "order-100")));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> checkoutFacade.checkout(newPayload, "idem-1")
        );

        assertTrue(ex.getMessage().contains("Idempotency key"));
        verify(checkoutAuditLogger).logIdempotencyMismatch("idem-1", "order-100");
        verify(walletGateway, never()).debit(anyString(), anyString(), anyDouble(), anyString());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void checkoutWithIdempotencyShouldRecoverWhenDuplicateKeyRaceHappens() {
        Order existingOrder = new Order();
        existingOrder.setId("order-100");
        existingOrder.setProductId("p1");
        existingOrder.setUserId("u1");
        existingOrder.setJumlah(2);
        existingOrder.setJastiperId(null);
        existingOrder.setAlamatPengiriman(null);

        when(checkoutLockManager.getLockForIdempotencyKey("idem-race")).thenReturn(new ReentrantLock());
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(orderIdempotencyRepository.findById("idem-race"))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-race", "order-100")));
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId("order-100");
            return saved;
        });
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(orderIdempotencyRepository)
                .save(any(OrderIdempotency.class));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        Order result = checkoutFacade.checkout(order, "idem-race");

        assertEquals("order-100", result.getId());
    }

    @Test
    void checkoutWithIdempotencyShouldThrowDomainErrorWhenRaceWinnerRecordMissing() {
        when(checkoutLockManager.getLockForIdempotencyKey("idem-missing")).thenReturn(new ReentrantLock());
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(orderIdempotencyRepository.findById("idem-missing"))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.empty());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId("order-101");
            return saved;
        });
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(orderIdempotencyRepository)
                .save(any(OrderIdempotency.class));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order, "idem-missing"));
    }

    @Test
    void checkoutWithIdempotencyShouldRejectRaceWinnerOrderWithDifferentPayload() {
        Order raceWinnerOrder = new Order();
        raceWinnerOrder.setId("order-202");
        raceWinnerOrder.setProductId("p1");
        raceWinnerOrder.setUserId("u1");
        raceWinnerOrder.setJumlah(99);
        raceWinnerOrder.setJastiperId(null);
        raceWinnerOrder.setAlamatPengiriman(null);

        when(checkoutLockManager.getLockForIdempotencyKey("idem-race-mismatch")).thenReturn(new ReentrantLock());
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(orderIdempotencyRepository.findById("idem-race-mismatch"))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-race-mismatch", "order-202")));
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId("order-201");
            return saved;
        });
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(orderIdempotencyRepository)
                .save(any(OrderIdempotency.class));
        when(orderRepository.findById("order-202")).thenReturn(java.util.Optional.of(raceWinnerOrder));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order, "idem-race-mismatch"));
    }
}
