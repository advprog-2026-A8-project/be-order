package id.ac.ui.cs.advprog.order.model.state;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void shouldAllowValidTransitions() {
        assertTrue(stateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.PURCHASED));
        assertTrue(stateMachine.isValidTransition(OrderStatus.PURCHASED, OrderStatus.SHIPPED));
        assertTrue(stateMachine.isValidTransition(OrderStatus.SHIPPED, OrderStatus.COMPLETED));
        assertTrue(stateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.CANCELLED));
    }

    @Test
    void shouldRejectInvalidTransitions() {
        assertFalse(stateMachine.isValidTransition(OrderStatus.PAID, OrderStatus.COMPLETED));
        assertFalse(stateMachine.isValidTransition(OrderStatus.COMPLETED, OrderStatus.CANCELLED));
        assertFalse(stateMachine.isValidTransition(OrderStatus.CANCELLED, OrderStatus.PAID));
    }
}
