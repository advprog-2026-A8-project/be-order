package id.ac.ui.cs.advprog.order.model.state;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

@Component
public class OrderStateMachine {
    private final Map<OrderStatus, OrderState> states = new EnumMap<>(OrderStatus.class);

    public OrderStateMachine() {
        states.put(OrderStatus.PENDING, new BasicOrderState(OrderStatus.PENDING, Set.of(OrderStatus.PAID, OrderStatus.CANCELLED)));
        states.put(OrderStatus.PAID, new BasicOrderState(OrderStatus.PAID, Set.of(OrderStatus.PURCHASED, OrderStatus.CANCELLED)));
        states.put(OrderStatus.PURCHASED, new BasicOrderState(OrderStatus.PURCHASED, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED)));
        states.put(OrderStatus.SHIPPED, new BasicOrderState(OrderStatus.SHIPPED, Set.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED)));
        states.put(OrderStatus.COMPLETED, new BasicOrderState(OrderStatus.COMPLETED, Set.of()));
        states.put(OrderStatus.CANCELLED, new BasicOrderState(OrderStatus.CANCELLED, Set.of()));
    }

    public boolean isValidTransition(OrderStatus currentStatus, OrderStatus nextStatus) {
        OrderState state = states.get(currentStatus);
        if (state == null) {
            return false;
        }
        return state.canTransitionTo(nextStatus);
    }
}
