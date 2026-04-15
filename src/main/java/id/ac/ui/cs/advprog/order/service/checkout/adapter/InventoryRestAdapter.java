package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.service.checkout.InventoryGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@RequiredArgsConstructor
public class InventoryRestAdapter implements InventoryGateway {

    private final RestTemplate restTemplate;

    @Value("${order.inventory.url}")
    private String inventoryUrl;

    @Override
    public InventoryResponse getProduct(String productId) {
        String productUrl = UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId)
                .toUriString();

        try {
            return restTemplate.getForObject(productUrl, InventoryResponse.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Produk tidak ditemukan di Inventory!", e);
        }
    }

    @Override
    public void reduceStock(String productId, int quantity) {
        String reduceStockUrl = UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment(productId, "reduce-stock")
                .queryParam("quantity", quantity)
                .toUriString();

        try {
            restTemplate.put(reduceStockUrl, null);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Gagal mengurangi stok inventory.", e);
        }
    }
}
