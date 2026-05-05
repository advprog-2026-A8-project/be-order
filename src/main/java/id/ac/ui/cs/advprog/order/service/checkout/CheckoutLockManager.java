package id.ac.ui.cs.advprog.order.service.checkout;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CheckoutLockManager {
    private static final String MESSAGE_PRODUCT_ID_REQUIRED = "Product ID lock key tidak boleh kosong";
    private static final String MESSAGE_IDEMPOTENCY_KEY_REQUIRED = "Idempotency key lock tidak boleh kosong";

    private final ConcurrentHashMap<String, ReentrantLock> productLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> idempotencyLocks = new ConcurrentHashMap<>();

    public ReentrantLock getLockForProduct(String productId) {
        validateKey(productId, MESSAGE_PRODUCT_ID_REQUIRED);
        return productLocks.computeIfAbsent(productId, key -> new ReentrantLock());
    }

    public ReentrantLock getLockForIdempotencyKey(String idempotencyKey) {
        validateKey(idempotencyKey, MESSAGE_IDEMPOTENCY_KEY_REQUIRED);
        return idempotencyLocks.computeIfAbsent(idempotencyKey, key -> new ReentrantLock());
    }

    private void validateKey(String key, String message) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
