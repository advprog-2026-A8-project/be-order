package id.ac.ui.cs.advprog.order.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import id.ac.ui.cs.advprog.order.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestSecurityHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void authenticationEntryPointShouldReturnStructuredUnauthorized() throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/orders/checkout");

        MockHttpServletResponse response = new MockHttpServletResponse();
        entryPoint.commence(request, response, new AuthenticationException("unauthorized") {
        });

        ApiErrorResponse body = objectMapper.readValue(response.getContentAsString(), ApiErrorResponse.class);
        assertEquals(401, response.getStatus());
        assertEquals("UNAUTHORIZED", body.getCode());
        assertEquals("/api/orders/checkout", body.getPath());
    }

    @Test
    void accessDeniedHandlerShouldReturnStructuredForbidden() throws Exception {
        RestAccessDeniedHandler deniedHandler = new RestAccessDeniedHandler(objectMapper);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/api/orders/admin/active");

        MockHttpServletResponse response = new MockHttpServletResponse();
        deniedHandler.handle(request, response, new AccessDeniedException("forbidden"));

        ApiErrorResponse body = objectMapper.readValue(response.getContentAsString(), ApiErrorResponse.class);
        assertEquals(403, response.getStatus());
        assertEquals("FORBIDDEN", body.getCode());
        assertEquals("/api/orders/admin/active", body.getPath());
    }
}

