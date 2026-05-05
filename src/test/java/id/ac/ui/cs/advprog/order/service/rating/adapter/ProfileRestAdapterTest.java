package id.ac.ui.cs.advprog.order.service.rating.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ProfileRestAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ProfileRestAdapter adapter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(adapter, "profileUrl", "http://localhost:8083/api/profile");
        ReflectionTestUtils.setField(adapter, "maxAttempts", 2);
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "internal-order-service");
    }

    @Test
    void submitRatingSuccess() {
        adapter.submitRating("o1", "1", "10", "p1", 5, 4);
        verify(restTemplate).put(
                eq("http://localhost:8083/api/profile/admin/jastiper/stats"),
                argThat(Objects::nonNull)
        );
    }

    @Test
    void submitRatingShouldSendAuthorizationHeaderAndStatsPayload() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "Bearer test-admin-token");

        adapter.submitRating("o1", "1", "10", "p1", 5, 4);

        verify(restTemplate).put(
                eq("http://localhost:8083/api/profile/admin/jastiper/stats"),
                argThat(request -> {
                    if (!(request instanceof HttpEntity<?> entity)) {
                        return false;
                    }

                    Object body = entity.getBody();
                    if (!(body instanceof Map<?, ?> payload)) {
                        return false;
                    }

                    return "Bearer test-admin-token".equals(entity.getHeaders().getFirst("Authorization"))
                            && Long.valueOf(10L).equals(payload.get("userId"))
                            && Long.valueOf(1L).equals(payload.get("delta"));
                })
        );
    }

    @Test
    void submitRatingFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).put(anyString(), any());

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "10", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldRetryOnTransientFailure() {
        doThrow(new ResourceAccessException("timeout"))
                .doNothing()
                .when(restTemplate).put(anyString(), any());

        adapter.submitRating("o1", "1", "10", "p1", 5, 4);

        verify(restTemplate, times(2)).put(anyString(), any());
    }

    @Test
    void submitRatingShouldThrowWhenTransientFailureExhausted() {
        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).put(anyString(), any());

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "10", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldThrowWhenJastiperIdInvalid() {
        assertThrows(IllegalArgumentException.class, () ->
                adapter.submitRating("o1", "1", "not-number", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldThrowWhenJastiperIdNotPositive() {
        assertThrows(IllegalArgumentException.class, () ->
                adapter.submitRating("o1", "1", "0", "p1", 5, 4));
        assertThrows(IllegalArgumentException.class, () ->
                adapter.submitRating("o1", "1", "-10", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldFailFastWhenInternalAuthorizationBlank() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "   ");

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "10", "p1", 5, 4));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void submitRatingShouldFailFastWhenInternalAuthorizationNotBearer() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "internal-order-service");

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "10", "p1", 5, 4));
        verify(restTemplate, never()).put(anyString(), any());
    }
}
