package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        ReflectionTestUtils.setField(adapter, "inventoryInternalRole", "ADMIN");
        ReflectionTestUtils.setField(adapter, "inventoryInternalUserId", "order-service");
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
    void getProductShouldRetryAndThrowWhenTransientFailureExhausted() {
        when(restTemplate.getForObject(anyString(), eq(InventoryResponse.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"));

        assertThrows(IllegalStateException.class, () -> adapter.getProduct("p1"));
        verify(restTemplate, times(2)).getForObject(anyString(), eq(InventoryResponse.class));
    }

    @Test
    void reserveStockSuccess() {
        adapter.reserveStock("p1", 2);
        verify(restTemplate).postForObject("http://localhost:8081/api/products/p1/reserve?quantity=2", null, String.class);
    }

    @Test
    void reserveStockShouldThrowWhenInsufficient() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThrows(IllegalStateException.class, () -> adapter.reserveStock("p1", 2));
    }

    @Test
    void reserveStockShouldRetryOnTransientFailure() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenReturn("ok");

        adapter.reserveStock("p1", 2);
        verify(restTemplate, times(2)).postForObject(anyString(), any(), eq(String.class));
    }

    @Test
    void reserveStockShouldThrowWhenTransientFailureExhausted() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"));

        assertThrows(IllegalStateException.class, () -> adapter.reserveStock("p1", 2));
    }

    @Test
    void releaseStockSuccessShouldPutUpdatedPayload() {
        InventoryResponse product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductName("Produk A");
        product.setDescription("Desk A");
        product.setPrice(20000.0);
        product.setProductQuantity(10);
        product.setJastiperId("j-1");

        when(restTemplate.getForObject("http://localhost:8081/api/products/p1", InventoryResponse.class))
                .thenReturn(product);

        AtomicReference<HttpEntity<?>> requestRef = new AtomicReference<>();
        doAnswer(invocation -> {
            requestRef.set(invocation.getArgument(2));
            return ResponseEntity.ok().build();
        }).when(restTemplate).exchange(
                eq("http://localhost:8081/api/products/update/p1"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        );

        adapter.releaseStock("p1", 2);

        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) requestRef.get().getBody();
        assertEquals(12, payload.get("stock"));
        assertEquals("Desk A", payload.get("description"));
        assertEquals("j-1", payload.get("jastiperId"));
        assertEquals("ADMIN", requestRef.get().getHeaders().getFirst("X-User-Role"));
        assertEquals("order-service", requestRef.get().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void releaseStockShouldRetryOnTransientFailure() {
        InventoryResponse product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductName("Produk A");
        product.setPrice(20000.0);
        product.setProductQuantity(10);
        when(restTemplate.getForObject("http://localhost:8081/api/products/p1", InventoryResponse.class))
                .thenReturn(product);

        doThrow(new ResourceAccessException("timeout"))
                .doReturn(ResponseEntity.ok().build())
                .when(restTemplate).exchange(
                        eq("http://localhost:8081/api/products/update/p1"),
                        eq(HttpMethod.PUT),
                        argThat(Objects::nonNull),
                        eq(Void.class)
                );

        adapter.releaseStock("p1", 2);
        verify(restTemplate, times(2)).exchange(
                eq("http://localhost:8081/api/products/update/p1"),
                eq(HttpMethod.PUT),
                argThat(Objects::nonNull),
                eq(Void.class)
        );
    }

    @Test
    void releaseStockShouldThrowWhenUpdateClientError() {
        InventoryResponse product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductName("Produk A");
        product.setPrice(20000.0);
        product.setProductQuantity(10);
        when(restTemplate.getForObject("http://localhost:8081/api/products/p1", InventoryResponse.class))
                .thenReturn(product);

        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).exchange(
                        eq("http://localhost:8081/api/products/update/p1"),
                        eq(HttpMethod.PUT),
                        argThat(Objects::nonNull),
                        eq(Void.class)
                );

        assertThrows(IllegalStateException.class, () -> adapter.releaseStock("p1", 2));
    }

    @Test
    void releaseStockShouldThrowWhenTransientFailureExhausted() {
        InventoryResponse product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductName("Produk A");
        product.setPrice(20000.0);
        product.setProductQuantity(10);
        when(restTemplate.getForObject("http://localhost:8081/api/products/p1", InventoryResponse.class))
                .thenReturn(product);

        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).exchange(
                        eq("http://localhost:8081/api/products/update/p1"),
                        eq(HttpMethod.PUT),
                        argThat(Objects::nonNull),
                        eq(Void.class)
                );

        assertThrows(IllegalStateException.class, () -> adapter.releaseStock("p1", 2));
    }

    @Test
    void releaseStockShouldUseDefaultsWhenProductFieldsAndInternalHeadersAreNull() {
        ReflectionTestUtils.setField(adapter, "inventoryInternalRole", null);
        ReflectionTestUtils.setField(adapter, "inventoryInternalUserId", null);

        InventoryResponse product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductName("Produk A");
        product.setDescription(null);
        product.setPrice(null);
        product.setProductQuantity(null);
        product.setJastiperId(null);

        when(restTemplate.getForObject("http://localhost:8081/api/products/p1", InventoryResponse.class))
                .thenReturn(product);

        AtomicReference<HttpEntity<?>> requestRef = new AtomicReference<>();
        doAnswer(invocation -> {
            requestRef.set(invocation.getArgument(2));
            return ResponseEntity.ok().build();
        }).when(restTemplate).exchange(
                eq("http://localhost:8081/api/products/update/p1"),
                eq(HttpMethod.PUT),
                any(HttpEntity.class),
                eq(Void.class)
        );

        adapter.releaseStock("p1", 2);

        @SuppressWarnings("unchecked")
        var payload = (java.util.Map<String, Object>) requestRef.get().getBody();
        assertEquals(2, payload.get("stock"));
        assertEquals("", payload.get("description"));
        assertEquals(0.0, payload.get("price"));
        assertEquals("", payload.get("jastiperId"));
        assertEquals("", requestRef.get().getHeaders().getFirst("X-User-Role"));
        assertEquals("", requestRef.get().getHeaders().getFirst("X-User-Id"));
    }

    @Test
    void getProductShouldFailFastWhenMaxAttemptsIsNotPositive() {
        ReflectionTestUtils.setField(adapter, "maxAttempts", 0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> adapter.getProduct("p1"));
        assertTrue(exception.getMessage().contains("max-attempts"));
        verify(restTemplate, times(0)).getForObject(anyString(), eq(InventoryResponse.class));
    }
}
