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
    private static final String WALLET_URL_KEY = "order.wallet.url";
    private static final String PROFILE_URL_KEY = "order.profile.url";

    @Test
    void shouldRequireOrderExternalServiceUrls() {
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources());

        assertNotNull(resolver.getProperty(INVENTORY_URL_KEY));
        assertNotNull(resolver.getProperty(WALLET_URL_KEY));
        assertNotNull(resolver.getProperty(PROFILE_URL_KEY));

        assertFalse(resolver.getProperty(INVENTORY_URL_KEY).isBlank());
        assertFalse(resolver.getProperty(WALLET_URL_KEY).isBlank());
        assertFalse(resolver.getProperty(PROFILE_URL_KEY).isBlank());
    }

    private MutablePropertySources propertySources() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("external", Map.of(
                INVENTORY_URL_KEY, "http://localhost:8081/api/products",
                WALLET_URL_KEY, "http://localhost:8082/wallet",
                PROFILE_URL_KEY, "http://localhost:8083/api/profile"
        )));
        return sources;
    }
}
