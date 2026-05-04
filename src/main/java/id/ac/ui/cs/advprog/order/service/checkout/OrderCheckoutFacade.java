package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.OrderIdempotency;
import id.ac.ui.cs.advprog.order.repository.OrderIdempotencyRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
public class OrderCheckoutFacade {
    private static final String MESSAGE_ORDER_NULL = "Order tidak boleh null";
    private static final String MESSAGE_PRODUCT_USER_REQUIRED = "Product ID dan User ID tidak boleh kosong";
    private static final String MESSAGE_INVALID_QUANTITY = "Jumlah pesanan harus lebih dari 0";

    private final InventoryGateway inventoryGateway;
    private final WalletGateway walletGateway;
    private final OrderRepository orderRepository;
    private final OrderIdempotencyRepository orderIdempotencyRepository;
    private final CheckoutLockManager checkoutLockManager;

    public Order checkout(Order order) {
        return checkout(order, null);
    }

    public Order checkout(Order order, String idempotencyKey) {
        validateOrderRequest(order);

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
                return orderRepository.findById(existingRecord.getOrderId())
                        .orElseThrow(() -> new IllegalStateException("Order untuk idempotency key tidak ditemukan"));
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
                throw new IllegalArgumentException("Stok barang tidak mencukupi!");
            }

            double totalPrice = product.getPrice() * order.getJumlah();
            walletGateway.debit(order.getUserId(), totalPrice);

            try {
                inventoryGateway.reduceStock(order.getProductId(), order.getJumlah());
            } catch (RuntimeException ex) {
                walletGateway.refund(order.getUserId(), totalPrice);
                throw new IllegalStateException("Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Dana direfund.", ex);
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
            throw new IllegalArgumentException(MESSAGE_ORDER_NULL);
        }

        if (isBlank(order.getProductId()) || isBlank(order.getUserId())) {
            throw new IllegalArgumentException(MESSAGE_PRODUCT_USER_REQUIRED);
        }

        if (order.getJumlah() == null || order.getJumlah() <= 0) {
            throw new IllegalArgumentException(MESSAGE_INVALID_QUANTITY);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
