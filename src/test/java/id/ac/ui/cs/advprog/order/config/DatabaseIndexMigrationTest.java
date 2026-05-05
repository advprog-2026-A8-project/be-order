package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertTrue;

@JdbcTest
class DatabaseIndexMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldHaveCompositeIndexesForOwnerStatusQueries() {
        List<String> indexNames = jdbcTemplate.queryForList(
                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME = 'ORDERS'",
                String.class
        );

        List<String> normalized = indexNames.stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();

        assertTrue(
                normalized.contains("IDX_ORDERS_USER_STATUS"),
                "Index IDX_ORDERS_USER_STATUS wajib ada untuk query user+status."
        );
        assertTrue(
                normalized.contains("IDX_ORDERS_JASTIPER_STATUS"),
                "Index IDX_ORDERS_JASTIPER_STATUS wajib ada untuk query jastiper+status."
        );
    }
}
