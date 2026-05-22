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
import id.ac.ui.cs.advprog.order.service.checkout.CompensationTaskDispatcher;
import id.ac.ui.cs.advprog.order.service.checkout.VoucherGateway;
import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
import id.ac.ui.cs.advprog.order.service.rating.RatingSyncDispatcher;
import id.ac.ui.cs.advprog.order.service.summary.AdminOrderSummaryMaterializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private static final String MESSAGE_INVALID_RATING_RANGE = "Rating harus berada pada rentang 1-5";
    private static final String MESSAGE_INVALID_JASTIPER_ID = "ID jastiper wajib diisi untuk update statistik.";
    private static final String CANCEL_REFUND_IDEMPOTENCY_PREFIX = "cancel-refund-";
    private static final String CANCEL_DEBIT_ROLLBACK_IDEMPOTENCY_PREFIX = "cancel-rollback-debit-";
    private static final String CANCEL_VOUCHER_RESTORE_IDEMPOTENCY_PREFIX = "cancel-restore-voucher-";
    private static final String CANCEL_RESERVE_ROLLBACK_REASON =
            "Cancel gagal; stok sudah direlease sehingga dicoba reserve ulang.";
    private static final String CANCEL_VOUCHER_ROLLBACK_REASON =
            "Cancel gagal; voucher sudah direstore sehingga dicoba dipakai ulang.";
    private static final String MESSAGE_CANCEL_COMPENSATION_PARTIAL_FAILURE =
            "Cancel order gagal diproses penuh karena ada kegagalan kompensasi.";
    private static final String MESSAGE_RATING_SYNC_MANUAL_REQUIRED =
            "Rating tersimpan, tetapi sinkronisasi statistik profile gagal. Perlu sinkronisasi manual.";
    private static final String RATING_LOCK_PREFIX = "rating-lock:";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "status", "totalAmount", "userId", "jastiperId");


    private final OrderRepository orderRepository;
    private final OrderStateMachine orderStateMachine;
    private final OrderCheckoutFacade orderCheckoutFacade;
    private final WalletGateway walletGateway;
    private final InventoryGateway inventoryGateway;
    private final VoucherGateway voucherGateway;
    private final ProfileGateway profileGateway;
    private final RatingSyncDispatcher ratingSyncDispatcher;
    private final CheckoutLockManager checkoutLockManager;
    private final CompensationTaskDispatcher compensationTaskDispatcher;
    private final AdminOrderSummaryMaterializer adminOrderSummaryMaterializer;

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
        return createOrder(order, idempotencyKey, null);
    }

    @Override
    public Order createOrder(Order order, String idempotencyKey, String authorizationHeader) {
        return orderCheckoutFacade.checkout(order, idempotencyKey, authorizationHeader);
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
            double refundAmount = order.getTotalAmount() == null ? 0.0 : order.getTotalAmount();
            CompensationAttempt refundAttempt = tryRefund(order, refundAmount);
            CompensationAttempt releaseAttempt = tryRelease(order);
            CompensationAttempt restoreAttempt = hasRestorableVoucher(order)
                    ? tryRestoreVoucher(order)
                    : CompensationAttempt.skipped();

            if (hasFailure(refundAttempt, releaseAttempt, restoreAttempt)) {
                RollbackAttempt rollbackAttempt = rollbackCancellation(
                        order,
                        refundAmount,
                        refundAttempt,
                        releaseAttempt,
                        restoreAttempt
                );
                throw buildCompensationFailure(
                        refundAttempt,
                        releaseAttempt,
                        restoreAttempt,
                        rollbackAttempt
                );
            }

            order.setStatus(OrderStatus.CANCELLED);
            return orderRepository.save(order);
        });
    }

    private CompensationAttempt tryRefund(Order order, double refundAmount) {
        try {
            walletGateway.refund(
                    order.getUserId(),
                    order.getId(),
                    refundAmount,
                    CANCEL_REFUND_IDEMPOTENCY_PREFIX + order.getId()
            );
            return CompensationAttempt.success();
        } catch (RuntimeException ex) {
            return CompensationAttempt.failure(ex);
        }
    }

    private CompensationAttempt tryRelease(Order order) {
        try {
            inventoryGateway.releaseStock(order.getProductId(), order.getJumlah());
            return CompensationAttempt.success();
        } catch (RuntimeException ex) {
            return CompensationAttempt.failure(ex);
        }
    }

    private CompensationAttempt tryRestoreVoucher(Order order) {
        try {
            voucherGateway.restoreVoucher(
                    order.getVoucherCode().trim(),
                    CANCEL_VOUCHER_RESTORE_IDEMPOTENCY_PREFIX + order.getId()
            );
            return CompensationAttempt.success();
        } catch (RuntimeException ex) {
            return CompensationAttempt.failure(ex);
        }
    }

    private boolean hasRestorableVoucher(Order order) {
        return Boolean.TRUE.equals(order.getVoucherApplied())
                && order.getVoucherCode() != null
                && !order.getVoucherCode().isBlank();
    }

    private boolean hasFailure(CompensationAttempt refund, CompensationAttempt release, CompensationAttempt restore) {
        return refund.hasFailure() || release.hasFailure() || restore.hasFailure();
    }

    private RollbackAttempt rollbackCancellation(
            Order order,
            double refundAmount,
            CompensationAttempt refundAttempt,
            CompensationAttempt releaseAttempt,
            CompensationAttempt restoreAttempt
    ) {
        RuntimeException voucherRollbackFailure = rollbackVoucherIfNeeded(order, restoreAttempt);
        RuntimeException inventoryRollbackFailure = rollbackInventoryIfNeeded(order, releaseAttempt);
        RuntimeException walletRollbackFailure = rollbackWalletIfNeeded(order, refundAmount, refundAttempt);
        enqueueRollbackCompensationIfNeeded(
                order,
                refundAmount,
                voucherRollbackFailure,
                inventoryRollbackFailure,
                walletRollbackFailure
        );
        return new RollbackAttempt(voucherRollbackFailure, inventoryRollbackFailure, walletRollbackFailure);
    }

    private void enqueueRollbackCompensationIfNeeded(
            Order order,
            double refundAmount,
            RuntimeException voucherRollbackFailure,
            RuntimeException inventoryRollbackFailure,
            RuntimeException walletRollbackFailure
    ) {
        if (walletRollbackFailure != null) {
            RuntimeException enqueueFailure = tryEnqueueCompensation(
                    "cancel_wallet_debit",
                    () -> compensationTaskDispatcher.enqueueWalletDebit(
                            order.getId(),
                            order.getUserId(),
                            refundAmount,
                            CANCEL_DEBIT_ROLLBACK_IDEMPOTENCY_PREFIX + order.getId()
                    )
            );
            attachSuppressedIfPresent(walletRollbackFailure, enqueueFailure);
        }

        if (inventoryRollbackFailure != null) {
            RuntimeException enqueueFailure = tryEnqueueCompensation(
                    "cancel_inventory_reserve",
                    () -> compensationTaskDispatcher.enqueueInventoryReserve(
                            order.getId(),
                            order.getProductId(),
                            order.getJumlah()
                    )
            );
            attachSuppressedIfPresent(inventoryRollbackFailure, enqueueFailure);
        }

        if (voucherRollbackFailure != null && hasRestorableVoucher(order)) {
            RuntimeException enqueueFailure = tryEnqueueCompensation(
                    "cancel_voucher_use",
                    () -> compensationTaskDispatcher.enqueueVoucherUse(order.getId(), order.getVoucherCode().trim())
            );
            attachSuppressedIfPresent(voucherRollbackFailure, enqueueFailure);
        }
    }

    private RuntimeException tryEnqueueCompensation(String taskName, Runnable enqueueAction) {
        try {
            enqueueAction.run();
            return null;
        } catch (RuntimeException enqueueEx) {
            log.warn("enqueue_cancel_compensation_failed task={} reason={}", taskName, enqueueEx.getMessage(), enqueueEx);
            return enqueueEx;
        }
    }

    private void attachSuppressedIfPresent(RuntimeException target, RuntimeException suppressed) {
        if (target != null && suppressed != null) {
            target.addSuppressed(suppressed);
        }
    }

    private RuntimeException rollbackVoucherIfNeeded(Order order, CompensationAttempt restoreAttempt) {
        if (!restoreAttempt.wasSuccessful()) {
            return null;
        }
        try {
            voucherGateway.useVoucher(order.getVoucherCode().trim());
            return null;
        } catch (RuntimeException ex) {
            log.warn(CANCEL_VOUCHER_ROLLBACK_REASON, ex);
            return ex;
        }
    }

    private RuntimeException rollbackInventoryIfNeeded(Order order, CompensationAttempt releaseAttempt) {
        if (!releaseAttempt.wasSuccessful()) {
            return null;
        }
        try {
            inventoryGateway.reserveStock(order.getProductId(), order.getJumlah());
            return null;
        } catch (RuntimeException ex) {
            log.warn(CANCEL_RESERVE_ROLLBACK_REASON, ex);
            return ex;
        }
    }

    private RuntimeException rollbackWalletIfNeeded(Order order, double refundAmount, CompensationAttempt refundAttempt) {
        if (!refundAttempt.wasSuccessful()) {
            return null;
        }
        try {
            walletGateway.debit(
                    order.getUserId(),
                    order.getId(),
                    refundAmount,
                    CANCEL_DEBIT_ROLLBACK_IDEMPOTENCY_PREFIX + order.getId()
            );
            return null;
        } catch (RuntimeException ex) {
            return ex;
        }
    }

    private IllegalStateException buildCompensationFailure(
            CompensationAttempt refundAttempt,
            CompensationAttempt releaseAttempt,
            CompensationAttempt restoreAttempt,
            RollbackAttempt rollbackAttempt
    ) {
        IllegalStateException wrapped = new IllegalStateException(MESSAGE_CANCEL_COMPENSATION_PARTIAL_FAILURE);
        addSuppressedIfPresent(wrapped, refundAttempt.failure());
        addSuppressedIfPresent(wrapped, releaseAttempt.failure());
        addSuppressedIfPresent(wrapped, restoreAttempt.failure());
        addSuppressedIfPresent(wrapped, rollbackAttempt.voucherRollbackFailure());
        addSuppressedIfPresent(wrapped, rollbackAttempt.inventoryRollbackFailure());
        addSuppressedIfPresent(wrapped, rollbackAttempt.walletRollbackFailure());
        return wrapped;
    }

    private void addSuppressedIfPresent(IllegalStateException wrapped, RuntimeException failure) {
        if (failure != null) {
            wrapped.addSuppressed(failure);
        }
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
        return adminOrderSummaryMaterializer.read();
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
        return submitOrderRating(orderId, userId, jastiperRating, productRating, null);
    }

    @Override
    public Order submitOrderRating(
            String orderId,
            String userId,
            int jastiperRating,
            int productRating,
            String authorizationHeader
    ) {
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
            validateJastiperIdentity(order.getJastiperId());

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
                        savedOrder.getJastiperRating(),
                        savedOrder.getProductRating(),
                        authorizationHeader
                );
            } catch (RuntimeException profileSyncFailure) {
                try {
                    ratingSyncDispatcher.enqueue(savedOrder);
                } catch (RuntimeException enqueueFailure) {
                    IllegalStateException wrapped = new IllegalStateException(MESSAGE_RATING_SYNC_MANUAL_REQUIRED);
                    wrapped.addSuppressed(profileSyncFailure);
                    wrapped.addSuppressed(enqueueFailure);
                    throw wrapped;
                }
            }
            return savedOrder;
        });
    }

    private void validateRatingRange(int jastiperRating, int productRating) {
        if (jastiperRating < 1 || jastiperRating > 5 || productRating < 1 || productRating > 5) {
            throw new IllegalArgumentException(MESSAGE_INVALID_RATING_RANGE);
        }
    }

    private void validateJastiperIdentity(String jastiperId) {
        if (jastiperId == null || jastiperId.isBlank()) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID);
        }
    }

    private record CompensationAttempt(boolean successful, RuntimeException failure) {
        static CompensationAttempt success() {
            return new CompensationAttempt(true, null);
        }

        static CompensationAttempt failure(RuntimeException failure) {
            return new CompensationAttempt(false, failure);
        }

        static CompensationAttempt skipped() {
            return new CompensationAttempt(false, null);
        }

        boolean wasSuccessful() {
            return successful;
        }

        boolean hasFailure() {
            return failure != null;
        }
    }

    private record RollbackAttempt(
            RuntimeException voucherRollbackFailure,
            RuntimeException inventoryRollbackFailure,
            RuntimeException walletRollbackFailure
    ) {
    }
}
