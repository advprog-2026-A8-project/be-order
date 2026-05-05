package id.ac.ui.cs.advprog.order.repository;

import id.ac.ui.cs.advprog.order.model.OrderIdempotency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class OrderIdempotencyRepositoryConstraintTest {

    @Autowired
    private OrderIdempotencyRepository repository;

    @Test
    void shouldRejectDuplicateOrderIdAcrossDifferentIdempotencyKeys() {
        repository.saveAndFlush(new OrderIdempotency("idem-1", "order-100"));
        OrderIdempotency duplicateOrderId = new OrderIdempotency("idem-2", "order-100");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> repository.saveAndFlush(duplicateOrderId)
        );
    }
}
