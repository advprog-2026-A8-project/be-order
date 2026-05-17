package id.ac.ui.cs.advprog.order.service.checkout;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class CheckoutLockManager {
    private static final String MESSAGE_PRODUCT_ID_REQUIRED = "Product ID lock key tidak boleh kosong";
    private static final String MESSAGE_IDEMPOTENCY_KEY_REQUIRED = "Idempotency key lock tidak boleh kosong";
    private static final String LOCK_MODE_LOCAL = "local";
    private static final String MESSAGE_ADVISORY_LOCK_FAILED = "Gagal mengambil advisory lock PostgreSQL untuk checkout";
    private static final int PRODUCT_LOCK_NAMESPACE = 1001;
    private static final int IDEMPOTENCY_LOCK_NAMESPACE = 1002;
    private static final String POSTGRES_XACT_LOCK_QUERY = "SELECT pg_advisory_xact_lock(?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final String lockMode;
    private final ConcurrentHashMap<String, ReentrantLock> fallbackProductLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> fallbackIdempotencyLocks = new ConcurrentHashMap<>();

    public CheckoutLockManager(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            @Value("${order.checkout.lock.mode:postgres-advisory}") String lockMode
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.lockMode = normalizeMode(lockMode);
    }

    public <T> T withProductLock(String productId, Supplier<T> criticalSection) {
        String normalizedProductId = normalizeKey(productId, MESSAGE_PRODUCT_ID_REQUIRED);
        validateCriticalSection(criticalSection);
        return executeWithLock(
                PRODUCT_LOCK_NAMESPACE,
                normalizedProductId,
                fallbackProductLocks,
                criticalSection
        );
    }

    public <T> T withIdempotencyLock(String idempotencyKey, Supplier<T> criticalSection) {
        String normalizedIdempotencyKey = normalizeKey(idempotencyKey, MESSAGE_IDEMPOTENCY_KEY_REQUIRED);
        validateCriticalSection(criticalSection);
        return executeWithLock(
                IDEMPOTENCY_LOCK_NAMESPACE,
                normalizedIdempotencyKey,
                fallbackIdempotencyLocks,
                criticalSection
        );
    }

    private <T> T executeWithLock(
            int namespace,
            String normalizedKey,
            ConcurrentHashMap<String, ReentrantLock> fallbackLocks,
            Supplier<T> criticalSection
    ) {
        if (LOCK_MODE_LOCAL.equals(lockMode)) {
            return executeWithLocalLock(normalizedKey, fallbackLocks, criticalSection);
        }
        return transactionTemplate.execute(status -> {
            acquirePostgresAdvisoryLock(namespace, normalizedKey);
            return criticalSection.get();
        });
    }

    private <T> T executeWithLocalLock(
            String normalizedKey,
            ConcurrentHashMap<String, ReentrantLock> fallbackLocks,
            Supplier<T> criticalSection
    ) {
        ReentrantLock lock = fallbackLocks.computeIfAbsent(normalizedKey, key -> new ReentrantLock());
        lock.lock();
        try {
            return criticalSection.get();
        } finally {
            lock.unlock();
        }
    }

    private void acquirePostgresAdvisoryLock(int namespace, String lockKey) {
        int hashedLockKey = lockKey.hashCode();
        try {
            jdbcTemplate.execute(
                    POSTGRES_XACT_LOCK_QUERY,
                    (PreparedStatementCallback<Void>) preparedStatement -> {
                        preparedStatement.setInt(1, namespace);
                        preparedStatement.setInt(2, hashedLockKey);
                        preparedStatement.execute();
                        return null;
                    }
            );
        } catch (DataAccessException ex) {
            throw new IllegalStateException(MESSAGE_ADVISORY_LOCK_FAILED, ex);
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null) {
            return LOCK_MODE_LOCAL;
        }
        String normalizedMode = mode.trim().toLowerCase();
        if (normalizedMode.isEmpty()) {
            return LOCK_MODE_LOCAL;
        }
        return normalizedMode;
    }

    private <T> void validateCriticalSection(Supplier<T> criticalSection) {
        if (criticalSection == null) {
            throw new IllegalArgumentException("Critical section tidak boleh null");
        }
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
