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
    private static final String INVENTORY_PROP =
            "order.inventory.internal-authorization=${ORDER_INVENTORY_INTERNAL_AUTHORIZATION:}";
    private static final String WALLET_GRPC_INTERNAL_TOKEN_PROP =
            "order.wallet.grpc.internal-token=${GRPC_SERVER_INTERNAL_TOKEN:}";

    @Test
    void applicationPropertiesShouldDefineInternalAuthProperties() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/application.properties"));

        assertTrue(content.contains(PROFILE_PROP));
        assertTrue(content.contains(INVENTORY_PROP));
        assertTrue(content.contains(WALLET_GRPC_INTERNAL_TOKEN_PROP));
    }

    @Test
    void envExampleShouldProvideInternalAuthVariables() throws IOException {
        List<String> lines = Files.readAllLines(Path.of("env.example"));

        assertTrue(lines.stream().anyMatch(line ->
                line.startsWith("ORDER_PROFILE_INTERNAL_AUTHORIZATION=Bearer ")));
        assertTrue(lines.stream().anyMatch(line ->
                line.startsWith("ORDER_INVENTORY_INTERNAL_AUTHORIZATION=Bearer ")));
        assertTrue(lines.stream().anyMatch(line ->
                line.startsWith("GRPC_SERVER_INTERNAL_TOKEN=")));
    }
}
