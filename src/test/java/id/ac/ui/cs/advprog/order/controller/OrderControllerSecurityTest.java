package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.config.SecurityConfig;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.security.OrderAccessGuard;
import id.ac.ui.cs.advprog.order.security.RestAccessDeniedHandler;
import id.ac.ui.cs.advprog.order.security.RestAuthenticationEntryPoint;
import id.ac.ui.cs.advprog.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(OrderController.class)
@Import({
        SecurityConfig.class,
        OrderAccessGuard.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkoutEndpointShouldRejectJastiperRole() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", "prod-1",
                                "userId", "user-1",
                                "jastiperId", "jastiper-1",
                                "jumlah", 1,
                                "alamatPengiriman", "Jakarta"
                        )))
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/orders/checkout"));
    }

    @Test
    void checkoutEndpointShouldRejectDifferentTitiperIdentity() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", "prod-1",
                                "userId", "user-2",
                                "jastiperId", "jastiper-1",
                                "jumlah", 1,
                                "alamatPengiriman", "Jakarta"
                        )))
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void checkoutEndpointShouldAllowOwnerTitiper() throws Exception {
        when(orderService.createOrder(org.mockito.ArgumentMatchers.any(Order.class), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new Order());

        mockMvc.perform(post("/api/orders/checkout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", "prod-1",
                                "userId", "user-1",
                                "jastiperId", "jastiper-1",
                                "jumlah", 1,
                                "alamatPengiriman", "Jakarta"
                        )))
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void adminEndpointShouldRejectNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/orders/admin/active")
                        .with(user("titiper-1").roles("TITIPER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/orders/admin/active"));
    }

    @Test
    void checkoutEndpointShouldReturnStructuredUnauthorizedWhenNoToken() throws Exception {
        mockMvc.perform(post("/api/orders/checkout")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "productId", "prod-1",
                                "userId", "user-1",
                                "jastiperId", "jastiper-1",
                                "jumlah", 1,
                                "alamatPengiriman", "Jakarta"
                        ))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/orders/checkout"));
    }

    @Test
    void adminEndpointShouldAllowAdminRole() throws Exception {
        when(orderService.findAdminActiveOrders()).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/admin/active")
                        .with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void titiperHistoryEndpointShouldRejectJastiperRole() throws Exception {
        mockMvc.perform(get("/api/orders/titiper/user-1/history")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void titiperHistoryEndpointShouldAllowTitiperRole() throws Exception {
        when(orderService.findTitiperOrderHistory("user-1")).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/titiper/user-1/history")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void titiperHistoryEndpointShouldRejectDifferentTitiperIdentity() throws Exception {
        mockMvc.perform(get("/api/orders/titiper/user-2/history")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void titiperActiveEndpointShouldRejectDifferentTitiperIdentity() throws Exception {
        mockMvc.perform(get("/api/orders/titiper/user-2/active")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void titiperActiveEndpointShouldAllowOwnerTitiper() throws Exception {
        when(orderService.findTitiperActiveOrders("user-1")).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/titiper/user-1/active")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void jastiperTodoEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        mockMvc.perform(get("/api/orders/jastiper/jastiper-2/todo")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jastiperTodoEndpointShouldAllowOwnerJastiper() throws Exception {
        when(orderService.findJastiperTodoOrders("jastiper-1")).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/jastiper/jastiper-1/todo")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void jastiperProcessingEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        mockMvc.perform(get("/api/orders/jastiper/jastiper-2/processing")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jastiperCompletedEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        mockMvc.perform(get("/api/orders/jastiper/jastiper-2/completed")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelEndpointShouldRejectTitiperRole() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/cancel")
                        .param("jastiperId", "jastiper-1")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelEndpointShouldAllowJastiperRole() throws Exception {
        when(orderService.cancelOrderByJastiper("order-1", "jastiper-1")).thenReturn(new Order());

        mockMvc.perform(post("/api/orders/order-1/cancel")
                        .param("jastiperId", "jastiper-1")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void cancelEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/cancel")
                        .param("jastiperId", "jastiper-2")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ratingEndpointShouldRejectJastiperRole() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/rating")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "user-1",
                                "jastiperRating", 5,
                                "productRating", 4
                        )))
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ratingEndpointShouldAllowTitiperRole() throws Exception {
        when(orderService.submitOrderRating("order-1", "user-1", 5, 4)).thenReturn(new Order());

        mockMvc.perform(post("/api/orders/order-1/rating")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "user-1",
                                "jastiperRating", 5,
                                "productRating", 4
                        )))
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void ratingEndpointShouldRejectDifferentTitiperIdentity() throws Exception {
        mockMvc.perform(post("/api/orders/order-1/rating")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "user-2",
                                "jastiperRating", 5,
                                "productRating", 4
                        )))
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }
}
