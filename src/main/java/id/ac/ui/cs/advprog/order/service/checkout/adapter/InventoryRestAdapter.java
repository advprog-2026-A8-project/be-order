package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.service.checkout.InventoryGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class InventoryRestAdapter implements InventoryGateway {
    private static final String UPDATE_PATH = "update";
    private static final String RESERVE_PATH = "reserve";
    private static final String DEFAULT_STRING = "";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_ID = "X-User-Id";

    private final RestTemplate restTemplate;

    @Value("${order.inventory.url}")
    private String inventoryUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${order.inventory.internal-role:ADMIN}")
    private String inventoryInternalRole;

    @Value("${order.inventory.internal-user-id:order-service}")
    private String inventoryInternalUserId;

    @Override
    public InventoryResponse getProduct(String productId) {
        validateRetryConfiguration();
        return getProductWithRetry(productId);
    }

    private InventoryResponse getProductWithRetry(String productId) {
        String productUrl = buildProductUrl(productId);

        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restTemplate.getForObject(productUrl, InventoryResponse.class);
            } catch (HttpClientErrorException e) {
                throw new IllegalArgumentException("Produk tidak ditemukan di Inventory!", e);
            } catch (ResourceAccessException e) {
                lastTransientError = e;
            }
        }
        throw new IllegalStateException("Gagal mengakses Inventory service (timeout/transient).", lastTransientError);
    }

    @Override
    public void reserveStock(String productId, int quantity) {
        validateRetryConfiguration();
        String reserveStockUrl = buildReserveStockUrl(productId, quantity);
        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.postForObject(reserveStockUrl, null, String.class);
                return;
            } catch (HttpClientErrorException e) {
                throw new IllegalStateException("Stok inventory tidak mencukupi.", e);
            } catch (ResourceAccessException e) {
                lastTransientError = e;
            }
        }
        throw new IllegalStateException("Gagal mengakses Inventory service saat reserve stock.", lastTransientError);
    }

    @Override
    public void releaseStock(String productId, int quantity) {
        validateRetryConfiguration();
        InventoryResponse currentProduct = getProductWithRetry(productId);
        int updatedStock = resolveStock(currentProduct) + quantity;
        String updateProductUrl = buildUpdateProductUrl(productId);
        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.exchange(
                        updateProductUrl,
                        HttpMethod.PUT,
                        buildUpdateRequest(currentProduct, updatedStock),
                        Void.class
                );
                return;
            } catch (HttpClientErrorException e) {
                throw new IllegalStateException("Gagal mengurangi stok inventory.", e);
            } catch (ResourceAccessException e) {
                lastTransientError = e;
            }
        }
        throw new IllegalStateException("Gagal mengakses Inventory service saat reduce stock.", lastTransientError);
    }

    private int resolveStock(InventoryResponse product) {
        Integer stock = product.getProductQuantity();
        return stock == null ? 0 : stock;
    }

    private double resolvePrice(InventoryResponse product) {
        Double price = product.getPrice();
        return price == null ? 0.0 : price;
    }

    private Map<String, Object> buildUpdatePayload(InventoryResponse product, int updatedStock) {
        return Map.of(
                "name", product.getProductName(),
                "description", resolveDescription(product),
                "price", resolvePrice(product),
                "stock", updatedStock,
                "jastiperId", resolveJastiperId(product)
        );
    }

    private HttpEntity<Map<String, Object>> buildUpdateRequest(InventoryResponse product, int updatedStock) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_USER_ROLE, resolveNullableString(inventoryInternalRole));
        headers.set(HEADER_USER_ID, resolveNullableString(inventoryInternalUserId));
        return new HttpEntity<>(buildUpdatePayload(product, updatedStock), headers);
    }

    private String resolveDescription(InventoryResponse product) {
        String description = product.getDescription();
        return resolveNullableString(description);
    }

    private String resolveJastiperId(InventoryResponse product) {
        String jastiperId = product.getJastiperId();
        return resolveNullableString(jastiperId);
    }

    private String resolveNullableString(String value) {
        return value == null ? DEFAULT_STRING : value;
    }

    private String buildProductUrl(String productId) {
        return UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId)
                .toUriString();
    }

    private String buildUpdateProductUrl(String productId) {
        return UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(UPDATE_PATH, productId)
                .toUriString();
    }

    private String buildReserveStockUrl(String productId, int quantity) {
        return UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId, RESERVE_PATH)
                .queryParam("quantity", quantity)
                .toUriString();
    }

    private void validateRetryConfiguration() {
        if (maxAttempts <= 0) {
            throw new IllegalStateException("Konfigurasi order.http.retry.max-attempts harus lebih dari 0.");
        }
    }
}
