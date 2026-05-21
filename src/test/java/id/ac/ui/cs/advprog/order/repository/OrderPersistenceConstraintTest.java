package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.model.Order;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class OrderPersistenceConstraintTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void shouldRejectWhenProductIdMissing() {
        Order order = new Order();
        order.setId("order-missing-product");
        order.setUserId("u1");
        order.setJumlah(2);
        order.setAlamatPengiriman("Jakarta");
        order.setTotalAmount(10000.0);

        assertThrows(ConstraintViolationException.class, () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void shouldRejectWhenJumlahNotPositive() {
        Order order = new Order();
        order.setId("order-invalid-jumlah");
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(0);
        order.setAlamatPengiriman("Jakarta");
        order.setTotalAmount(10000.0);

        assertThrows(ConstraintViolationException.class, () -> orderRepository.saveAndFlush(order));
    }
}
