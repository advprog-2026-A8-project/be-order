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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveForeignKeyFromOrderIdempotencyToOrders() {
        List<String> fkNames = jdbcTemplate.queryForList(
                """
                SELECT CONSTRAINT_NAME
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE TABLE_NAME = 'ORDER_IDEMPOTENCY'
                  AND CONSTRAINT_TYPE = 'FOREIGN KEY'
                """,
                String.class
        );

        List<String> normalized = fkNames.stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();

        assertTrue(
                normalized.contains("FK_ORDER_IDEMPOTENCY_ORDER_ID"),
                "Foreign key FK_ORDER_IDEMPOTENCY_ORDER_ID wajib ada."
        );
    }
}
