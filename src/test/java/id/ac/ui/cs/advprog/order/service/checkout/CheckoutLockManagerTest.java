package id.ac.ui.cs.advprog.order.service.checkout;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckoutLockManagerTest {

    @Test
    void shouldReturnSameLockForSameProduct() {
        CheckoutLockManager manager = new CheckoutLockManager();

        ReentrantLock lock1 = manager.getLockForProduct("p1");
        ReentrantLock lock2 = manager.getLockForProduct("p1");

        assertNotNull(lock1);
        assertSame(lock1, lock2);
    }

    @Test
    void shouldReturnSameLockForSameIdempotencyKey() {
        CheckoutLockManager manager = new CheckoutLockManager();

        ReentrantLock lock1 = manager.getLockForIdempotencyKey("idem-1");
        ReentrantLock lock2 = manager.getLockForIdempotencyKey("idem-1");

        assertNotNull(lock1);
        assertSame(lock1, lock2);
    }

    @Test
    void shouldRejectNullOrBlankProductId() {
        CheckoutLockManager manager = new CheckoutLockManager();

        assertThrows(IllegalArgumentException.class, () -> manager.getLockForProduct(null));
        assertThrows(IllegalArgumentException.class, () -> manager.getLockForProduct(""));
        assertThrows(IllegalArgumentException.class, () -> manager.getLockForProduct("   "));
    }

    @Test
    void shouldRejectNullOrBlankIdempotencyKey() {
        CheckoutLockManager manager = new CheckoutLockManager();

        assertThrows(IllegalArgumentException.class, () -> manager.getLockForIdempotencyKey(null));
        assertThrows(IllegalArgumentException.class, () -> manager.getLockForIdempotencyKey(""));
        assertThrows(IllegalArgumentException.class, () -> manager.getLockForIdempotencyKey("   "));
    }

    @Test
    void shouldNormalizeProductLockKeyByTrim() {
        CheckoutLockManager manager = new CheckoutLockManager();

        ReentrantLock lock1 = manager.getLockForProduct("p1");
        ReentrantLock lock2 = manager.getLockForProduct("  p1  ");

        assertSame(lock1, lock2);
    }

    @Test
    void shouldNormalizeIdempotencyLockKeyByTrim() {
        CheckoutLockManager manager = new CheckoutLockManager();

        ReentrantLock lock1 = manager.getLockForIdempotencyKey("idem-1");
        ReentrantLock lock2 = manager.getLockForIdempotencyKey("  idem-1  ");

        assertSame(lock1, lock2);
    }
}
