package id.ac.ui.cs.advprog.order.service.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class CheckoutLockManagerTest {

    @Test
    void shouldExecuteProductCriticalSection() {
        CheckoutLockManager manager = buildLocalManager();
        String result = manager.withProductLock("p1", () -> "ok");
        assertEquals("ok", result);
    }

    @Test
    void shouldExecuteIdempotencyCriticalSection() {
        CheckoutLockManager manager = buildLocalManager();
        Integer result = manager.withIdempotencyLock("idem-1", () -> 42);
        assertEquals(42, result);
    }

    @Test
    void shouldRejectNullOrBlankProductId() {
        CheckoutLockManager manager = buildLocalManager();

        assertThrows(IllegalArgumentException.class, () -> manager.withProductLock(null, () -> "ok"));
        assertThrows(IllegalArgumentException.class, () -> manager.withProductLock("", () -> "ok"));
        assertThrows(IllegalArgumentException.class, () -> manager.withProductLock("   ", () -> "ok"));
    }

    @Test
    void shouldRejectNullOrBlankIdempotencyKey() {
        CheckoutLockManager manager = buildLocalManager();

        assertThrows(IllegalArgumentException.class, () -> manager.withIdempotencyLock(null, () -> "ok"));
        assertThrows(IllegalArgumentException.class, () -> manager.withIdempotencyLock("", () -> "ok"));
        assertThrows(IllegalArgumentException.class, () -> manager.withIdempotencyLock("   ", () -> "ok"));
    }

    @Test
    void shouldRejectNullCriticalSectionForProduct() {
        CheckoutLockManager manager = buildLocalManager();
        assertThrows(IllegalArgumentException.class, () -> manager.withProductLock("p1", null));
    }

    @Test
    void shouldRejectNullCriticalSectionForIdempotency() {
        CheckoutLockManager manager = buildLocalManager();
        assertThrows(IllegalArgumentException.class, () -> manager.withIdempotencyLock("idem-1", null));
    }

    @Test
    void shouldNormalizeModeWhenNull() {
        CheckoutLockManager manager = new CheckoutLockManager(
                mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class),
                null
        );

        String result = manager.withProductLock("p1", () -> "ok");
        assertNotNull(result);
    }

    private CheckoutLockManager buildLocalManager() {
        return new CheckoutLockManager(
                mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class),
                "local"
        );
    }
}
