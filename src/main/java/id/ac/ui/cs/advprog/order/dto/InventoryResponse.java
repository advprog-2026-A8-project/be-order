package id.ac.ui.cs.advprog.order.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class InventoryResponse {
    @JsonProperty("id")
    private String productId;

    @JsonAlias("name")
    private String productName;

    @JsonAlias("stock")
    private Integer productQuantity;

    private Double price;

    private String description;

    private String jastiperId;
}
