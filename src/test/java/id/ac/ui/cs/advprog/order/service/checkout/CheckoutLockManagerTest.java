package id.ac.ui.cs.advprog.order.service.checkout;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void shouldNormalizeModeWhenBlankToLocal() {
        CheckoutLockManager manager = new CheckoutLockManager(
                mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class),
                "   "
        );

        String result = manager.withIdempotencyLock("idem-1", () -> "ok");
        assertEquals("ok", result);
    }

    @Test
    void shouldExecuteCriticalSectionWithPostgresAdvisoryLock() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jdbcTemplate.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(null);

        CheckoutLockManager manager = new CheckoutLockManager(jdbcTemplate, txManager, "postgres-advisory");

        String result = manager.withProductLock("p1", () -> "locked");

        assertEquals("locked", result);
        verify(jdbcTemplate).execute(anyString(), any(PreparedStatementCallback.class));
        verify(txManager).commit(status);
    }

    @Test
    void shouldThrowWhenPostgresAdvisoryLockCannotBeAcquired() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jdbcTemplate.execute(anyString(), any(PreparedStatementCallback.class)))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        CheckoutLockManager manager = new CheckoutLockManager(jdbcTemplate, txManager, "postgres-advisory");

        assertThrows(IllegalStateException.class, () -> manager.withProductLock("p1", () -> "ok"));
    }

    @Test
    void shouldRejectNullResultFromCriticalSectionInPostgresMode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus status = mock(TransactionStatus.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        when(jdbcTemplate.execute(anyString(), any(PreparedStatementCallback.class))).thenReturn(null);

        CheckoutLockManager manager = new CheckoutLockManager(jdbcTemplate, txManager, "postgres-advisory");

        assertThrows(NullPointerException.class, () -> manager.withIdempotencyLock("idem-1", () -> null));
    }

    private CheckoutLockManager buildLocalManager() {
        return new CheckoutLockManager(
                mock(JdbcTemplate.class),
                mock(PlatformTransactionManager.class),
                "local"
        );
    }
}
