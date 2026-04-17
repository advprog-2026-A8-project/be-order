package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class InventoryRestAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private InventoryRestAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "inventoryUrl", "http://localhost:8081/api/products");
        ReflectionTestUtils.setField(adapter, "maxAttempts", 2);
    }

    @Test
    void getProductSuccess() {
        InventoryResponse response = new InventoryResponse();
        response.setProductId("p1");
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class))).thenReturn(response);

        InventoryResponse result = adapter.getProduct("p1");

        assertEquals("p1", result.getProductId());
    }

    @Test
    void getProductNotFoundShouldThrow() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        assertThrows(IllegalArgumentException.class, () -> adapter.getProduct("p1"));
    }

    @Test
    void getProductShouldRetryOnTransientFailure() {
        InventoryResponse response = new InventoryResponse();
        response.setProductId("p1");
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
                .thenThrow(new ResourceAccessException("timeout"))
                .thenReturn(response);

        InventoryResponse result = adapter.getProduct("p1");

        assertEquals("p1", result.getProductId());
        verify(restTemplate, times(2)).getForObject(anyString(), eq(InventoryResponse.class));
    }

    @Test
    void getProductShouldThrowWhenTransientFailureExhausted() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"));

        assertThrows(IllegalStateException.class, () -> adapter.getProduct("p1"));
    }

    @Test
    void reduceStockSuccess() {
        adapter.reduceStock("p1", 2);
        verify(restTemplate).put(anyString(), eq((Object) null));
    }

    @Test
    void reduceStockFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).put(anyString(), eq((Object) null));

        assertThrows(IllegalStateException.class, () -> adapter.reduceStock("p1", 2));
    }

    @Test
    void reduceStockShouldRetryOnTransientFailure() {
        doThrow(new ResourceAccessException("timeout"))
                .doNothing()
                .when(restTemplate).put(anyString(), eq((Object) null));

        adapter.reduceStock("p1", 2);

        verify(restTemplate, times(2)).put(anyString(), eq((Object) null));
    }

    @Test
    void reduceStockShouldThrowWhenTransientFailureExhausted() {
        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).put(anyString(), eq((Object) null));

        assertThrows(IllegalStateException.class, () -> adapter.reduceStock("p1", 2));
    }
}
