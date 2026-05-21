package id.ac.ui.cs.advprog.order.model.state;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;

import java.util.Set;

public class BasicOrderState implements OrderState {
    private final OrderStatus status;
    private final Set<OrderStatus> allowedTransitions;

    public BasicOrderState(OrderStatus status, Set<OrderStatus> allowedTransitions) {
        this.status = status;
        this.allowedTransitions = allowedTransitions;
    }

    @Override
    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public boolean canTransitionTo(OrderStatus nextStatus) {
        return allowedTransitions.contains(nextStatus);
    }
}
