package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.exception.InvalidOrderTransitionException;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.state.OrderStateMachine;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.service.checkout.CheckoutLockManager;
import id.ac.ui.cs.advprog.order.service.checkout.CompensationTaskDispatcher;
import id.ac.ui.cs.advprog.order.service.checkout.InventoryGateway;
import id.ac.ui.cs.advprog.order.service.checkout.OrderCheckoutFacade;
import id.ac.ui.cs.advprog.order.service.checkout.VoucherGateway;
import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import id.ac.ui.cs.advprog.order.service.rating.RatingSyncDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderStateMachine orderStateMachine;

    @Mock
    private OrderCheckoutFacade orderCheckoutFacade;

    @Mock
    private WalletGateway walletGateway;

    @Mock
    private InventoryGateway inventoryGateway;

    @Mock
    private VoucherGateway voucherGateway;

    @Mock
    private RatingSyncDispatcher ratingSyncDispatcher;

    @Mock
    private CheckoutLockManager checkoutLockManager;

    @Mock
    private CompensationTaskDispatcher compensationTaskDispatcher;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        lenient().when(checkoutLockManager.withProductLock(anyString(), any()))
                .thenAnswer(invocation -> runCriticalSection(invocation.getArgument(1)));
        lenient().when(checkoutLockManager.withIdempotencyLock(anyString(), any()))
                .thenAnswer(invocation -> runCriticalSection(invocation.getArgument(1)));

        order = new Order();
        order.setId("order-1");
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(1);
        order.setStatus(OrderStatus.PENDING);
    }

    private <T> T runCriticalSection(Supplier<T> criticalSection) {
        return criticalSection.get();
    }

    @Test
    void testCreateOrderDelegatesToFacade() {
        when(orderCheckoutFacade.checkout(order, null)).thenReturn(order);

        Order result = orderService.createOrder(order);

        assertEquals("order-1", result.getId());
    }

    @Test
    void testCreateOrderWithIdempotencyDelegatesToFacade() {
        when(orderCheckoutFacade.checkout(order, "idem-1")).thenReturn(order);

        Order result = orderService.createOrder(order, "idem-1");

        assertEquals("order-1", result.getId());
        verify(orderCheckoutFacade).checkout(eq(order), eq("idem-1"));
    }

    @Test
    void testFindAll() {
        when(orderRepository.findAll()).thenReturn(Arrays.asList(order));
        assertEquals(1, orderService.findAllOrders().size());
    }

    @Test
    void testFindById() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        assertEquals("order-1", orderService.findOrderById("order-1").getId());
        assertNull(orderService.findOrderById("kosong"));
    }

    @Test
    void testUpdateStatusSuccess() {
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.PURCHASED)).thenReturn(true);

        order.setStatus(OrderStatus.PAID);
        Order result = orderService.updateOrderStatus("order-1", "PURCHASED");
        assertEquals(OrderStatus.PURCHASED, result.getStatus());
    }

    @Test
    void testUpdateStatusInvalid() {
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class, () -> orderService.updateOrderStatus("order-1", "NGASAL"));
    }

    @Test
    void testUpdateStatusInvalidTransition() {
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(order));
        order.setStatus(OrderStatus.PAID);
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.COMPLETED)).thenReturn(false);

        assertThrows(InvalidOrderTransitionException.class, () ->
                orderService.updateOrderStatus("order-1", "COMPLETED"));
    }

    @Test
    void testUpdateOrderStatusOrderNotFound() {
        when(orderRepository.findById("id-ngawur")).thenReturn(Optional.empty());
        Order result = orderService.updateOrderStatus("id-ngawur", "SHIPPED");
        assertNull(result);
    }

    @Test
    void testCancelOrderByJastiperSuccess() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.cancelOrderByJastiper("order-1", "jastiper-1");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(walletGateway).refund(eq("u1"), eq("order-1"), eq(10000.0), anyString());
        verify(inventoryGateway).releaseStock("p1", 1);
    }

    @Test
    void testCancelOrderByJastiperNotFound() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());

        assertNull(orderService.cancelOrderByJastiper("missing", "jastiper-1"));
    }

    @Test
    void testCancelOrderByJastiperInvalidTransitionShouldFail() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.COMPLETED, OrderStatus.CANCELLED)).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));
    }

    @Test
    void testCancelOrderByJastiperShouldRefundZeroWhenTotalAmountNull() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setTotalAmount(null);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrderByJastiper("order-1", "jastiper-1");
        verify(walletGateway).refund(eq("u1"), eq("order-1"), eq(0.0), anyString());
        verify(inventoryGateway).releaseStock("p1", 1);
    }

    @Test
    void testCancelOrderByJastiperWrongOwnerShouldFail() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.cancelOrderByJastiper("order-1", "jastiper-2"));
    }

    @Test
    void testCancelOrderByJastiperShouldRestoreVoucherWhenVoucherUsed() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setVoucherCode("HEMAT10");
        order.setVoucherApplied(true);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrderByJastiper("order-1", "jastiper-1");

        verify(voucherGateway).restoreVoucher(eq("HEMAT10"), anyString());
    }

    @Test
    void testCancelOrderByJastiperShouldNotRestoreVoucherWhenNotApplied() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setVoucherCode("HEMAT10");
        order.setVoucherApplied(false);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrderByJastiper("order-1", "jastiper-1");

        verify(voucherGateway, never()).restoreVoucher(anyString(), anyString());
    }

    @Test
    void testCancelOrderByJastiperShouldAttemptRollbackWhenReleaseFails() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        doThrow(new IllegalStateException("release failed")).when(inventoryGateway).releaseStock("p1", 1);

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));

        verify(walletGateway).refund(eq("u1"), eq("order-1"), eq(10000.0), anyString());
        verify(walletGateway).debit(eq("u1"), eq("order-1"), eq(10000.0), anyString());
    }

    @Test
    void testCancelOrderByJastiperShouldRollbackReserveWhenRefundFailsButReleaseSucceeded() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        doThrow(new IllegalStateException("refund failed"))
                .when(walletGateway).refund(eq("u1"), eq("order-1"), eq(10000.0), anyString());

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));

        verify(inventoryGateway).releaseStock("p1", 1);
        verify(inventoryGateway).reserveStock("p1", 1);
        verify(walletGateway, never()).debit(eq("u1"), eq("order-1"), eq(10000.0), anyString());
        verify(compensationTaskDispatcher, never()).enqueueInventoryReserve(anyString(), anyString(), anyInt());
    }

    @Test
    void testCancelOrderByJastiperShouldRollbackDebitAndReserveWhenVoucherRestoreFails() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setVoucherCode("HEMAT10");
        order.setVoucherApplied(true);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        doThrow(new IllegalStateException("restore failed"))
                .when(voucherGateway).restoreVoucher(eq("HEMAT10"), anyString());

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));

        verify(inventoryGateway).reserveStock("p1", 1);
        verify(walletGateway).debit(eq("u1"), eq("order-1"), eq(10000.0), anyString());
        verify(voucherGateway, never()).useVoucher("HEMAT10");
    }

    @Test
    void testCancelOrderByJastiperShouldAttemptVoucherRollbackWhenRestoreSucceededButLaterFailed() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setVoucherCode("HEMAT10");
        order.setVoucherApplied(true);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        doThrow(new IllegalStateException("release failed")).when(inventoryGateway).releaseStock("p1", 1);

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));

        verify(voucherGateway).restoreVoucher(eq("HEMAT10"), anyString());
        verify(voucherGateway).useVoucher("HEMAT10");
    }

    @Test
    void testCancelOrderByJastiperShouldEnqueueWalletDebitWhenRollbackDebitFails() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        doThrow(new IllegalStateException("release failed")).when(inventoryGateway).releaseStock("p1", 1);
        doThrow(new IllegalStateException("debit rollback failed"))
                .when(walletGateway).debit(eq("u1"), eq("order-1"), eq(10000.0), anyString());

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));

        verify(compensationTaskDispatcher).enqueueWalletDebit(eq("order-1"), eq("u1"), eq(10000.0), anyString());
    }

    @Test
    void testCancelOrderByJastiperShouldEnqueueInventoryReserveWhenRollbackReserveFails() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        doThrow(new IllegalStateException("refund failed"))
                .when(walletGateway).refund(eq("u1"), eq("order-1"), eq(10000.0), anyString());
        doThrow(new IllegalStateException("reserve rollback failed"))
                .when(inventoryGateway).reserveStock("p1", 1);

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));

        verify(compensationTaskDispatcher).enqueueInventoryReserve("order-1", "p1", 1);
    }

    @Test
    void testCancelOrderByJastiperShouldEnqueueVoucherUseWhenVoucherRollbackFails() {
        order.setStatus(OrderStatus.PAID);
        order.setJastiperId("jastiper-1");
        order.setProductId("p1");
        order.setJumlah(1);
        order.setVoucherCode("HEMAT10");
        order.setVoucherApplied(true);
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        doThrow(new IllegalStateException("release failed")).when(inventoryGateway).releaseStock("p1", 1);
        doThrow(new IllegalStateException("voucher rollback failed")).when(voucherGateway).useVoucher("HEMAT10");

        assertThrows(IllegalStateException.class, () -> orderService.cancelOrderByJastiper("order-1", "jastiper-1"));

        verify(compensationTaskDispatcher).enqueueVoucherUse("order-1", "HEMAT10");
    }

    @Test
    void testGetTitiperActiveOrders() {
        when(orderRepository.findByUserIdAndStatusIn(anyString(), any()))
                .thenReturn(List.of(order));

        assertEquals(1, orderService.findTitiperActiveOrders("user-1").size());
    }

    @Test
    void testGetTitiperOrderHistory() {
        when(orderRepository.findByUserId("user-1")).thenReturn(List.of(order));

        assertEquals(1, orderService.findTitiperOrderHistory("user-1").size());
    }

    @Test
    void testGetJastiperTodoOrders() {
        when(orderRepository.findByJastiperIdAndStatusIn(anyString(), any()))
                .thenReturn(List.of(order));

        assertEquals(1, orderService.findJastiperTodoOrders("jastiper-1").size());
    }

    @Test
    void testGetJastiperProcessingOrders() {
        when(orderRepository.findByJastiperIdAndStatusIn(anyString(), any()))
                .thenReturn(List.of(order));

        assertEquals(1, orderService.findJastiperProcessingOrders("jastiper-1").size());
    }

    @Test
    void testGetJastiperCompletedOrders() {
        when(orderRepository.findByJastiperIdAndStatusIn(anyString(), any()))
                .thenReturn(List.of(order));

        assertEquals(1, orderService.findJastiperCompletedOrders("jastiper-1").size());
    }

    @Test
    void testGetAdminActiveOrders() {
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(order));
        assertEquals(1, orderService.findAdminActiveOrders().size());
    }

    @Test
    void testSubmitRatingSuccess() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        order.setJastiperId("550e8400-e29b-41d4-a716-446655440000");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.submitOrderRating("order-1", "user-1", 5, 4);

        assertEquals(5, result.getJastiperRating());
        assertEquals(4, result.getProductRating());
        verify(ratingSyncDispatcher).enqueue(order);
        verify(orderRepository).save(order);
    }

    @Test
    void testSubmitRatingShouldThrowWhenEnqueueFails() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        order.setJastiperId("550e8400-e29b-41d4-a716-446655440000");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new IllegalStateException("enqueue failed"))
                .when(ratingSyncDispatcher)
                .enqueue(any(Order.class));

        assertThrows(IllegalStateException.class,
                () -> orderService.submitOrderRating("order-1", "user-1", 5, 4));
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    void testSubmitRatingReturnsNullWhenOrderNotFound() {
        when(orderRepository.findById("missing")).thenReturn(Optional.empty());
        assertNull(orderService.submitOrderRating("missing", "user-1", 5, 4));
    }

    @Test
    void testSubmitRatingShouldFailForWrongUser() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("owner-user");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.submitOrderRating("order-1", "other-user", 5, 4));
        verify(ratingSyncDispatcher, never()).enqueue(any(Order.class));
    }

    @Test
    void testSubmitRatingShouldFailWhenOrderNotCompleted() {
        order.setStatus(OrderStatus.SHIPPED);
        order.setUserId("user-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> orderService.submitOrderRating("order-1", "user-1", 5, 4));
    }

    @Test
    void testSubmitRatingShouldFailWhenAlreadySubmitted() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        order.setRatingSubmitted(true);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
                () -> orderService.submitOrderRating("order-1", "user-1", 5, 4));
    }

    @Test
    void testSubmitRatingShouldFailForInvalidRange() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.submitOrderRating("order-1", "user-1", 0, 6));
    }

    @Test
    void testSubmitRatingShouldAllowBoundaryRange() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        order.setJastiperId("550e8400-e29b-41d4-a716-446655440000");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.submitOrderRating("order-1", "user-1", 1, 5);

        assertEquals(1, result.getJastiperRating());
        assertEquals(5, result.getProductRating());
    }

    @Test
    void testSubmitRatingShouldFailWhenJastiperIdNull() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        order.setJastiperId(null);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.submitOrderRating("order-1", "user-1", 5, 4));
    }

    @Test
    void testSubmitRatingShouldAllowWhenJastiperIdNonUuidAndDelegateToProfileGateway() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        order.setJastiperId("jastiper-x");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.submitOrderRating("order-1", "user-1", 5, 4);

        assertEquals(5, result.getJastiperRating());
        assertEquals(4, result.getProductRating());
        verify(ratingSyncDispatcher).enqueue(order);
    }

    @Test
    void testSubmitRatingShouldFailWhenJastiperIdBlank() {
        order.setStatus(OrderStatus.COMPLETED);
        order.setUserId("user-1");
        order.setJastiperId(" ");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class,
                () -> orderService.submitOrderRating("order-1", "user-1", 5, 4));
        verify(ratingSyncDispatcher, never()).enqueue(any(Order.class));
    }

    @Test
    void testGetAdminOrderSummary() {
        Order paid = new Order();
        paid.setStatus(OrderStatus.PAID);
        Order completed = new Order();
        completed.setStatus(OrderStatus.COMPLETED);
        Order cancelled = new Order();
        cancelled.setStatus(OrderStatus.CANCELLED);

        when(orderRepository.findAll()).thenReturn(List.of(paid, completed, cancelled));

        AdminOrderSummaryResponse summary = orderService.getAdminOrderSummary();

        assertEquals(3L, summary.getTotalOrders());
        assertEquals(1L, summary.getActiveOrders());
        assertEquals(1L, summary.getCompletedOrders());
        assertEquals(1L, summary.getCancelledOrders());
        assertEquals(1L, summary.getStatusCounts().get("PAID"));
        assertEquals(1L, summary.getStatusCounts().get("COMPLETED"));
        assertEquals(1L, summary.getStatusCounts().get("CANCELLED"));
    }

    @Test
    void testGetAdminOrderSummaryWhenNoOrders() {
        when(orderRepository.findAll()).thenReturn(List.of());

        AdminOrderSummaryResponse summary = orderService.getAdminOrderSummary();

        assertEquals(0L, summary.getTotalOrders());
        assertEquals(0L, summary.getActiveOrders());
        assertEquals(0L, summary.getCompletedOrders());
        assertEquals(0L, summary.getCancelledOrders());
        assertEquals(0, summary.getStatusCounts().size());
    }

    @Test
    void testGetAdminOrdersByStatusSuccess() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(order));

        List<Order> result = orderService.findAdminOrdersByStatus("PAID");

        assertEquals(1, result.size());
        assertEquals(OrderStatus.PAID, result.get(0).getStatus());
    }

    @Test
    void testGetAdminOrdersByStatusShouldNormalizeInput() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findByStatusIn(any())).thenReturn(List.of(order));

        List<Order> result = orderService.findAdminOrdersByStatus(" paid ");

        assertEquals(1, result.size());
        assertEquals(OrderStatus.PAID, result.get(0).getStatus());
    }

    @Test
    void testGetAdminOrdersByStatusInvalidShouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> orderService.findAdminOrdersByStatus("INVALID"));
    }

    @Test
    void testGetAdminOrdersByStatusPagedSuccess() {
        order.setStatus(OrderStatus.PAID);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByStatusIn(any(), eq(PageRequest.of(0, 5, org.springframework.data.domain.Sort.by("id").ascending()))))
                .thenReturn(page);

        Page<Order> result = orderService.findAdminOrdersByStatusPaged("PAID", 0, 5);

        assertEquals(1, result.getTotalElements());
        assertEquals(OrderStatus.PAID, result.getContent().get(0).getStatus());
    }

    @Test
    void testGetAdminOrdersByStatusPagedShouldThrowWhenPageNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.findAdminOrdersByStatusPaged("PAID", -1, 5));
    }

    @Test
    void testGetAdminOrdersByStatusPagedShouldThrowWhenSizeNotPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.findAdminOrdersByStatusPaged("PAID", 0, 0));
    }

    @Test
    void testGetAdminOrdersByStatusPagedWithSorting() {
        order.setStatus(OrderStatus.PAID);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByStatusIn(any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        Page<Order> result = orderService.findAdminOrdersByStatusPaged("PAID", 0, 5, "totalAmount", "desc");

        assertEquals(1, result.getTotalElements());
        assertEquals(OrderStatus.PAID, result.getContent().get(0).getStatus());
    }

    @Test
    void testGetAdminOrdersByStatusPagedWithSortingShouldRejectInvalidSortBy() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.findAdminOrdersByStatusPaged("PAID", 0, 5, "createdAt", "asc"));
    }

    @Test
    void testGetAdminOrdersByStatusPagedWithSortingShouldRejectInvalidDirection() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.findAdminOrdersByStatusPaged("PAID", 0, 5, "id", "down"));
    }

    @Test
    void testGetAdminOrdersByStatusPagedWithSortingShouldRejectInvalidStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.findAdminOrdersByStatusPaged("UNKNOWN", 0, 5, "id", "asc"));
    }

    @Test
    void testGetAdminActiveOrdersPaged() {
        order.setStatus(OrderStatus.PAID);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByStatusIn(any(), eq(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("id").ascending()))))
                .thenReturn(page);

        Page<Order> result = orderService.findAdminActiveOrdersPaged(0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals(OrderStatus.PAID, result.getContent().get(0).getStatus());
    }

    @Test
    void testGetAdminActiveOrdersPagedShouldThrowWhenPageNegative() {
        assertThrows(IllegalArgumentException.class, () -> orderService.findAdminActiveOrdersPaged(-1, 10));
    }

    @Test
    void testGetAdminActiveOrdersPagedShouldThrowWhenSizeNotPositive() {
        assertThrows(IllegalArgumentException.class, () -> orderService.findAdminActiveOrdersPaged(0, 0));
    }

    @Test
    void testGetAdminActiveOrdersPagedWithSorting() {
        order.setStatus(OrderStatus.PAID);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByStatusIn(any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        Page<Order> result = orderService.findAdminActiveOrdersPaged(0, 10, "totalAmount", "desc");

        assertEquals(1, result.getTotalElements());
        assertEquals(OrderStatus.PAID, result.getContent().get(0).getStatus());
    }

    @Test
    void testGetAdminActiveOrdersPagedWithSortingShouldRejectInvalidSortBy() {
        assertThrows(IllegalArgumentException.class,
                () -> orderService.findAdminActiveOrdersPaged(0, 10, "createdAt", "asc"));
    }

    @Test
    void testGetAdminActiveOrdersPagedWithSortingShouldUseDefaultsWhenSortAndDirectionBlank() {
        order.setStatus(OrderStatus.PAID);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByStatusIn(any(), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        Page<Order> result = orderService.findAdminActiveOrdersPaged(0, 10, "   ", "   ");

        assertEquals(1, result.getTotalElements());
    }

}
