package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletRestAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private WalletRestAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "walletUrl", "http://localhost:8082/wallet");
        ReflectionTestUtils.setField(adapter, "maxAttempts", 2);
    }

    @Test
    void debitSuccess() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adapter.debit("u1", 10000.0);
        verify(restTemplate).postForEntity(eq("http://localhost:8082/wallet/pay"), any(), eq(Void.class));
    }

    @Test
    void debitFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalArgumentException.class, () -> adapter.debit("u1", 10000.0));
    }

    @Test
    void debitShouldRetryOnTransientFailure() {
        doThrow(new ResourceAccessException("timeout"))
                .doReturn(ResponseEntity.ok().build())
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        adapter.debit("u1", 10000.0);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void debitShouldThrowWhenTransientFailureExhausted() {
        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalStateException.class, () -> adapter.debit("u1", 10000.0));
    }

    @Test
    void refundSuccess() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adapter.refund("u1", 10000.0);
        verify(restTemplate).postForEntity(eq("http://localhost:8082/wallet/refund"), any(), eq(Void.class));
    }

    @Test
    void refundFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalStateException.class, () -> adapter.refund("u1", 10000.0));
    }

    @Test
    void refundShouldRetryOnTransientFailure() {
        doThrow(new ResourceAccessException("timeout"))
                .doReturn(ResponseEntity.ok().build())
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        adapter.refund("u1", 10000.0);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void refundShouldThrowWhenTransientFailureExhausted() {
        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalStateException.class, () -> adapter.refund("u1", 10000.0));
    }
}
