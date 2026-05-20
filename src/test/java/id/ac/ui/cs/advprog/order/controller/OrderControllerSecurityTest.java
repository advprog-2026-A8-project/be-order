package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.config.SecurityConfig;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private OrderRepository orderRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void checkoutEndpointShouldRejectJastiperRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
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
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
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
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
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
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/orders/admin/active")
                        .with(user("titiper-1").roles("TITIPER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.path").value("/api/orders/admin/active"));
    }

    @Test
    void checkoutEndpointShouldReturnStructuredUnauthorizedWhenNoToken() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
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
    void actuatorPrometheusShouldNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminEndpointShouldAllowAdminRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderService.findAdminActiveOrders()).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/admin/active")
                        .with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void titiperHistoryEndpointShouldRejectJastiperRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/orders/titiper/user-1/history")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void titiperHistoryEndpointShouldAllowTitiperRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderService.findTitiperOrderHistory("user-1")).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/titiper/user-1/history")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void titiperHistoryEndpointShouldRejectDifferentTitiperIdentity() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/orders/titiper/user-2/history")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void titiperActiveEndpointShouldRejectDifferentTitiperIdentity() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/orders/titiper/user-2/active")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void titiperActiveEndpointShouldAllowOwnerTitiper() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderService.findTitiperActiveOrders("user-1")).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/titiper/user-1/active")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void jastiperTodoEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/orders/jastiper/jastiper-2/todo")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jastiperTodoEndpointShouldAllowOwnerJastiper() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderService.findJastiperTodoOrders("jastiper-1")).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders/jastiper/jastiper-1/todo")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void jastiperProcessingEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/orders/jastiper/jastiper-2/processing")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void jastiperCompletedEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/orders/jastiper/jastiper-2/completed")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelEndpointShouldRejectTitiperRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/orders/order-1/cancel")
                        .param("jastiperId", "jastiper-1")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelEndpointShouldAllowJastiperRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderService.cancelOrderByJastiper("order-1", "jastiper-1")).thenReturn(new Order());

        mockMvc.perform(post("/api/orders/order-1/cancel")
                        .param("jastiperId", "jastiper-1")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void cancelEndpointShouldRejectDifferentJastiperIdentity() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        mockMvc.perform(post("/api/orders/order-1/cancel")
                        .param("jastiperId", "jastiper-2")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void ratingEndpointShouldRejectJastiperRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
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
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
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
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
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

    @Test
    void updateStatusEndpointShouldRejectTitiperRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/orders/order-1/status")
                        .param("status", "PAID")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatusEndpointShouldAllowAdminRole() throws Exception {
        lenient().when(orderRepository.findById(anyString())).thenReturn(Optional.empty());
        when(orderService.updateOrderStatus("order-1", "PAID")).thenReturn(new Order());

        mockMvc.perform(patch("/api/orders/order-1/status")
                        .param("status", "PAID")
                        .with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatusEndpointShouldAllowOwnerJastiper() throws Exception {
        Order order = new Order();
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderService.updateOrderStatus("order-1", "PURCHASED")).thenReturn(order);

        mockMvc.perform(patch("/api/orders/order-1/status")
                        .param("status", "PURCHASED")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatusEndpointShouldRejectNonOwnerJastiper() throws Exception {
        Order order = new Order();
        order.setJastiperId("jastiper-owner");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        mockMvc.perform(patch("/api/orders/order-1/status")
                        .param("status", "PURCHASED")
                        .with(user("jastiper-other").roles("JASTIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllOrdersShouldRejectNonAdmin() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(user("user-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getAllOrdersShouldAllowAdmin() throws Exception {
        when(orderService.findAllOrders()).thenReturn(List.of(new Order()));

        mockMvc.perform(get("/api/orders")
                        .with(user("admin-1").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void getOrderByIdShouldAllowOwnerTitiper() throws Exception {
        Order order = new Order();
        order.setId("order-1");
        order.setUserId("titiper-1");
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderService.findOrderById("order-1")).thenReturn(order);

        mockMvc.perform(get("/api/orders/order-1")
                        .with(user("titiper-1").roles("TITIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void getOrderByIdShouldAllowOwnerJastiper() throws Exception {
        Order order = new Order();
        order.setId("order-1");
        order.setUserId("titiper-1");
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
        when(orderService.findOrderById("order-1")).thenReturn(order);

        mockMvc.perform(get("/api/orders/order-1")
                        .with(user("jastiper-1").roles("JASTIPER")))
                .andExpect(status().isOk());
    }

    @Test
    void getOrderByIdShouldRejectNonOwner() throws Exception {
        Order order = new Order();
        order.setId("order-1");
        order.setUserId("titiper-1");
        order.setJastiperId("jastiper-1");
        when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/order-1")
                        .with(user("other-user").roles("TITIPER")))
                .andExpect(status().isForbidden());
    }
}
