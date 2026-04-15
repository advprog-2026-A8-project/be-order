package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.exception.InvalidOrderTransitionException;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.state.OrderStateMachine;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.service.checkout.OrderCheckoutFacade;
import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final OrderCheckoutFacade orderCheckoutFacade;
    private final WalletGateway walletGateway;

    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.PAID,
            OrderStatus.PURCHASED,
            OrderStatus.SHIPPED
    );

    @Override
    public Order createOrder(Order order) {
        return orderCheckoutFacade.checkout(order);
    }

    @Override
    public List<Order> findAllOrders() {
        return orderRepository.findAll();
    }

    @Override
    public Order findOrderById(String id) {
        return orderRepository.findById(id).orElse(null);
    }

    @Override
    public Order updateOrderStatus(String id, String status) {
        Order order = findOrderById(id);
        if (order != null) {
            try {
                OrderStatus newStatus = OrderStatus.valueOf(status.toUpperCase());
                if (!orderStateMachine.isValidTransition(order.getStatus(), newStatus)) {
                    throw new InvalidOrderTransitionException(
                            String.format("Invalid transition: %s -> %s", order.getStatus(), newStatus)
                    );
                }
                order.setStatus(newStatus);
                return orderRepository.save(order);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
        }
        return null;
    }

    @Override
    public Order cancelOrderByJastiper(String id, String jastiperId) {
        Order order = findOrderById(id);
        if (order == null) {
            return null;
        }
        if (!jastiperId.equals(order.getJastiperId())) {
            throw new IllegalArgumentException("Jastiper tidak berhak membatalkan order ini");
        }
        if (!orderStateMachine.isValidTransition(order.getStatus(), OrderStatus.CANCELLED)) {
            throw new InvalidOrderTransitionException(
                    String.format("Invalid transition: %s -> %s", order.getStatus(), OrderStatus.CANCELLED)
            );
        }

        double refundAmount = order.getTotalAmount() == null ? 0.0 : order.getTotalAmount();
        walletGateway.refund(order.getUserId(), refundAmount);
        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Override
    public List<Order> findTitiperActiveOrders(String userId) {
        return orderRepository.findByUserIdAndStatusIn(userId, ACTIVE_STATUSES);
    }

    @Override
    public List<Order> findAdminActiveOrders() {
        return orderRepository.findByStatusIn(ACTIVE_STATUSES);
    }
}
