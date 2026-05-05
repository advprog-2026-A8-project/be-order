package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpEntity;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

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
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "Bearer test-wallet-token");
    }

    @Test
    void debitSuccess() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0);
        verify(restTemplate).postForEntity(eq("http://localhost:8082/wallet/pay"), any(), eq(Void.class));
    }

    @Test
    void debitFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalArgumentException.class, () -> adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0));
    }

    @Test
    void debitShouldRetryOnTransientFailure() {
        doThrow(new ResourceAccessException("timeout"))
                .doReturn(ResponseEntity.ok().build())
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void debitShouldThrowWhenTransientFailureExhausted() {
        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalStateException.class, () -> adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0));
    }

    @Test
    void refundSuccess() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adapter.refund("00000000-0000-0000-0000-000000000001", 10000.0);
        verify(restTemplate).postForEntity(eq("http://localhost:8082/wallet/refund"), any(), eq(Void.class));
    }

    @Test
    void refundFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.INTERNAL_SERVER_ERROR))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalStateException.class, () -> adapter.refund("00000000-0000-0000-0000-000000000001", 10000.0));
    }

    @Test
    void refundShouldRetryOnTransientFailure() {
        doThrow(new ResourceAccessException("timeout"))
                .doReturn(ResponseEntity.ok().build())
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        adapter.refund("00000000-0000-0000-0000-000000000001", 10000.0);
        verify(restTemplate, times(2)).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void refundShouldThrowWhenTransientFailureExhausted() {
        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).postForEntity(anyString(), any(), eq(Void.class));

        assertThrows(IllegalStateException.class, () -> adapter.refund("00000000-0000-0000-0000-000000000001", 10000.0));
    }

    @Test
    void debitShouldThrowWhenUserIdIsNotUuid() {
        assertThrows(IllegalArgumentException.class, () -> adapter.debit("not-uuid", 10000.0));
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void refundShouldThrowWhenUserIdIsNotUuid() {
        assertThrows(IllegalArgumentException.class, () -> adapter.refund("not-uuid", 10000.0));
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void debitShouldFailFastWhenMaxAttemptsIsNotPositive() {
        ReflectionTestUtils.setField(adapter, "maxAttempts", 0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0));
        assertTrue(exception.getMessage().contains("max-attempts"));
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void debitShouldSendUuidTypedUserIdInPayload() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0);

        verify(restTemplate).postForEntity(
                eq("http://localhost:8082/wallet/pay"),
                argThat(request -> hasUuidUserId(request, UUID.fromString("00000000-0000-0000-0000-000000000001"))),
                eq(Void.class)
        );
    }

    @Test
    void debitShouldSendBearerAuthorizationHeader() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0);

        verify(restTemplate).postForEntity(
                eq("http://localhost:8082/wallet/pay"),
                argThat(request -> hasAuthorizationHeader(request, "Bearer test-wallet-token")),
                eq(Void.class)
        );
    }

    @Test
    void debitShouldFailFastWhenInternalAuthorizationBlank() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "   ");

        assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0));
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void debitShouldFailFastWhenInternalAuthorizationNotBearer() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "internal-order-service");

        assertThrows(IllegalStateException.class, () ->
                adapter.debit("00000000-0000-0000-0000-000000000001", 10000.0));
        verify(restTemplate, never()).postForEntity(anyString(), any(), eq(Void.class));
    }

    @Test
    void refundShouldSendUuidTypedUserIdInPayload() {
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());

        adapter.refund("00000000-0000-0000-0000-000000000001", 10000.0);

        verify(restTemplate).postForEntity(
                eq("http://localhost:8082/wallet/refund"),
                argThat(request -> hasUuidUserId(request, UUID.fromString("00000000-0000-0000-0000-000000000001"))),
                eq(Void.class)
        );
    }

    private boolean hasUuidUserId(Object request, UUID expected) {
        if (!(request instanceof HttpEntity<?> entity)) {
            return false;
        }
        if (!(entity.getBody() instanceof Map<?, ?> body)) {
            return false;
        }
        return expected.equals(body.get("userId"));
    }

    private boolean hasAuthorizationHeader(Object request, String expected) {
        if (!(request instanceof HttpEntity<?> entity)) {
            return false;
        }
        return expected.equals(entity.getHeaders().getFirst("Authorization"));
    }
}
