package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMigrationStructureTest {

    @Test
    void shouldHaveAtLeastOneFlywayMigrationScript() throws IOException {
        Path migrationDir = Path.of("src", "main", "resources", "db", "migration");

        assertTrue(Files.exists(migrationDir), "Folder migration Flyway harus ada.");
        try (Stream<Path> paths = Files.list(migrationDir)) {
            assertTrue(paths.anyMatch(path -> path.getFileName().toString().matches("V\\d+__.*\\.sql")),
                    "Minimal harus ada satu script migration dengan format V<versi>__<name>.sql");
        }
    }
}
