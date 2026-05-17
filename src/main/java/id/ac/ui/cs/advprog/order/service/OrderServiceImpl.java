package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.exception.InvalidOrderTransitionException;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.state.OrderStateMachine;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import id.ac.ui.cs.advprog.order.service.checkout.InventoryGateway;
import id.ac.ui.cs.advprog.order.service.checkout.CheckoutLockManager;
import id.ac.ui.cs.advprog.order.service.checkout.OrderCheckoutFacade;
import id.ac.ui.cs.advprog.order.service.checkout.VoucherGateway;
import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private static final String MESSAGE_INVALID_RATING_RANGE = "Rating harus berada pada rentang 1-5";
    private static final String MESSAGE_INVALID_JASTIPER_ID = "ID jastiper harus UUID valid untuk update statistik.";
    private static final String CANCEL_REFUND_IDEMPOTENCY_PREFIX = "cancel-refund-";
    private static final String CANCEL_DEBIT_ROLLBACK_IDEMPOTENCY_PREFIX = "cancel-rollback-debit-";
    private static final String CANCEL_RESERVE_ROLLBACK_REASON =
            "Cancel gagal; stok sudah direlease sehingga dicoba reserve ulang.";
    private static final String CANCEL_VOUCHER_ROLLBACK_REASON =
            "Cancel gagal; voucher sudah direstore sehingga dicoba dipakai ulang.";
    private static final String MESSAGE_CANCEL_COMPENSATION_PARTIAL_FAILURE =
            "Cancel order gagal diproses penuh karena ada kegagalan kompensasi.";
    private static final String MESSAGE_PROFILE_SYNC_FAILED =
            "Rating tersimpan, tetapi sinkronisasi statistik profile gagal. Perlu sinkronisasi manual.";
    private static final String MESSAGE_PROFILE_SYNC_FAILED_ROLLBACK =
            "Sinkronisasi rating ke profile gagal dan rollback rating lokal juga gagal.";
    private static final String RATING_LOCK_PREFIX = "rating-lock:";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "status", "totalAmount", "userId", "jastiperId");


    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final OrderCheckoutFacade orderCheckoutFacade;
    private final WalletGateway walletGateway;
    private final InventoryGateway inventoryGateway;
    private final VoucherGateway voucherGateway;
    private final ProfileGateway profileGateway;
    private final CheckoutLockManager checkoutLockManager;

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
            OrderStatus newStatus;
            try {
                newStatus = OrderStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
            if (!orderStateMachine.isValidTransition(order.getStatus(), newStatus)) {
                throw new InvalidOrderTransitionException(
                        String.format("Invalid transition: %s -> %s", order.getStatus(), newStatus)
                );
            }
            order.setStatus(newStatus);
            return orderRepository.save(order);
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

        return checkoutLockManager.withProductLock(order.getProductId(), () -> {
            RuntimeException refundFailure = null;
            RuntimeException releaseFailure = null;
            RuntimeException restoreFailure = null;
            boolean refundSucceeded = false;
            boolean releaseSucceeded = false;
            boolean voucherRestoreSucceeded = false;

            double refundAmount = order.getTotalAmount() == null ? 0.0 : order.getTotalAmount();
            try {
                walletGateway.refund(
                        order.getUserId(),
                        order.getId(),
                        refundAmount,
                        CANCEL_REFUND_IDEMPOTENCY_PREFIX + order.getId()
                );
                refundSucceeded = true;
            } catch (RuntimeException ex) {
                refundFailure = ex;
            }

            try {
                inventoryGateway.releaseStock(order.getProductId(), order.getJumlah());
                releaseSucceeded = true;
            } catch (RuntimeException ex) {
                releaseFailure = ex;
            }

            if (Boolean.TRUE.equals(order.getVoucherApplied())
                    && order.getVoucherCode() != null
                    && !order.getVoucherCode().isBlank()) {
                try {
                    voucherGateway.restoreVoucher(order.getVoucherCode().trim());
                    voucherRestoreSucceeded = true;
                } catch (RuntimeException ex) {
                    restoreFailure = ex;
                }
            }

            if (refundFailure != null || releaseFailure != null || restoreFailure != null) {
                RuntimeException voucherRollbackFailure = null;
                RuntimeException inventoryRollbackFailure = null;
                RuntimeException walletRollbackFailure = null;

                if (voucherRestoreSucceeded) {
                    try {
                        voucherGateway.useVoucher(order.getVoucherCode().trim());
                    } catch (RuntimeException ex) {
                        voucherRollbackFailure = ex;
                        log.warn(CANCEL_VOUCHER_ROLLBACK_REASON, ex);
                    }
                }

                if (releaseSucceeded) {
                    try {
                        inventoryGateway.reserveStock(order.getProductId(), order.getJumlah());
                    } catch (RuntimeException ex) {
                        inventoryRollbackFailure = ex;
                        log.warn(CANCEL_RESERVE_ROLLBACK_REASON, ex);
                    }
                }

                if (refundSucceeded) {
                    try {
                        walletGateway.debit(
                                order.getUserId(),
                                order.getId(),
                                refundAmount,
                                CANCEL_DEBIT_ROLLBACK_IDEMPOTENCY_PREFIX + order.getId()
                        );
                    } catch (RuntimeException ex) {
                        walletRollbackFailure = ex;
                    }
                }

                IllegalStateException wrapped = new IllegalStateException(MESSAGE_CANCEL_COMPENSATION_PARTIAL_FAILURE);
                if (refundFailure != null) {
                    wrapped.addSuppressed(refundFailure);
                }
                if (releaseFailure != null) {
                    wrapped.addSuppressed(releaseFailure);
                }
                if (restoreFailure != null) {
                    wrapped.addSuppressed(restoreFailure);
                }
                if (voucherRollbackFailure != null) {
                    wrapped.addSuppressed(voucherRollbackFailure);
                }
                if (inventoryRollbackFailure != null) {
                    wrapped.addSuppressed(inventoryRollbackFailure);
                }
                if (walletRollbackFailure != null) {
                    wrapped.addSuppressed(walletRollbackFailure);
                }
                throw wrapped;
            }

            order.setStatus(OrderStatus.CANCELLED);
            return orderRepository.save(order);
        });
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
        String ratingLockKey = RATING_LOCK_PREFIX + orderId;
        return checkoutLockManager.withIdempotencyLock(ratingLockKey, () -> {
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
            validateJastiperUuid(order.getJastiperId());

            order.setJastiperRating(jastiperRating);
            order.setProductRating(productRating);
            order.setRatingSubmitted(true);
            Order savedOrder = orderRepository.save(order);

            try {
                profileGateway.submitRating(
                        savedOrder.getId(),
                        savedOrder.getUserId(),
                        savedOrder.getJastiperId(),
                        savedOrder.getProductId(),
                        jastiperRating,
                        productRating
                );
            } catch (RuntimeException ex) {
                log.error(
                        "submit_rating_profile_sync_failed orderId={} userId={} jastiperId={}",
                        savedOrder.getId(),
                        savedOrder.getUserId(),
                        savedOrder.getJastiperId(),
                        ex
                );
                throw rollbackRatingAndBuildException(savedOrder, ex);
            }

            return savedOrder;
        });
    }

    private IllegalStateException rollbackRatingAndBuildException(Order savedOrder, RuntimeException profileSyncFailure) {
        savedOrder.setJastiperRating(null);
        savedOrder.setProductRating(null);
        savedOrder.setRatingSubmitted(false);
        try {
            orderRepository.save(savedOrder);
            return new IllegalStateException(MESSAGE_PROFILE_SYNC_FAILED, profileSyncFailure);
        } catch (RuntimeException rollbackFailure) {
            IllegalStateException wrapped = new IllegalStateException(MESSAGE_PROFILE_SYNC_FAILED_ROLLBACK, profileSyncFailure);
            wrapped.addSuppressed(rollbackFailure);
            return wrapped;
        }
    }

    private void validateRatingRange(int jastiperRating, int productRating) {
        if (jastiperRating < 1 || jastiperRating > 5 || productRating < 1 || productRating > 5) {
            throw new IllegalArgumentException(MESSAGE_INVALID_RATING_RANGE);
        }
    }

    private void validateJastiperUuid(String jastiperId) {
        if (jastiperId == null || jastiperId.isBlank()) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID);
        }
        try {
            UUID.fromString(jastiperId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID, ex);
        }
    }
}
