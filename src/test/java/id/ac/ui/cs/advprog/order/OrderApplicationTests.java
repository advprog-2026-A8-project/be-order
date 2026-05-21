package id.ac.ui.cs.advprog.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class OrderApplicationTests {
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void contextLoads() {
        //
    }

    @Test
    void testMain() {
        assertNotNull(OrderApplication.class);
    }
}
