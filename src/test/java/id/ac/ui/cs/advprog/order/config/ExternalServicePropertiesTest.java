package id.ac.ui.cs.advprog.order.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExternalServicePropertiesTest {

    @Test
    void shouldRequireOrderExternalServiceUrls() {
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources());

        assertNotNull(resolver.getProperty("order.inventory.url"));
        assertNotNull(resolver.getProperty("order.wallet.url"));
        assertNotNull(resolver.getProperty("order.profile.url"));

        assertFalse(resolver.getProperty("order.inventory.url").isBlank());
        assertFalse(resolver.getProperty("order.wallet.url").isBlank());
        assertFalse(resolver.getProperty("order.profile.url").isBlank());
    }

    private MutablePropertySources propertySources() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("external", Map.of(
                "order.inventory.url", "http://localhost:8081/api/products",
                "order.wallet.url", "http://localhost:8082/wallet",
                "order.profile.url", "http://localhost:8083/api/profile"
        )));
        return sources;
    }
}
