package id.ac.ui.cs.advprog.order.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeInventoryCatalogJsonContract() throws Exception {
        String json = """
                {
                  "id": "p-1",
                  "name": "Produk A",
                  "description": "Desc",
                  "price": 15000.0,
                  "stock": 8,
                  "jastiperId": "j-1"
                }
                """;

        InventoryResponse response = objectMapper.readValue(json, InventoryResponse.class);

        assertEquals("p-1", response.getProductId());
        assertEquals("Produk A", response.getProductName());
        assertEquals("Desc", response.getDescription());
        assertEquals(15000.0, response.getPrice());
        assertEquals(8, response.getProductQuantity());
        assertEquals("j-1", response.getJastiperId());
    }
}
