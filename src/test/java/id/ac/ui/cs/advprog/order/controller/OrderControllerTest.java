package id.ac.ui.cs.advprog.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId("order-123");
        order.setProductId("prod-abc");
        order.setUserId("user-def");
        order.setJumlah(2);
        order.setStatus(OrderStatus.PENDING);
    }

    @Test
    void testCheckoutSuccess() throws Exception {
        when(orderService.createOrder(any(Order.class))).thenReturn(order);
        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(order)))
                .andExpect(status().isOk());
    }

    @Test
    void testGetAllOrders() throws Exception {
        when(orderService.findAllOrders()).thenReturn(Arrays.asList(order));
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-123"));
    }

    @Test
    void testGetOrderByIdFound() throws Exception {
        when(orderService.findOrderById("order-123")).thenReturn(order);
        mockMvc.perform(get("/api/orders/order-123"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetOrderByIdNotFound() throws Exception {
        when(orderService.findOrderById("ngawur")).thenReturn(null);
        mockMvc.perform(get("/api/orders/ngawur"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateStatusSuccess() throws Exception {
        order.setStatus(OrderStatus.PAID);
        when(orderService.updateOrderStatus(anyString(), anyString())).thenReturn(order);
        mockMvc.perform(patch("/api/orders/order-123/status").param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void testUpdateStatusNotFound() throws Exception {
        when(orderService.updateOrderStatus(anyString(), anyString())).thenReturn(null);
        mockMvc.perform(patch("/api/orders/null-id/status").param("status", "PAID"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUpdateStatusInvalid() throws Exception {
        when(orderService.updateOrderStatus(anyString(), anyString())).thenThrow(new IllegalArgumentException());
        mockMvc.perform(patch("/api/orders/order-123/status").param("status", "SALAH"))
                .andExpect(status().isBadRequest());
    }
}