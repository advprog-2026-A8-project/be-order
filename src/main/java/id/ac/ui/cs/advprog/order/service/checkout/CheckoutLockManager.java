package id.ac.ui.cs.advprog.order.service.checkout;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CheckoutLockManager {
    private final ConcurrentHashMap<String, ReentrantLock> productLocks = new ConcurrentHashMap<>();

    public ReentrantLock getLockForProduct(String productId) {
        return productLocks.computeIfAbsent(productId, key -> new ReentrantLock());
    }
}
