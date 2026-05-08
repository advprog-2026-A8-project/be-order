package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
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
        ReflectionTestUtils.setField(adapter, "walletUrl", "http://localhost:8082/api/contracts/wallet");
        ReflectionTestUtils.setField(adapter, "maxAttempts", 2);
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "Bearer test-wallet-token");
    }

    @Test
    void ensureSufficientBalanceSuccess() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(walletResult(true, false));

        adapter.ensureSufficientBalance("00000000-0000-0000-0000-000000000001", 10000.0);

        verify(restTemplate).postForObject(
                eq("http://localhost:8082/api/contracts/wallet/check-balance"),
                argThat(request -> hasAuthorizationHeader(request, "Bearer test-wallet-token")),
                any()
        );
    }

    @Test
    void ensureSufficientBalanceShouldThrowWhenContractReturnsFailure() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(walletResult(false, false));

        assertThrows(IllegalArgumentException.class,
                () -> adapter.ensureSufficientBalance("00000000-0000-0000-0000-000000000001", 10000.0));
    }

    @Test
    void debitSuccess() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(walletResult(true, false));

        adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1");

        verify(restTemplate).postForObject(
                eq("http://localhost:8082/api/contracts/wallet/deduct"),
                argThat(request -> hasMutationPayload(request, "order-1", "idem-1")),
                any()
        );
    }

    @Test
    void refundSuccess() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(walletResult(true, false));

        adapter.refund("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-r1");

        verify(restTemplate).postForObject(
                eq("http://localhost:8082/api/contracts/wallet/refund"),
                argThat(request -> hasMutationPayload(request, "order-1", "idem-r1")),
                any()
        );
    }

    @Test
    void debitShouldRetryOnRetryableContractError() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(walletResult(false, true))
                .thenReturn(walletResult(true, false));

        adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1");
        verify(restTemplate, times(2)).postForObject(anyString(), any(), any());
    }

    @Test
    void debitShouldThrowWhenRetryableErrorExhausted() {
        when(restTemplate.postForObject(anyString(), any(), any()))
                .thenReturn(walletResult(false, true))
                .thenReturn(walletResult(false, true));

        assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1"));
    }

    @Test
    void debitShouldThrowWhenUserIdIsNotUuid() {
        assertThrows(IllegalArgumentException.class, () ->
                adapter.debit("not-uuid", "order-1", 10000.0, "idem-1"));
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void ensureSufficientBalanceShouldThrowWhenUserIdIsNotUuid() {
        assertThrows(IllegalArgumentException.class, () ->
                adapter.ensureSufficientBalance("not-uuid", 10000.0));
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void debitShouldFailFastWhenMaxAttemptsIsNotPositive() {
        ReflectionTestUtils.setField(adapter, "maxAttempts", 0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1"));
        assertTrue(exception.getMessage().contains("max-attempts"));
    }

    @Test
    void debitShouldFailFastWhenInternalAuthorizationBlank() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "   ");

        assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1"));
        verify(restTemplate, never()).postForObject(anyString(), any(), any());
    }

    @Test
    void debitShouldThrowOnHttpClientError() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).postForObject(anyString(), any(), any());

        assertThrows(IllegalArgumentException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-1"));
    }

    @Test
    void refundShouldRetryOnTransientResourceAccess() {
        doThrow(new ResourceAccessException("timeout"))
                .doReturn(walletResult(true, false))
                .when(restTemplate).postForObject(anyString(), any(), any());

        adapter.refund("00000000-0000-0000-0000-000000000001", "order-1", 10000.0, "idem-r1");
        verify(restTemplate, times(2)).postForObject(anyString(), any(), any());
    }

    private Object walletResult(boolean success, boolean retryable) {
        Map<String, Object> payload = Map.of(
                "success", success,
                "updatedBalance", 100000.0,
                "errorCode", success ? "NONE" : "INTERNAL_ERROR",
                "retryable", retryable
        );
        return new java.util.HashMap<>(payload);
    }

    private boolean hasMutationPayload(Object request, String orderId, String idempotencyKey) {
        if (!(request instanceof HttpEntity<?> entity)) {
            return false;
        }
        if (!(entity.getBody() instanceof Map<?, ?> body)) {
            return false;
        }
        return orderId.equals(body.get("orderId"))
                && idempotencyKey.equals(body.get("idempotencyKey"))
                && body.get("userId") instanceof UUID
                && hasAuthorizationHeader(request, "Bearer test-wallet-token");
    }

    private boolean hasAuthorizationHeader(Object request, String expected) {
        if (!(request instanceof HttpEntity<?> entity)) {
            return false;
        }
        return expected.equals(entity.getHeaders().getFirst("Authorization"));
    }
}
