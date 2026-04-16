package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.state.OrderStateMachine;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.service.checkout.OrderCheckoutFacade;
import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
    private ProfileGateway profileGateway;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId("order-1");
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(1);
        order.setStatus(OrderStatus.PENDING);
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

        assertThrows(IllegalArgumentException.class, () ->
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
        order.setTotalAmount(10000.0);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.cancelOrderByJastiper("order-1", "jastiper-1");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        verify(walletGateway).refund("u1", 10000.0);
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
        order.setTotalAmount(null);
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderStateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED)).thenReturn(true);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        orderService.cancelOrderByJastiper("order-1", "jastiper-1");
        verify(walletGateway).refund("u1", 0.0);
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
        order.setJastiperId("j1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.submitOrderRating("order-1", "user-1", 5, 4);

        assertEquals(5, result.getJastiperRating());
        assertEquals(4, result.getProductRating());
        verify(profileGateway).submitRating(anyString(), anyString(), any(), anyString(), anyInt(), anyInt());
        verify(orderRepository).save(order);
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
        verify(profileGateway, never()).submitRating(anyString(), anyString(), any(), anyString(), anyInt(), anyInt());
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
}
