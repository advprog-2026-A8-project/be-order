package id.ac.ui.cs.advprog.order.controller;

import id.ac.ui.cs.advprog.order.config.SecurityConfig;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.security.OrderAccessGuard;
import id.ac.ui.cs.advprog.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, OrderAccessGuard.class})
class OrderControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void adminEndpointShouldRejectNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/orders/admin/active")
                        .with(user("titiper-1").roles("TITIPER")))
                .andExpect(status().isForbidden());
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
}
