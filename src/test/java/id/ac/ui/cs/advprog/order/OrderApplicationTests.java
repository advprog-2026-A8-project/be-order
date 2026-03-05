package id.ac.ui.cs.advprog.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class OrderApplicationTests {

    @Test
    void contextLoads() {
        //
    }

    @Test
    void testMain() {
        assertDoesNotThrow(() -> OrderApplication.main(new String[] {}));
    }
}