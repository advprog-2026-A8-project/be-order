package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
class DatabaseForeignKeyMigrationTest {
    private static final String TABLE_ORDER_IDEMPOTENCY = "ORDER_IDEMPOTENCY";
    private static final String FOREIGN_KEY_CONSTRAINT_TYPE = "FOREIGN KEY";
    private static final String FK_ORDER_IDEMPOTENCY_ORDER_ID = "FK_ORDER_IDEMPOTENCY_ORDER_ID";
    private static final String FK_QUERY = """
            SELECT CONSTRAINT_NAME
            FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
            WHERE TABLE_NAME = ?
              AND CONSTRAINT_TYPE = ?
            """;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveForeignKeyFromOrderIdempotencyToOrders() {
        List<String> fkNames = jdbcTemplate.queryForList(
                FK_QUERY,
                String.class,
                TABLE_ORDER_IDEMPOTENCY,
                FOREIGN_KEY_CONSTRAINT_TYPE
        );

        List<String> normalized = fkNames.stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();

        assertTrue(
                normalized.contains(FK_ORDER_IDEMPOTENCY_ORDER_ID),
                "Foreign key " + FK_ORDER_IDEMPOTENCY_ORDER_ID + " wajib ada."
        );
    }
}
