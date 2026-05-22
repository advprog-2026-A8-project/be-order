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

import java.lang.reflect.Method;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutFacadeTest {

    @Mock
    private InventoryGateway inventoryGateway;

    @Mock
    private WalletGateway walletGateway;
    @Mock
    private VoucherGateway voucherGateway;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderIdempotencyRepository orderIdempotencyRepository;

    @Mock
    private CheckoutLockManager checkoutLockManager;

    @Mock
    private CheckoutAuditLogger checkoutAuditLogger;

    @Mock
    private CompensationTaskDispatcher compensationTaskDispatcher;

    @InjectMocks
    private OrderCheckoutFacade checkoutFacade;

    private Order order;
    private InventoryResponse product;

    @BeforeEach
    void setUp() {
        lenient().when(checkoutLockManager.withProductLock(anyString(), any()))
                .thenAnswer(invocation -> runCriticalSection(invocation.getArgument(1)));
        lenient().when(checkoutLockManager.withIdempotencyLock(anyString(), any()))
                .thenAnswer(invocation -> runCriticalSection(invocation.getArgument(1)));

        order = new Order();
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(2);

        product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductQuantity(10);
        product.setPrice(5000.0);
    }

    private <T> T runCriticalSection(Supplier<T> criticalSection) {
        return criticalSection.get();
    }

    @Test
    void checkoutSuccessShouldSetPaidAndPersist() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = checkoutFacade.checkout(order);

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(walletGateway).ensureSufficientBalance("u1", 10000.0);
        verify(walletGateway).debit(anyString(), anyString(), anyDouble(), anyString());
        verify(inventoryGateway).reserveStock("p1", 2);
        verify(checkoutAuditLogger).logCheckoutStarted(order, null);
        verify(checkoutAuditLogger).logDebitSucceeded("u1", 10000.0);
        verify(checkoutAuditLogger).logStockReductionSucceeded("p1", 2);
        verify(voucherGateway, never()).validateDiscount(anyString(), anyDouble());
        verify(voucherGateway, never()).useVoucher(anyString());
    }

    @Test
    void checkoutWithVoucherShouldApplyDiscountAndUseVoucher() {
        order.setVoucherCode("HEMAT10");
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(voucherGateway.validateDiscount("HEMAT10", 10000.0)).thenReturn(1500.0);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = checkoutFacade.checkout(order);

        assertEquals(8500.0, result.getTotalAmount());
        assertEquals(Boolean.TRUE, result.getVoucherApplied());
        verify(walletGateway).ensureSufficientBalance("u1", 8500.0);
        verify(walletGateway).debit(anyString(), anyString(), eq(8500.0), anyString());
        verify(voucherGateway).useVoucher("HEMAT10");
    }

    @Test
    void checkoutWithVoucherShouldClampTotalToZeroWhenDiscountExceedsBasePrice() {
        order.setVoucherCode("HEMAT10");
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(voucherGateway.validateDiscount("HEMAT10", 10000.0)).thenReturn(15000.0);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = checkoutFacade.checkout(order);

        assertEquals(0.0, result.getTotalAmount());
        verify(walletGateway).ensureSufficientBalance("u1", 0.0);
        verify(walletGateway).debit(anyString(), anyString(), eq(0.0), anyString());
    }

    @Test
    void checkoutWithInvalidVoucherShouldFailBeforeWalletCharge() {
        order.setVoucherCode("BADVOUCHER");
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalArgumentException("invalid voucher"))
                .when(voucherGateway).validateDiscount("BADVOUCHER", 10000.0);

        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(walletGateway, never()).debit(anyString(), anyString(), anyDouble(), anyString());
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_VOUCHER);
    }

    @Test
    void checkoutShouldStillSucceedWhenVoucherUseFailsAfterCheckout() {
        order.setVoucherCode("HEMAT10");
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(voucherGateway.validateDiscount("HEMAT10", 10000.0)).thenReturn(1000.0);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("voucher service down")).when(voucherGateway).useVoucher("HEMAT10");

        Order result = checkoutFacade.checkout(order);

        assertEquals(OrderStatus.PAID, result.getStatus());
        assertEquals(9000.0, result.getTotalAmount());
        assertNotEquals(Boolean.TRUE, result.getVoucherApplied());
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VOUCHER_USE_FAILED_AFTER_CHECKOUT);
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
    void checkoutShouldRejectSelfPurchaseByJastiper() {
        order.setUserId("550e8400-e29b-41d4-a716-446655440000");
        product.setJastiperId("550E8400-E29B-41D4-A716-446655440000");
        when(inventoryGateway.getProduct("p1")).thenReturn(product);

        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));

        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_SELF_PURCHASE);
        verify(walletGateway, never()).debit(anyString(), anyString(), anyDouble(), anyString());
        verify(inventoryGateway, never()).reserveStock(anyString(), any(Integer.class));
    }

    @Test
    void checkoutShouldRejectWhenInventoryProductMissingOrInsufficient() {
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
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalArgumentException("wallet error"))
                .when(walletGateway).ensureSufficientBalance("u1", 10000.0);

        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(inventoryGateway, never()).reserveStock(any(), any(Integer.class));
        verify(checkoutAuditLogger).logValidationFailed(CheckoutAuditReason.VALIDATION_WALLET_DEBIT_FAILED);
    }

    @Test
    void checkoutShouldRefundWhenStockReductionFailsAfterDebit() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("inventory down")).when(inventoryGateway).reserveStock("p1", 2);

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));
        verify(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());
        verify(checkoutAuditLogger).logRefundTriggered("u1", 10000.0, CheckoutAuditReason.REFUND_INVENTORY_REDUCE_FAILED);
    }

    @Test
    void checkoutShouldWrapWhenRefundAlsoFailsAfterStockReductionFailure() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("inventory down")).when(inventoryGateway).reserveStock("p1", 2);
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
        verify(compensationTaskDispatcher).enqueueWalletRefund(anyString(), anyString(), anyDouble(), anyString());
    }

    @Test
    void checkoutShouldWrapWhenRefundAndEnqueueAlsoFailAfterStockReductionFailure() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("inventory down")).when(inventoryGateway).reserveStock("p1", 2);
        doThrow(new IllegalStateException("refund down"))
                .when(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());
        doThrow(new IllegalStateException("enqueue down"))
                .when(compensationTaskDispatcher).enqueueWalletRefund(anyString(), anyString(), anyDouble(), anyString());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));

        assertTrue(ex.getSuppressed().length >= 2);
        verify(compensationTaskDispatcher).enqueueWalletRefund(anyString(), anyString(), anyDouble(), anyString());
    }

    @Test
    void checkoutShouldCompensateRefundAndReleaseWhenSaveFailsAfterReserve() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("db down")).when(orderRepository).save(any(Order.class));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));

        verify(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());
        verify(inventoryGateway).releaseStock("p1", 2);
        verify(compensationTaskDispatcher, never()).enqueueWalletRefund(anyString(), anyString(), anyDouble(), anyString());
        verify(compensationTaskDispatcher, never()).enqueueInventoryRelease(anyString(), anyString(), anyInt());
    }

    @Test
    void checkoutShouldThrowWhenSaveFailsAndReleaseAlsoFails() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("db down")).when(orderRepository).save(any(Order.class));
        doThrow(new IllegalStateException("release failed")).when(inventoryGateway).releaseStock("p1", 2);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));
        assertTrue(ex.getMessage().contains("Kompensasi"));
        verify(compensationTaskDispatcher).enqueueInventoryRelease(anyString(), anyString(), anyInt());
    }

    @Test
    void checkoutShouldThrowWhenSaveFailsAndAllCompensationsFail() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("db down")).when(orderRepository).save(any(Order.class));
        doThrow(new IllegalStateException("refund failed"))
                .when(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());
        doThrow(new IllegalStateException("release failed")).when(inventoryGateway).releaseStock("p1", 2);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));
        assertTrue(ex.getSuppressed().length >= 2);
        verify(checkoutAuditLogger).logRefundTriggered("u1", 10000.0, CheckoutAuditReason.REFUND_COMPENSATION_FAILED);
        verify(compensationTaskDispatcher).enqueueWalletRefund(anyString(), anyString(), anyDouble(), anyString());
        verify(compensationTaskDispatcher).enqueueInventoryRelease(anyString(), anyString(), anyInt());
    }

    @Test
    void checkoutShouldRestoreVoucherWhenSaveFailsAfterVoucherApplied() {
        order.setVoucherCode("HEMAT10");
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(voucherGateway.validateDiscount("HEMAT10", 10000.0)).thenReturn(1000.0);
        doThrow(new IllegalStateException("db down")).when(orderRepository).save(any(Order.class));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));
        verify(voucherGateway).useVoucher("HEMAT10");
        verify(voucherGateway).restoreVoucher(eq("HEMAT10"), anyString());
    }

    @Test
    void checkoutWithIdempotencyKeyShouldStoreKeyOnFirstRequest() {
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

        when(orderIdempotencyRepository.findById("idem-1"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-1", "order-100")));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        Order result = checkoutFacade.checkout(order, "idem-1");

        assertEquals("order-100", result.getId());
        verify(walletGateway, never()).debit(anyString(), anyString(), anyDouble(), anyString());
        verify(inventoryGateway, never()).reserveStock(any(), any(Integer.class));
        verify(orderRepository, times(0)).save(any(Order.class));
        verify(checkoutAuditLogger).logIdempotencyHit("idem-1", "order-100");
    }

    @Test
    void checkoutWithExistingIdempotencyKeyButMissingOrderShouldThrow() {
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

    @Test
    void checkoutWithIdempotencyShouldThrowWhenRaceWinnerOrderRecordExistsButOrderMissing() {
        when(orderIdempotencyRepository.findById("idem-race-order-missing"))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-race-order-missing", "order-404")));
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setId("order-101");
            return saved;
        });
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(orderIdempotencyRepository)
                .save(any(OrderIdempotency.class));
        when(orderRepository.findById("order-404")).thenReturn(java.util.Optional.empty());

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order, "idem-race-order-missing"));
    }

    @Test
    void checkoutWithBlankIdempotencyKeyShouldFallbackToNonIdempotentFlow() {
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = checkoutFacade.checkout(order, "   ");

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderIdempotencyRepository, never()).findById(anyString());
        verify(orderIdempotencyRepository, never()).save(any(OrderIdempotency.class));
    }

    @Test
    void checkoutShouldAddVoucherRestoreFailureAsSuppressedException() {
        order.setVoucherCode("HEMAT10");
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(voucherGateway.validateDiscount("HEMAT10", 10000.0)).thenReturn(1000.0);
        doThrow(new IllegalStateException("db down")).when(orderRepository).save(any(Order.class));
        doThrow(new IllegalStateException("voucher restore failed"))
                .when(voucherGateway).restoreVoucher(eq("HEMAT10"), anyString());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));

        assertTrue(ex.getSuppressed().length >= 1);
        verify(voucherGateway).restoreVoucher(eq("HEMAT10"), anyString());
        verify(compensationTaskDispatcher).enqueueVoucherRestore(anyString(), eq("HEMAT10"), anyString());
    }

    @Test
    void checkoutWithExistingIdempotencyKeyAndDifferentProductShouldThrow() {
        Order existingOrder = new Order();
        existingOrder.setId("order-100");
        existingOrder.setProductId("p-old");
        existingOrder.setUserId("u1");
        existingOrder.setJumlah(2);
        existingOrder.setJastiperId(null);
        existingOrder.setAlamatPengiriman(null);

        when(orderIdempotencyRepository.findById("idem-product"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-product", "order-100")));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order, "idem-product"));
    }

    @Test
    void checkoutWithExistingIdempotencyKeyAndDifferentUserShouldThrow() {
        Order existingOrder = new Order();
        existingOrder.setId("order-100");
        existingOrder.setProductId("p1");
        existingOrder.setUserId("u-old");
        existingOrder.setJumlah(2);
        existingOrder.setJastiperId(null);
        existingOrder.setAlamatPengiriman(null);

        when(orderIdempotencyRepository.findById("idem-user"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-user", "order-100")));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order, "idem-user"));
    }

    @Test
    void checkoutWithExistingIdempotencyKeyAndDifferentJastiperShouldThrow() {
        Order existingOrder = new Order();
        existingOrder.setId("order-100");
        existingOrder.setProductId("p1");
        existingOrder.setUserId("u1");
        existingOrder.setJumlah(2);
        existingOrder.setJastiperId("j-old");
        existingOrder.setAlamatPengiriman("alamat");

        Order incoming = new Order();
        incoming.setProductId("p1");
        incoming.setUserId("u1");
        incoming.setJumlah(2);
        incoming.setJastiperId("j-new");
        incoming.setAlamatPengiriman("alamat");

        when(orderIdempotencyRepository.findById("idem-jastiper"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-jastiper", "order-100")));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(incoming, "idem-jastiper"));
    }

    @Test
    void checkoutWithExistingIdempotencyKeyAndDifferentAddressShouldThrow() {
        Order existingOrder = new Order();
        existingOrder.setId("order-100");
        existingOrder.setProductId("p1");
        existingOrder.setUserId("u1");
        existingOrder.setJumlah(2);
        existingOrder.setJastiperId("j1");
        existingOrder.setAlamatPengiriman("alamat-lama");

        Order incoming = new Order();
        incoming.setProductId("p1");
        incoming.setUserId("u1");
        incoming.setJumlah(2);
        incoming.setJastiperId("j1");
        incoming.setAlamatPengiriman("alamat-baru");

        when(orderIdempotencyRepository.findById("idem-address"))
                .thenReturn(java.util.Optional.of(new OrderIdempotency("idem-address", "order-100")));
        when(orderRepository.findById("order-100")).thenReturn(java.util.Optional.of(existingOrder));

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(incoming, "idem-address"));
    }

    @Test
    void resolveWalletIdempotencyKeyShouldFallbackForBlankValue() throws Exception {
        Method method = OrderCheckoutFacade.class
                .getDeclaredMethod("resolveWalletIdempotencyKey", Order.class, String.class);
        method.setAccessible(true);

        Order explicitOrder = new Order();
        explicitOrder.setId("order-xyz");

        String result = (String) method.invoke(checkoutFacade, explicitOrder, "   ");

        assertEquals("wallet-order-order-xyz", result);
    }

    @Test
    void tryEnqueueWithSuppressedShouldReturnNullWhenTaskSucceeds() throws Exception {
        RuntimeException result = invokeTryEnqueue("wallet_refund", () -> {
            // no-op
        });

        assertNull(result);
    }

    @Test
    void tryEnqueueWithSuppressedShouldReturnExceptionWhenTaskFails() throws Exception {
        RuntimeException result = invokeTryEnqueue("wallet_refund", () -> {
            throw new IllegalStateException("queue down");
        });

        assertEquals("queue down", result.getMessage());
    }

    @Test
    void attachSuppressedIfPresentShouldAttachAndIgnoreNulls() throws Exception {
        RuntimeException target = new RuntimeException("target");
        RuntimeException suppressed = new RuntimeException("suppressed");

        invokeAttachSuppressed(target, suppressed);
        assertEquals(1, target.getSuppressed().length);

        invokeAttachSuppressed(target, null);
        assertEquals(1, target.getSuppressed().length);

        invokeAttachSuppressed(null, suppressed);
    }

    @Test
    void extractRootCauseSummaryShouldReturnDeepestCauseAndMessage() throws Exception {
        Throwable deepest = new IllegalStateException("deepest");
        Throwable wrapped = new RuntimeException("top", new IllegalArgumentException("mid", deepest));

        String result = invokeExtractRootCauseSummary(wrapped);

        assertEquals("IllegalStateException: deepest", result);
    }

    @Test
    void extractRootCauseSummaryShouldReturnClassNameWhenMessageMissing() throws Exception {
        Throwable root = new RuntimeException((String) null);

        String result = invokeExtractRootCauseSummary(root);

        assertEquals("RuntimeException", result);
    }

    @Test
    void extractRootCauseSummaryShouldReturnClassNameWhenMessageBlank() throws Exception {
        Throwable root = new RuntimeException("   ");

        String result = invokeExtractRootCauseSummary(root);

        assertEquals("RuntimeException", result);
    }

    @Test
    void extractRootCauseSummaryShouldHandleSelfReferencingCauseSafely() throws Exception {
        class SelfCauseException extends RuntimeException {
            private SelfCauseException(String message) {
                super(message);
            }

            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        }

        RuntimeException selfCause = new SelfCauseException("self");

        String result = invokeExtractRootCauseSummary(selfCause);

        assertEquals("SelfCauseException: self", result);
    }

    private RuntimeException invokeTryEnqueue(String taskName, Runnable action) throws Exception {
        Method method = OrderCheckoutFacade.class
                .getDeclaredMethod("tryEnqueueWithSuppressed", String.class, Runnable.class);
        method.setAccessible(true);
        return (RuntimeException) method.invoke(checkoutFacade, taskName, action);
    }

    private void invokeAttachSuppressed(RuntimeException target, RuntimeException suppressed) throws Exception {
        Method method = OrderCheckoutFacade.class
                .getDeclaredMethod("attachSuppressedIfPresent", RuntimeException.class, RuntimeException.class);
        method.setAccessible(true);
        method.invoke(checkoutFacade, target, suppressed);
    }

    private String invokeExtractRootCauseSummary(Throwable throwable) throws Exception {
        Method method = OrderCheckoutFacade.class
                .getDeclaredMethod("extractRootCauseSummary", Throwable.class);
        method.setAccessible(true);
        return (String) method.invoke(checkoutFacade, throwable);
    }
}
