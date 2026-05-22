package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;

public interface InventoryGateway {
    InventoryResponse getProduct(String productId);
    void reserveStock(String productId, int quantity);
    void reserveStock(String productId, int quantity, String authorizationHeader);
    void releaseStock(String productId, int quantity);
}
