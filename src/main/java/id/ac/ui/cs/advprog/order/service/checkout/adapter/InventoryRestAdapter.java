package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.service.checkout.InventoryGateway;
import id.ac.ui.cs.advprog.order.service.common.AdapterConfigValidator;
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

@Component
@RequiredArgsConstructor
public class InventoryRestAdapter implements InventoryGateway {
    private static final String RESERVE_PATH = "reserve";
    private static final String RELEASE_PATH = "release";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String ADAPTER_NAME = "Inventory";

    private final RestTemplate restTemplate;

    @Value("${order.inventory.url}")
    private String inventoryUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${order.inventory.internal-authorization:}")
    private String internalAuthorization;

    @Override
    public InventoryResponse getProduct(String productId) {
        AdapterConfigValidator.validateRetryMaxAttempts(maxAttempts);
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
        reserveStock(productId, quantity, null);
    }

    @Override
    public void reserveStock(String productId, int quantity, String authorizationHeader) {
        AdapterConfigValidator.validateRetryMaxAttempts(maxAttempts);
        String authorizationToken = resolveAuthorizationToken(authorizationHeader);
        String reserveStockUrl = buildReserveStockUrl(productId, quantity);
        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.exchange(
                        reserveStockUrl,
                        HttpMethod.POST,
                        buildMutationRequest(authorizationToken),
                        Void.class
                );
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
        AdapterConfigValidator.validateRetryMaxAttempts(maxAttempts);
        String authorizationToken = AdapterConfigValidator
                .validateAndNormalizeBearerToken(internalAuthorization, ADAPTER_NAME);
        String releaseStockUrl = buildReleaseStockUrl(productId, quantity);
        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.exchange(
                        releaseStockUrl,
                        HttpMethod.POST,
                        buildMutationRequest(authorizationToken),
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

    private String resolveAuthorizationToken(String authorizationHeader) {
        String preferredAuthorization = authorizationHeader;
        if (preferredAuthorization == null || preferredAuthorization.isBlank()) {
            preferredAuthorization = internalAuthorization;
        }
        return AdapterConfigValidator.validateAndNormalizeBearerToken(preferredAuthorization, ADAPTER_NAME);
    }

    private HttpEntity<Void> buildMutationRequest(String authorizationToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_AUTHORIZATION, authorizationToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(headers);
    }

    private String buildProductUrl(String productId) {
        return UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId)
                .toUriString();
    }

    private String buildReleaseStockUrl(String productId, int quantity) {
        return UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId, RELEASE_PATH)
                .queryParam("quantity", quantity)
                .toUriString();
    }

    private String buildReserveStockUrl(String productId, int quantity) {
        return UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId, RESERVE_PATH)
                .queryParam("quantity", quantity)
                .toUriString();
    }

}
