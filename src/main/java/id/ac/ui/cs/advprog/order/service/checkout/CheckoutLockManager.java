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
        String normalizedProductId = normalizeKey(productId, MESSAGE_PRODUCT_ID_REQUIRED);
        return productLocks.computeIfAbsent(normalizedProductId, key -> new ReentrantLock());
    }

    public ReentrantLock getLockForIdempotencyKey(String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeKey(idempotencyKey, MESSAGE_IDEMPOTENCY_KEY_REQUIRED);
        return idempotencyLocks.computeIfAbsent(normalizedIdempotencyKey, key -> new ReentrantLock());
    }

    private String normalizeKey(String key, String message) {
        if (key == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = key.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
