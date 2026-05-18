package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InternalAuthorizationConfigContractTest {

    private static final String PROFILE_PROP =
            "order.profile.internal-authorization=${ORDER_PROFILE_INTERNAL_AUTHORIZATION:}";
    private static final String WALLET_PROP =
            "order.wallet.internal-authorization=${ORDER_WALLET_INTERNAL_AUTHORIZATION:Bearer ";

    @Test
    void applicationPropertiesShouldDefineInternalAuthProperties() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/application.properties"));

        assertTrue(content.contains(PROFILE_PROP));
        assertTrue(content.contains(WALLET_PROP));
    }

    @Test
    void envExampleShouldProvideBearerInternalAuthVariables() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("env.example"));

        assertTrue(lines.stream().anyMatch(line ->
                line.startsWith("ORDER_PROFILE_INTERNAL_AUTHORIZATION=Bearer ")));
        assertTrue(lines.stream().anyMatch(line ->
                line.startsWith("ORDER_WALLET_INTERNAL_AUTHORIZATION=Bearer ")));
    }
}
