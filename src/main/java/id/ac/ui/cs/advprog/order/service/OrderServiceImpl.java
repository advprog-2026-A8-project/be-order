package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.exception.InvalidOrderTransitionException;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.state.OrderStateMachine;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.service.checkout.OrderCheckoutFacade;
import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private static final String MESSAGE_INVALID_RATING_RANGE = "Rating harus berada pada rentang 1-5";
    private static final String MESSAGE_INVALID_JASTIPER_ID = "ID jastiper tidak valid untuk update statistik.";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "status", "totalAmount", "userId", "jastiperId");


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
    public Page<Order> findAdminActiveOrdersPaged(int page, int size) {
        return findAdminActiveOrdersPaged(page, size, "id", "asc");
    }

    @Override
    public Page<Order> findAdminActiveOrdersPaged(int page, int size, String sortBy, String direction) {
        validatePagination(page, size);
        Sort sort = buildSort(sortBy, direction);
        return orderRepository.findByStatusIn(ACTIVE_STATUSES, PageRequest.of(page, size, sort));
    }

    @Override
    public List<Order> findAdminOrdersByStatus(String status) {
        try {
            OrderStatus parsedStatus = parseOrderStatus(status);
            return orderRepository.findByStatusIn(List.of(parsedStatus));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
    }

    @Override
    public Page<Order> findAdminOrdersByStatusPaged(String status, int page, int size) {
        return findAdminOrdersByStatusPaged(status, page, size, "id", "asc");
    }

    @Override
    public Page<Order> findAdminOrdersByStatusPaged(String status, int page, int size, String sortBy, String direction) {
        validatePagination(page, size);
        OrderStatus parsedStatus;
        try {
            parsedStatus = parseOrderStatus(status);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status: " + status);
        }
        Sort sort = buildSort(sortBy, direction);
        return orderRepository.findByStatusIn(List.of(parsedStatus), PageRequest.of(page, size, sort));
    }

    @Override
    public AdminOrderSummaryResponse getAdminOrderSummary() {
        List<Order> orders = orderRepository.findAll();
        Map<String, Long> statusCounts = groupStatusCounts(orders);

        long totalOrders = orders.size();
        long activeOrders = countOrders(orders, order -> ACTIVE_STATUSES.contains(order.getStatus()));
        long completedOrders = countOrders(orders, order -> order.getStatus() == OrderStatus.COMPLETED);
        long cancelledOrders = countOrders(orders, order -> order.getStatus() == OrderStatus.CANCELLED);

        return new AdminOrderSummaryResponse(
                totalOrders,
                activeOrders,
                completedOrders,
                cancelledOrders,
                statusCounts
        );
    }

    private Map<String, Long> groupStatusCounts(List<Order> orders) {
        return orders.stream()
                .collect(Collectors.groupingBy(
                        order -> order.getStatus().name(),
                        Collectors.counting()
                ));
    }

    private long countOrders(List<Order> orders, Predicate<Order> predicate) {
        return orders.stream().filter(predicate).count();
    }

    private OrderStatus parseOrderStatus(String status) {
        return OrderStatus.valueOf(status.trim().toUpperCase());
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page tidak boleh negatif");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size harus lebih dari 0");
        }
    }

    private Sort buildSort(String sortBy, String direction) {
        String normalizedSortBy = (sortBy == null || sortBy.isBlank()) ? "id" : sortBy.trim();
        String normalizedDirection = (direction == null || direction.isBlank()) ? "asc" : direction.trim().toLowerCase();

        if (!ALLOWED_SORT_FIELDS.contains(normalizedSortBy)) {
            throw new IllegalArgumentException("SortBy tidak valid");
        }
        if (!normalizedDirection.equals("asc") && !normalizedDirection.equals("desc")) {
            throw new IllegalArgumentException("Direction harus asc atau desc");
        }

        Sort.Direction sortDirection = normalizedDirection.equals("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(sortDirection, normalizedSortBy);
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
        validateNumericJastiperId(order.getJastiperId());

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
            throw new IllegalArgumentException(MESSAGE_INVALID_RATING_RANGE);
        }
    }

    private void validateNumericJastiperId(String jastiperId) {
        if (jastiperId == null || jastiperId.isBlank()) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID);
        }
        try {
            Long.parseLong(jastiperId);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID, ex);
        }
    }
}
