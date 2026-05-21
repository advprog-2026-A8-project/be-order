package id.ac.ui.cs.advprog.order.model.state;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasicOrderStateTest {

    @Test
    void shouldExposeStatusAndValidateTransition() {
        BasicOrderState state = new BasicOrderState(
                OrderStatus.PAID,
                Set.of(OrderStatus.PURCHASED, OrderStatus.CANCELLED)
        );

        assertEquals(OrderStatus.PAID, state.getStatus());
        assertTrue(state.canTransitionTo(OrderStatus.PURCHASED));
        assertFalse(state.canTransitionTo(OrderStatus.COMPLETED));
    }
}
