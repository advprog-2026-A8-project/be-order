package id.ac.ui.cs.advprog.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        order.setJastiperId("jastiper-1");
        order.setJumlah(2);
        order.setAlamatPengiriman("Jakarta");
        order.setStatus(OrderStatus.PENDING);
    }

    @Test
    void testCheckoutSuccess() throws Exception {
        when(orderService.createOrder(any(Order.class), anyString())).thenReturn(order);
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", "idem-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(order)))
                .andExpect(status().isOk());

        verify(orderService).createOrder(any(Order.class), eq("idem-1"));
    }

    @Test
    void testCheckoutWithoutIdempotencyHeader() throws Exception {
        when(orderService.createOrder(any(Order.class), isNull())).thenReturn(order);

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(order)))
                .andExpect(status().isOk());
    }

    @Test
    void testCheckoutShouldTrimIdempotencyKeyBeforeCallingService() throws Exception {
        when(orderService.createOrder(any(Order.class), eq("idem-1"))).thenReturn(order);

        mockMvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", "  idem-1  ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(order)))
                .andExpect(status().isOk());

        verify(orderService).createOrder(any(Order.class), eq("idem-1"));
    }

    @Test
    void testCheckoutValidationErrorShouldReturnStructuredError() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "productId", "",
                                "userId", "user-def",
                                "jumlah", 0,
                                "alamatPengiriman", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/orders/checkout"));
    }

    @Test
    void testCheckoutShouldRejectBlankIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", "   ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(order)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/orders/checkout"));

        verify(orderService, never()).createOrder(any(Order.class), anyString());
    }

    @Test
    void testCheckoutShouldRejectTooLongIdempotencyKey() throws Exception {
        String tooLongKey = "k".repeat(129);
        mockMvc.perform(post("/api/orders/checkout")
                        .header("Idempotency-Key", tooLongKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(order)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/orders/checkout"));

        verify(orderService, never()).createOrder(any(Order.class), anyString());
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/orders/order-123/status"));
    }

    @Test
    void testCancelByJastiperSuccess() throws Exception {
        order.setStatus(OrderStatus.CANCELLED);
        when(orderService.cancelOrderByJastiper("order-123", "jastiper-1")).thenReturn(order);

        mockMvc.perform(post("/api/orders/order-123/cancel").param("jastiperId", "jastiper-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void testCancelByJastiperNotFound() throws Exception {
        when(orderService.cancelOrderByJastiper("order-123", "jastiper-1")).thenReturn(null);

        mockMvc.perform(post("/api/orders/order-123/cancel").param("jastiperId", "jastiper-1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testCancelByJastiperBadRequest() throws Exception {
        when(orderService.cancelOrderByJastiper("order-123", "jastiper-1"))
                .thenThrow(new IllegalArgumentException("forbidden"));

        mockMvc.perform(post("/api/orders/order-123/cancel").param("jastiperId", "jastiper-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/orders/order-123/cancel"));
    }

    @Test
    void testGetTitiperActiveOrders() throws Exception {
        when(orderService.findTitiperActiveOrders("user-def")).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/api/orders/titiper/user-def/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-123"));
    }

    @Test
    void testGetAdminActiveOrders() throws Exception {
        when(orderService.findAdminActiveOrders()).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/api/orders/admin/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-123"));
    }

    @Test
    void testGetAdminOrderSummary() throws Exception {
        Map<String, Long> statusCounts = new HashMap<>();
        statusCounts.put("PAID", 2L);
        statusCounts.put("COMPLETED", 1L);
        AdminOrderSummaryResponse summary = new AdminOrderSummaryResponse(
                3L, 2L, 1L, 0L, statusCounts
        );
        when(orderService.getAdminOrderSummary()).thenReturn(summary);

        mockMvc.perform(get("/api/orders/admin/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(3))
                .andExpect(jsonPath("$.activeOrders").value(2))
                .andExpect(jsonPath("$.completedOrders").value(1))
                .andExpect(jsonPath("$.cancelledOrders").value(0))
                .andExpect(jsonPath("$.statusCounts.PAID").value(2))
                .andExpect(jsonPath("$.statusCounts.COMPLETED").value(1));
    }

    @Test
    void testGetAdminOrdersByStatus() throws Exception {
        order.setStatus(OrderStatus.PAID);
        when(orderService.findAdminOrdersByStatus("PAID")).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/api/orders/admin/by-status").param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PAID"));
    }

    @Test
    void testGetAdminOrdersByStatusPaged() throws Exception {
        order.setStatus(OrderStatus.PAID);
        when(orderService.findAdminOrdersByStatusPaged("PAID", 0, 5))
                .thenReturn(new PageImpl<>(Arrays.asList(order)));

        mockMvc.perform(get("/api/orders/admin/by-status/paged")
                        .param("status", "PAID")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-123"))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));
    }

    @Test
    void testGetAdminOrdersByStatusPagedWithSorting() throws Exception {
        order.setStatus(OrderStatus.PAID);
        when(orderService.findAdminOrdersByStatusPaged("PAID", 0, 5, "totalAmount", "desc"))
                .thenReturn(new PageImpl<>(Arrays.asList(order)));

        mockMvc.perform(get("/api/orders/admin/by-status/paged")
                        .param("status", "PAID")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sortBy", "totalAmount")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-123"));
    }

    @Test
    void testGetAdminOrdersByStatusPagedShouldRejectNegativePage() throws Exception {
        when(orderService.findAdminOrdersByStatusPaged("PAID", -1, 5))
                .thenThrow(new IllegalArgumentException("Page tidak boleh negatif"));

        mockMvc.perform(get("/api/orders/admin/by-status/paged")
                        .param("status", "PAID")
                        .param("page", "-1")
                        .param("size", "5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    void testGetAdminOrdersByStatusPagedShouldRejectNonPositiveSize() throws Exception {
        when(orderService.findAdminOrdersByStatusPaged("PAID", 0, 0))
                .thenThrow(new IllegalArgumentException("Size harus lebih dari 0"));

        mockMvc.perform(get("/api/orders/admin/by-status/paged")
                        .param("status", "PAID")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    void testGetAdminActiveOrdersPaged() throws Exception {
        order.setStatus(OrderStatus.PAID);
        when(orderService.findAdminActiveOrdersPaged(0, 10, "id", "asc"))
                .thenReturn(new PageImpl<>(Arrays.asList(order)));

        mockMvc.perform(get("/api/orders/admin/active/paged")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-123"))
                .andExpect(jsonPath("$.content[0].status").value("PAID"));
    }

    @Test
    void testGetAdminActiveOrdersPagedWithSorting() throws Exception {
        order.setStatus(OrderStatus.PAID);
        when(orderService.findAdminActiveOrdersPaged(0, 10, "totalAmount", "desc"))
                .thenReturn(new PageImpl<>(Arrays.asList(order)));

        mockMvc.perform(get("/api/orders/admin/active/paged")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "totalAmount")
                        .param("direction", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("order-123"));
    }

    @Test
    void testGetAdminActiveOrdersPagedWithSortingShouldRejectInvalidSortBy() throws Exception {
        when(orderService.findAdminActiveOrdersPaged(0, 10, "createdAt", "asc"))
                .thenThrow(new IllegalArgumentException("SortBy tidak valid"));

        mockMvc.perform(get("/api/orders/admin/active/paged")
                        .param("page", "0")
                        .param("size", "10")
                        .param("sortBy", "createdAt")
                        .param("direction", "asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    void testGetAdminActiveOrdersPagedShouldRejectNegativePage() throws Exception {
        when(orderService.findAdminActiveOrdersPaged(-1, 10, "id", "asc"))
                .thenThrow(new IllegalArgumentException("Page tidak boleh negatif"));

        mockMvc.perform(get("/api/orders/admin/active/paged")
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    void testGetAdminActiveOrdersPagedShouldRejectNonPositiveSize() throws Exception {
        when(orderService.findAdminActiveOrdersPaged(0, 0, "id", "asc"))
                .thenThrow(new IllegalArgumentException("Size harus lebih dari 0"));

        mockMvc.perform(get("/api/orders/admin/active/paged")
                        .param("page", "0")
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"));
    }

    @Test
    void testGetTitiperHistoryOrders() throws Exception {
        when(orderService.findTitiperOrderHistory("user-def")).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/api/orders/titiper/user-def/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-123"));
    }

    @Test
    void testGetJastiperTodoOrders() throws Exception {
        when(orderService.findJastiperTodoOrders("jastiper-1")).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/api/orders/jastiper/jastiper-1/todo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-123"));
    }

    @Test
    void testGetJastiperProcessingOrders() throws Exception {
        when(orderService.findJastiperProcessingOrders("jastiper-1")).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/api/orders/jastiper/jastiper-1/processing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-123"));
    }

    @Test
    void testGetJastiperCompletedOrders() throws Exception {
        when(orderService.findJastiperCompletedOrders("jastiper-1")).thenReturn(Arrays.asList(order));

        mockMvc.perform(get("/api/orders/jastiper/jastiper-1/completed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("order-123"));
    }

    @Test
    void testSubmitRatingSuccess() throws Exception {
        order.setStatus(OrderStatus.COMPLETED);
        order.setJastiperRating(5);
        order.setProductRating(4);
        when(orderService.submitOrderRating("order-123", "user-def", 5, 4)).thenReturn(order);

        mockMvc.perform(post("/api/orders/order-123/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "userId", "user-def",
                                "jastiperRating", 5,
                                "productRating", 4
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jastiperRating").value(5))
                .andExpect(jsonPath("$.productRating").value(4));
    }

    @Test
    void testSubmitRatingNotFound() throws Exception {
        when(orderService.submitOrderRating("order-123", "user-def", 5, 4)).thenReturn(null);

        mockMvc.perform(post("/api/orders/order-123/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "userId", "user-def",
                                "jastiperRating", 5,
                                "productRating", 4
                        ))))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSubmitRatingBadRequest() throws Exception {
        when(orderService.submitOrderRating("order-123", "user-def", 5, 4))
                .thenThrow(new IllegalArgumentException("invalid"));

        mockMvc.perform(post("/api/orders/order-123/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "userId", "user-def",
                                "jastiperRating", 5,
                                "productRating", 4
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/orders/order-123/rating"));
    }

    @Test
    void testSubmitRatingValidationErrorShouldReturnStructuredError() throws Exception {
        mockMvc.perform(post("/api/orders/order-123/rating")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(Map.of(
                                "userId", "",
                                "jastiperRating", 0,
                                "productRating", 7
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/orders/order-123/rating"));
    }
}
