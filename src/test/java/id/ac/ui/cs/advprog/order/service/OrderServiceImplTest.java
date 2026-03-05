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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
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
        order.setId("order-1");
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(1);
        order.setStatus(OrderStatus.PENDING);

        inventoryResponse = new InventoryResponse();
        inventoryResponse.setProductId("p1");
        inventoryResponse.setProductQuantity(10);
        inventoryResponse.setPrice(5000.0);

        ReflectionTestUtils.setField(orderService, "inventoryUrl", "http://localhost:8081/api/products");
        ReflectionTestUtils.setField(orderService, "walletUrl", "http://localhost:8082/api/wallets");
    }

    @Test
    void testCreateOrderSuccess() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class))).thenReturn(inventoryResponse);
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.createOrder(order);
        assertEquals(OrderStatus.PAID, result.getStatus());
    }

    @Test
    void testCreateOrderInventoryNotFound() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(order));
    }

    @Test
    void testCreateOrderInsufficientStock() {
        inventoryResponse.setProductQuantity(0);
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class))).thenReturn(inventoryResponse);

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(order));
    }

    @Test
    void testCreateOrderWalletFailed() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class))).thenReturn(inventoryResponse);
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST)).when(restTemplate).put(contains("wallet"), any());

        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(order));
    }

    @Test
    void testCreateOrderReduceStockFailed() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class))).thenReturn(inventoryResponse);
        // Put pertama (wallet) sukses, put kedua (reduce-stock) gagal
        doNothing().when(restTemplate).put(contains("wallet"), any());
        doThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR)).when(restTemplate).put(contains("reduce-stock"), any());

        assertThrows(RuntimeException.class, () -> orderService.createOrder(order));
    }

    @Test
    void testFindAll() {
        when(orderRepository.findAll()).thenReturn(Arrays.asList(order));
        assertEquals(1, orderService.findAllOrders().size());
    }

    @Test
    void testFindById() {
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        assertNotNull(orderService.findOrderById("order-1"));
        assertNull(orderService.findOrderById("kosong"));
    }

    @Test
    void testUpdateStatusSuccess() {
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenReturn(order);

        Order result = orderService.updateOrderStatus("order-1", "SHIPPED");
        assertEquals(OrderStatus.SHIPPED, result.getStatus());
    }

    @Test
    void testUpdateStatusInvalid() {
        when(orderRepository.findById(anyString())).thenReturn(Optional.of(order));
        assertThrows(IllegalArgumentException.class, () -> orderService.updateOrderStatus("order-1", "NGASAL"));
    }

    @Test
    void testUpdateOrderStatus_OrderNotFound() {
        when(orderRepository.findById("id-ngawur")).thenReturn(Optional.empty());
        Order result = orderService.updateOrderStatus("id-ngawur", "SHIPPED");
        assertNull(result);
    }

    @Test
    void testCreateOrder_ProductResponseNull() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
                .thenReturn(null);
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(order)
        );
        assertEquals("Stok barang tidak mencukupi!", exception.getMessage());
    }

    @Test
    void testCreateOrder_ProductIdNull() {
        order.setProductId(null);
        order.setUserId("user-123");
        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(order)
        );
        assertEquals("Product ID dan User ID tidak boleh kosong", exception.getMessage());
    }

    @Test
    void testCreateOrder_UserIdNull() {
        order.setProductId("prod-123");
        order.setUserId(null);

        Exception exception = assertThrows(IllegalArgumentException.class, () ->
                orderService.createOrder(order)
        );
        assertEquals("Product ID dan User ID tidak boleh kosong", exception.getMessage());
    }
}