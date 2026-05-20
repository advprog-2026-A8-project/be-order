package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExternalServicePropertiesTest {
    private static final String INVENTORY_URL_KEY = "order.inventory.url";
    private static final String VOUCHER_URL_KEY = "order.voucher.url";
    private static final String PROFILE_URL_KEY = "order.profile.url";
    private static final String WALLET_GRPC_HOST_KEY = "order.wallet.grpc.host";
    private static final String WALLET_GRPC_PORT_KEY = "order.wallet.grpc.port";

    @Test
    void shouldRequireOrderExternalServiceUrls() {
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources());

        assertNotNull(resolver.getProperty(INVENTORY_URL_KEY));
        assertNotNull(resolver.getProperty(VOUCHER_URL_KEY));
        assertNotNull(resolver.getProperty(PROFILE_URL_KEY));
        assertNotNull(resolver.getProperty(WALLET_GRPC_HOST_KEY));
        assertNotNull(resolver.getProperty(WALLET_GRPC_PORT_KEY));

        assertFalse(resolver.getProperty(INVENTORY_URL_KEY).isBlank());
        assertFalse(resolver.getProperty(VOUCHER_URL_KEY).isBlank());
        assertFalse(resolver.getProperty(PROFILE_URL_KEY).isBlank());
        assertFalse(resolver.getProperty(WALLET_GRPC_HOST_KEY).isBlank());
        assertFalse(resolver.getProperty(WALLET_GRPC_PORT_KEY).isBlank());
    }

    private MutablePropertySources propertySources() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("external", Map.of(
                INVENTORY_URL_KEY, "http://localhost:8081/api/products",
                VOUCHER_URL_KEY, "http://localhost:7002/api/vouchers",
                PROFILE_URL_KEY, "http://localhost:8083/api/profile",
                WALLET_GRPC_HOST_KEY, "localhost",
                WALLET_GRPC_PORT_KEY, "9090"
        )));
        return sources;
    }
}
