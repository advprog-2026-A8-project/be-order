package id.ac.ui.cs.advprog.order.dto;
import lombok.Data;

@Data
public class InventoryResponse {
    private String productId;
    private String productName;
    private Integer productQuantity;
    private Double price;
}