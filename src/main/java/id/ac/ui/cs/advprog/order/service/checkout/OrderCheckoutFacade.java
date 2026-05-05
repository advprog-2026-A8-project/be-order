package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.OrderIdempotency;
import id.ac.ui.cs.advprog.order.repository.OrderIdempotencyRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
public class OrderCheckoutFacade {
    private static final String MESSAGE_ORDER_NULL = "Order tidak boleh null";
    private static final String MESSAGE_PRODUCT_USER_REQUIRED = "Product ID dan User ID tidak boleh kosong";
    private static final String MESSAGE_INVALID_QUANTITY = "Jumlah pesanan harus lebih dari 0";
    private static final String MESSAGE_INVALID_PRICE = "Harga produk tidak valid";
    private static final String MESSAGE_IDEMPOTENCY_ORDER_NOT_FOUND = "Order untuk idempotency key tidak ditemukan";
    private static final String MESSAGE_IDEMPOTENCY_PAYLOAD_MISMATCH =
            "Idempotency key sudah digunakan untuk payload order yang berbeda";
    private static final String MESSAGE_INVENTORY_REDUCE_FAILED_REFUND_DONE =
            "Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Dana direfund.";
    private static final String MESSAGE_INVENTORY_REDUCE_FAILED_REFUND_FAILED =
            "Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Dana direfund gagal diproses.";

    private final InventoryGateway inventoryGateway;
    private final WalletGateway walletGateway;
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
        return performCheckout(order);
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

            Order savedOrder = performCheckout(order);
            orderIdempotencyRepository.save(new OrderIdempotency(idempotencyKey, savedOrder.getId()));
            return savedOrder;
        } finally {
            idempotencyLock.unlock();
        }
    }

    private Order performCheckout(Order order) {
        ReentrantLock lock = checkoutLockManager.getLockForProduct(order.getProductId());
        lock.lock();
        try {
            InventoryResponse product = inventoryGateway.getProduct(order.getProductId());
            if (product == null || product.getProductQuantity() < order.getJumlah()) {
                checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_INSUFFICIENT_STOCK);
                throw new IllegalArgumentException("Stok barang tidak mencukupi!");
            }

            if (product.getPrice() == null || product.getPrice() <= 0) {
                checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_INVALID_PRICE);
                throw new IllegalArgumentException(MESSAGE_INVALID_PRICE);
            }

            double totalPrice = product.getPrice() * order.getJumlah();
            try {
                walletGateway.debit(order.getUserId(), totalPrice);
            } catch (RuntimeException ex) {
                checkoutAuditLogger.logValidationFailed(CheckoutAuditReason.VALIDATION_WALLET_DEBIT_FAILED);
                throw ex;
            }
            checkoutAuditLogger.logDebitSucceeded(order.getUserId(), totalPrice);

            try {
                inventoryGateway.reduceStock(order.getProductId(), order.getJumlah());
                checkoutAuditLogger.logStockReductionSucceeded(order.getProductId(), order.getJumlah());
            } catch (RuntimeException ex) {
                throw handleInventoryReduceFailure(order, totalPrice, ex);
            }

            order.setStatus(OrderStatus.PAID);
            order.setTotalAmount(totalPrice);
            return orderRepository.save(order);
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

    private IllegalStateException handleInventoryReduceFailure(Order order, double totalPrice, RuntimeException inventoryException) {
        logRefundReason(order, totalPrice, CheckoutAuditReason.REFUND_INVENTORY_REDUCE_FAILED);

        try {
            walletGateway.refund(order.getUserId(), totalPrice);
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
}
