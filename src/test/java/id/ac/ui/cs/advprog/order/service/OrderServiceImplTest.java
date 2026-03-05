package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Order order;
    private InventoryResponse inventoryResponse;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId("order-123");
        order.setProductId("prod-123");
        order.setUserId("user-456");
        order.setJumlah(2);
        order.setStatus(OrderStatus.PENDING);

        inventoryResponse = new InventoryResponse();
        inventoryResponse.setProductId("prod-123");
        inventoryResponse.setProductQuantity(10);
        inventoryResponse.setPrice(10000.0);
    }

    @Test
    void testCreateOrder_Success() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
                .thenReturn(inventoryResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        Order result = orderService.createOrder(order);
        assertNotNull(result);
        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void testFindAllOrders() {
        when(orderRepository.findAll()).thenReturn(Arrays.asList(order));
        List<Order> result = orderService.findAllOrders();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testFindOrderById_Found() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(order));
        Order result = orderService.findOrderById("order-123");
        assertEquals("order-123", result.getId());
    }

    @Test
    void testFindOrderById_NotFound() {
        when(orderRepository.findById("ngawur")).thenReturn(Optional.empty());
        Order result = orderService.findOrderById("ngawur");
        assertNull(result);
    }

    @Test
    void testUpdateOrderStatus_Success() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);
        Order result = orderService.updateOrderStatus("order-123", "SHIPPED");
        assertEquals(OrderStatus.SHIPPED, result.getStatus());
    }

    @Test
    void testUpdateOrderStatus_InvalidStatus() {
        when(orderRepository.findById("order-123")).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class, () ->
                orderService.updateOrderStatus("order-123", "STATUS_NGAWUR")
        );
    }
}