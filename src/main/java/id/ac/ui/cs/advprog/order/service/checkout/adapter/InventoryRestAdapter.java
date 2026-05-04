package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.service.checkout.InventoryGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

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
        InventoryResponse currentProduct = getProduct(productId);
        Integer currentStock = currentProduct.getProductQuantity();
        int updatedStock = (currentStock == null ? 0 : currentStock) - quantity;

        String updateProductUrl = UriComponentsBuilder.fromUriString(inventoryUrl)
                .pathSegment("update", productId)
                .toUriString();

        Map<String, Object> payload = Map.of(
                "name", currentProduct.getProductName(),
                "description", "",
                "price", currentProduct.getPrice() == null ? 0.0 : currentProduct.getPrice(),
                "stock", updatedStock,
                "jastiperId", ""
        );

        try {
            restTemplate.put(updateProductUrl, payload);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("Gagal mengurangi stok inventory.", e);
        }
    }
}
