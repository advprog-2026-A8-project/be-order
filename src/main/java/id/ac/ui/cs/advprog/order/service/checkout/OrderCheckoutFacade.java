package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.OrderIdempotency;
import id.ac.ui.cs.advprog.order.repository.OrderIdempotencyRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
public class OrderCheckoutFacade {
    private static final String MESSAGE_ORDER_NULL = "Order tidak boleh null";
    private static final String MESSAGE_PRODUCT_USER_REQUIRED = "Product ID dan User ID tidak boleh kosong";
    private static final String MESSAGE_INVALID_QUANTITY = "Jumlah pesanan harus lebih dari 0";
    private static final String MESSAGE_INVALID_PRICE = "Harga produk tidak valid";
    private static final String MESSAGE_INVALID_VOUCHER = "Voucher tidak valid atau tidak dapat digunakan.";
    private static final String MESSAGE_IDEMPOTENCY_ORDER_NOT_FOUND = "Order untuk idempotency key tidak ditemukan";
    private static final String MESSAGE_IDEMPOTENCY_PAYLOAD_MISMATCH =
            "Idempotency key sudah digunakan untuk payload order yang berbeda";
    private static final String MESSAGE_INVENTORY_REDUCE_FAILED_REFUND_DONE =
            "Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Dana direfund.";
    private static final String MESSAGE_INVENTORY_REDUCE_FAILED_REFUND_FAILED =
            "Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Dana direfund gagal diproses.";
    private static final String DEFAULT_WALLET_IDEMPOTENCY_PREFIX = "wallet-order-";
    private static final String REFUND_SUFFIX = "-refund";

    private final InventoryGateway inventoryGateway;
    private final WalletGateway walletGateway;
    private final VoucherGateway voucherGateway;
    private final OrderRepository orderRepository;
    private final OrderIdempotencyRepository orderIdempotencyRepository;
    private final CheckoutLockManager checkoutLockManager;
    private final CheckoutAuditLogger checkoutAuditLogger;

    public Order checkout(Order order) {
        return checkout(order, null);
    }

    public Order checkout(Order order, String idempotencyKey) {
        validateOrderRequest(order);
        checkoutAuditLogger.logCheckoutStarted(order, idempotencyKey);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return checkoutWithIdempotency(order, idempotencyKey.trim());
        }
        return performCheckout(order, null);
    }

    private Order checkoutWithIdempotency(Order order, String idempotencyKey) {
        ReentrantLock idempotencyLock = checkoutLockManager.getLockForIdempotencyKey(idempotencyKey);
        idempotencyLock.lock();
        try {
            OrderIdempotency existingRecord = orderIdempotencyRepository.findById(idempotencyKey).orElse(null);
            if (existingRecord != null) {
                Order existingOrder = orderRepository.findById(existingRecord.getOrderId())
                        .orElseThrow(() -> new IllegalStateException(MESSAGE_IDEMPOTENCY_ORDER_NOT_FOUND));
                if (!hasSameCheckoutPayload(existingOrder, order)) {
                    checkoutAuditLogger.logIdempotencyMismatch(idempotencyKey, existingOrder.getId());
                    throw new IllegalStateException(MESSAGE_IDEMPOTENCY_PAYLOAD_MISMATCH);
                }
                checkoutAuditLogger.logIdempotencyHit(idempotencyKey, existingOrder.getId());
                return existingOrder;
            }

            Order savedOrder = performCheckout(order, idempotencyKey);
            try {
                orderIdempotencyRepository.save(new OrderIdempotency(idempotencyKey, savedOrder.getId()));
                return savedOrder;
            } catch (DataIntegrityViolationException ex) {
                return resolveRaceWinnerOrder(idempotencyKey, order, ex);
            }
        } finally {
            idempotencyLock.unlock();
        }
    }

    private Order performCheckout(Order order, String idempotencyKey) {
        ReentrantLock lock = checkoutLockManager.getLockForProduct(order.getProductId());
        lock.lock();
        try {
            ensureOrderId(order);
            String walletIdempotencyKey = resolveWalletIdempotencyKey(order, idempotencyKey);

            InventoryResponse product = inventoryGateway.getProduct(order.getProductId());
            if (product == null || product.getProductQuantity() < order.getJumlah()) {
                checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_INSUFFICIENT_STOCK);
                throw new IllegalArgumentException("Stok barang tidak mencukupi!");
            }
            validateProductPrice(product);

            double baseTotalPrice = product.getPrice() * order.getJumlah();
            double discountAmount = resolveDiscountAmount(order.getVoucherCode(), baseTotalPrice);
            double totalPrice = Math.max(0.0, baseTotalPrice - discountAmount);
            try {
                walletGateway.ensureSufficientBalance(order.getUserId(), totalPrice);
                walletGateway.debit(order.getUserId(), order.getId(), totalPrice, walletIdempotencyKey);
            } catch (RuntimeException ex) {
                checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_WALLET_DEBIT_FAILED);
                throw ex;
            }
            checkoutAuditLogger.logDebitSucceeded(order.getUserId(), totalPrice);

            try {
                inventoryGateway.reduceStock(order.getProductId(), order.getJumlah());
                checkoutAuditLogger.logStockReductionSucceeded(order.getProductId(), order.getJumlah());
            } catch (RuntimeException ex) {
                throw handleInventoryReduceFailure(order, totalPrice, walletIdempotencyKey, ex);
            }

            order.setStatus(OrderStatus.PAID);
            order.setTotalAmount(totalPrice);
            Order savedOrder = orderRepository.save(order);
            tryUseVoucher(order.getVoucherCode());
            return savedOrder;
        } finally {
            lock.unlock();
        }
    }

    private void validateOrderRequest(Order order) {
        if (order == null) {
            checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_ORDER_NULL);
            throw new IllegalArgumentException(MESSAGE_ORDER_NULL);
        }

        if (isBlank(order.getProductId()) || isBlank(order.getUserId())) {
            checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_MISSING_PRODUCT_OR_USER);
            throw new IllegalArgumentException(MESSAGE_PRODUCT_USER_REQUIRED);
        }

        if (order.getJumlah() == null || order.getJumlah() <= 0) {
            checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_QUANTITY);
            throw new IllegalArgumentException(MESSAGE_INVALID_QUANTITY);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean hasSameCheckoutPayload(Order existingOrder, Order incomingOrder) {
        return Objects.equals(existingOrder.getProductId(), incomingOrder.getProductId())
                && Objects.equals(existingOrder.getUserId(), incomingOrder.getUserId())
                && Objects.equals(existingOrder.getJastiperId(), incomingOrder.getJastiperId())
                && Objects.equals(existingOrder.getJumlah(), incomingOrder.getJumlah())
                && Objects.equals(existingOrder.getAlamatPengiriman(), incomingOrder.getAlamatPengiriman());
    }

    private void validateProductPrice(InventoryResponse product) {
        if (product.getPrice() == null || product.getPrice() <= 0) {
            checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_PRICE);
            throw new IllegalArgumentException(MESSAGE_INVALID_PRICE);
        }
    }

    private double resolveDiscountAmount(String voucherCode, double baseTotalPrice) {
        if (isBlank(voucherCode)) {
            return 0.0;
        }
        try {
            return voucherGateway.validateDiscount(voucherCode.trim(), baseTotalPrice);
        } catch (IllegalArgumentException ex) {
            checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_VOUCHER);
            throw new IllegalArgumentException(MESSAGE_INVALID_VOUCHER, ex);
        }
    }

    private void tryUseVoucher(String voucherCode) {
        if (isBlank(voucherCode)) {
            return;
        }
        try {
            voucherGateway.useVoucher(voucherCode.trim());
        } catch (RuntimeException ex) {
            checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VOUCHER_USE_FAILED_AFTER_CHECKOUT);
        }
    }

    private IllegalStateException handleInventoryReduceFailure(
            Order order,
            double totalPrice,
            String walletIdempotencyKey,
            RuntimeException inventoryException
    ) {
        logRefundReason(order, totalPrice, CheckoutAuditReason.REFUND_INVENTORY_REDUCE_FAILED);

        try {
            walletGateway.refund(
                    order.getUserId(),
                    order.getId(),
                    totalPrice,
                    walletIdempotencyKey + REFUND_SUFFIX
            );
            return new IllegalStateException(
                    MESSAGE_INVENTORY_REDUCE_FAILED_REFUND_DONE,
                    inventoryException
            );
        } catch (RuntimeException refundEx) {
            logRefundReason(order, totalPrice, CheckoutAuditReason.REFUND_COMPENSATION_FAILED);
            IllegalStateException wrapped = new IllegalStateException(
                    MESSAGE_INVENTORY_REDUCE_FAILED_REFUND_FAILED,
                    inventoryException
            );
            wrapped.addSuppressed(refundEx);
            return wrapped;
        }
    }

    private void logRefundReason(Order order, double totalPrice, String reason) {
        checkoutAuditLogger.logRefundTriggered(order.getUserId(), totalPrice, reason);
    }

    private Order resolveRaceWinnerOrder(String idempotencyKey, Order incomingOrder, DataIntegrityViolationException ex) {
        OrderIdempotency raceWinnerRecord = orderIdempotencyRepository.findById(idempotencyKey)
                .orElseThrow(() -> buildIdempotencyRaceResolutionException(ex));
        Order raceWinnerOrder = orderRepository.findById(raceWinnerRecord.getOrderId())
                .orElseThrow(() -> new IllegalStateException(MESSAGE_IDEMPOTENCY_ORDER_NOT_FOUND));
        ensureSamePayloadForRaceWinner(idempotencyKey, incomingOrder, raceWinnerOrder);
        checkoutAuditLogger.logIdempotencyHit(idempotencyKey, raceWinnerOrder.getId());
        return raceWinnerOrder;
    }

    private IllegalStateException buildIdempotencyRaceResolutionException(Throwable cause) {
        return new IllegalStateException(MESSAGE_IDEMPOTENCY_ORDER_NOT_FOUND, cause);
    }

    private void ensureSamePayloadForRaceWinner(String idempotencyKey, Order incomingOrder, Order raceWinnerOrder) {
        if (!hasSameCheckoutPayload(raceWinnerOrder, incomingOrder)) {
            checkoutAuditLogger.logIdempotencyMismatch(idempotencyKey, raceWinnerOrder.getId());
            throw new IllegalStateException(MESSAGE_IDEMPOTENCY_PAYLOAD_MISMATCH);
        }
    }

    private void ensureOrderId(Order order) {
        if (isBlank(order.getId())) {
            order.setId(UUID.randomUUID().toString());
        }
    }

    private String resolveWalletIdempotencyKey(Order order, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return DEFAULT_WALLET_IDEMPOTENCY_PREFIX + order.getId();
    }
}
