package id.ac.ui.cs.advprog.order.model.state;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;

public interface OrderState {
    OrderStatus getStatus();
    boolean canTransitionTo(OrderStatus nextStatus);
}
