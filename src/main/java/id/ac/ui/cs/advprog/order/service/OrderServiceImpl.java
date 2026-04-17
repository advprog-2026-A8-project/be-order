package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.exception.InvalidOrderTransitionException;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.state.OrderStateMachine;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.service.checkout.OrderCheckoutFacade;
import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
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
    private final ProfileGateway profileGateway;

    private static final List<OrderStatus> ACTIVE_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.PAID,
            OrderStatus.PURCHASED,
            OrderStatus.SHIPPED
    );
    private static final List<OrderStatus> JASTIPER_TODO_STATUSES = List.of(OrderStatus.PAID);
    private static final List<OrderStatus> JASTIPER_PROCESSING_STATUSES = List.of(OrderStatus.PURCHASED, OrderStatus.SHIPPED);
    private static final List<OrderStatus> JASTIPER_COMPLETED_STATUSES = List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED);

    @Override
    public Order createOrder(Order order) {
        return createOrder(order, null);
    }

    @Override
    public Order createOrder(Order order, String idempotencyKey) {
        return orderCheckoutFacade.checkout(order, idempotencyKey);
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
    public List<Order> findTitiperOrderHistory(String userId) {
        return orderRepository.findByUserId(userId);
    }

    @Override
    public List<Order> findJastiperTodoOrders(String jastiperId) {
        return orderRepository.findByJastiperIdAndStatusIn(jastiperId, JASTIPER_TODO_STATUSES);
    }

    @Override
    public List<Order> findJastiperProcessingOrders(String jastiperId) {
        return orderRepository.findByJastiperIdAndStatusIn(jastiperId, JASTIPER_PROCESSING_STATUSES);
    }

    @Override
    public List<Order> findJastiperCompletedOrders(String jastiperId) {
        return orderRepository.findByJastiperIdAndStatusIn(jastiperId, JASTIPER_COMPLETED_STATUSES);
    }

    @Override
    public List<Order> findAdminActiveOrders() {
        return orderRepository.findByStatusIn(ACTIVE_STATUSES);
    }

    @Override
    public Order submitOrderRating(String orderId, String userId, int jastiperRating, int productRating) {
        Order order = findOrderById(orderId);
        if (order == null) {
            return null;
        }
        if (!userId.equals(order.getUserId())) {
            throw new IllegalArgumentException("User tidak berhak memberi rating untuk order ini");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new IllegalStateException("Rating hanya dapat diberikan setelah order completed");
        }
        if (Boolean.TRUE.equals(order.getRatingSubmitted())) {
            throw new IllegalStateException("Rating untuk order ini sudah pernah dikirim");
        }
        validateRatingRange(jastiperRating, productRating);

        profileGateway.submitRating(
                order.getId(),
                order.getUserId(),
                order.getJastiperId(),
                order.getProductId(),
                jastiperRating,
                productRating
        );

        order.setJastiperRating(jastiperRating);
        order.setProductRating(productRating);
        order.setRatingSubmitted(true);
        return orderRepository.save(order);
    }

    private void validateRatingRange(int jastiperRating, int productRating) {
        if (jastiperRating < 1 || jastiperRating > 5 || productRating < 1 || productRating > 5) {
            throw new IllegalArgumentException("Rating harus berada pada rentang 1-5");
        }
    }
}
