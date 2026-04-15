package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;

public interface InventoryGateway {
    InventoryResponse getProduct(String productId);
    void reduceStock(String productId, int quantity);
}
