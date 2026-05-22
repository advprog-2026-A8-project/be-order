package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "Bearer internal-token");
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
    void reserveStockSuccessShouldUseAuthorizationHeader() {
        adapter.reserveStock("p1", 2);

        verify(restTemplate).exchange(
                eq("http://localhost:8081/api/products/p1/reserve?quantity=2"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    void reserveStockShouldThrowWhenInsufficient() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThrows(IllegalStateException.class, () -> adapter.reserveStock("p1", 2));
    }

    @Test
    void reserveStockShouldRetryOnTransientFailure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenReturn(null);

        adapter.reserveStock("p1", 2);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void reserveStockShouldThrowWhenTransientFailureExhausted() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"));

        assertThrows(IllegalStateException.class, () -> adapter.reserveStock("p1", 2));
    }

    @Test
    void reserveStockShouldPreferRequestAuthorizationHeader() {
        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        adapter.reserveStock("p1", 2, "Bearer request-token");

        verify(restTemplate).exchange(
                eq("http://localhost:8081/api/products/p1/reserve?quantity=2"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Void.class)
        );
        assertEquals("Bearer request-token", entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void reserveStockShouldFallbackToInternalAuthorizationWhenRequestHeaderBlank() {
        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        adapter.reserveStock("p1", 2, "   ");

        verify(restTemplate).exchange(
                eq("http://localhost:8081/api/products/p1/reserve?quantity=2"),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(Void.class)
        );
        assertEquals("Bearer internal-token", entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void reserveStockShouldFailFastWhenRequestAuthorizationInvalidBearerFormat() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> adapter.reserveStock("p1", 1, "not-a-bearer-token")
        );
        assertTrue(exception.getMessage().contains("Bearer"));
    }

    @Test
    void releaseStockSuccessShouldUseReleaseEndpoint() {
        adapter.releaseStock("p1", 2);

        verify(restTemplate).exchange(
                eq("http://localhost:8081/api/products/p1/release?quantity=2"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Void.class)
        );
    }

    @Test
    void releaseStockShouldRetryOnTransientFailure() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenReturn(null);

        adapter.releaseStock("p1", 2);
        verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class));
    }

    @Test
    void releaseStockShouldThrowWhenClientError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThrows(IllegalStateException.class, () -> adapter.releaseStock("p1", 2));
    }

    @Test
    void releaseStockShouldThrowWhenTransientFailureExhausted() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"));

        assertThrows(IllegalStateException.class, () -> adapter.releaseStock("p1", 2));
    }

    @Test
    void reserveStockShouldFailFastWhenInternalAuthorizationBlank() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "   ");
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> adapter.reserveStock("p1", 1));
        assertTrue(exception.getMessage().contains("Token internal authorization"));
    }

    @Test
    void getProductShouldFailFastWhenMaxAttemptsIsNotPositive() {
        ReflectionTestUtils.setField(adapter, "maxAttempts", 0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> adapter.getProduct("p1"));
        assertTrue(exception.getMessage().contains("max-attempts"));
        verify(restTemplate, times(0)).getForObject(anyString(), eq(InventoryResponse.class));
    }
}

