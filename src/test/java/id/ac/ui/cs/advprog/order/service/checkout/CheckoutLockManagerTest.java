package id.ac.ui.cs.advprog.order.service.checkout;

import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CheckoutLockManagerTest {

    @Test
    void shouldReturnSameLockForSameProduct() {
        CheckoutLockManager manager = new CheckoutLockManager();

        ReentrantLock lock1 = manager.getLockForProduct("p1");
        ReentrantLock lock2 = manager.getLockForProduct("p1");

        assertNotNull(lock1);
        assertSame(lock1, lock2);
    }
}
