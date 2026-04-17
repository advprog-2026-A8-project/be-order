package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.service.checkout.InventoryGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class InventoryRestAdapter implements InventoryGateway {

    private final RestTemplate restTemplate;

    @Value("${order.inventory.url}")
    private String inventoryUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Override
    public InventoryResponse getProduct(String productId) {
        String productUrl = UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId)
                .toUriString();

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
    public void reduceStock(String productId, int quantity) {
        String reduceStockUrl = UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId, "reduce-stock")
                .queryParam("quantity", quantity)
                .toUriString();

        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.put(reduceStockUrl, null);
                return;
            } catch (HttpClientErrorException e) {
                throw new IllegalStateException("Gagal mengurangi stok inventory.", e);
            } catch (ResourceAccessException e) {
                lastTransientError = e;
            }
        }
        throw new IllegalStateException("Gagal mengakses Inventory service saat reduce stock.", lastTransientError);
    }

}
