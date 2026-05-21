package id.ac.ui.cs.advprog.order.service.rating.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

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
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "Bearer test-admin-token");
    }

    @Test
    void submitRatingSuccess() {
        adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4);
        verify(restTemplate).put(
                eq("http://localhost:8083/api/profile/admin/jastiper/stats"),
                argThat(Objects::nonNull)
        );
    }

    @Test
    void submitRatingShouldSendAuthorizationHeaderAndStatsPayload() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "Bearer test-admin-token");

        adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4);

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
                            && "550e8400-e29b-41d4-a716-446655440000".equals(payload.get("userId").toString())
                            && Long.valueOf(1L).equals(payload.get("delta"));
                })
        );
    }

    @Test
    void submitRatingFailureShouldThrow() {
        doThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST))
                .when(restTemplate).put(anyString(), any());

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldRetryOnTransientFailure() {
        doThrow(new ResourceAccessException("timeout"))
                .doNothing()
                .when(restTemplate).put(anyString(), any());

        adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4);

        verify(restTemplate, times(2)).put(anyString(), any());
    }

    @Test
    void submitRatingShouldThrowWhenTransientFailureExhausted() {
        doThrow(new ResourceAccessException("timeout-1"))
                .doThrow(new ResourceAccessException("timeout-2"))
                .when(restTemplate).put(anyString(), any());

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldThrowWhenJastiperIdInvalidAndLookupFails() {
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(ResponseEntity.ok(Map.of()));

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "not-uuid", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldThrowWhenJastiperIdNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () ->
                adapter.submitRating("o1", "1", null, "p1", 5, 4));
        assertThrows(IllegalArgumentException.class, () ->
                adapter.submitRating("o1", "1", "   ", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldThrowWhenJastiperIdNotUuidAndLookupFails() {
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        )).thenReturn(ResponseEntity.ok(Map.of()));

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "0", "p1", 5, 4));
        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "-10", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldResolveJastiperUuidByEmailWhenNeeded() {
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        ))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("id", "550e8400-e29b-41d4-a716-446655440000"))));

        adapter.submitRating("o1", "1", "jastiper@example.com", "p1", 5, 4);

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
                    return "550e8400-e29b-41d4-a716-446655440000".equals(payload.get("userId").toString());
                })
        );
    }

    @Test
    void submitRatingShouldFailFastWhenInternalAuthorizationBlank() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "   ");

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void submitRatingShouldFailFastWhenInternalAuthorizationNotBearer() {
        ReflectionTestUtils.setField(adapter, "internalAuthorization", "internal-order-service");

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void submitRatingShouldFailFastWhenMaxAttemptsIsNotPositive() {
        ReflectionTestUtils.setField(adapter, "maxAttempts", 0);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4));
        assertTrue(exception.getMessage().contains("max-attempts"));
        verify(restTemplate, never()).put(anyString(), any());
    }

    @Test
    void submitRatingShouldWrapIllegalArgumentFromRestTemplatePut() {
        doThrow(new IllegalArgumentException("invalid payload"))
                .when(restTemplate).put(anyString(), any());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                adapter.submitRating("o1", "1", "550e8400-e29b-41d4-a716-446655440000", "p1", 5, 4));

        assertTrue(ex.getMessage().contains("ID jastiper tidak valid"));
    }

    @Test
    void submitRatingShouldFailWhenLookupDataIsNotMap() {
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        ))
                .thenReturn(ResponseEntity.ok(Map.of("data", "not-a-map")));

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "jastiper@example.com", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldFailWhenLookupIdIsBlank() {
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        ))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("id", "   "))));

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "jastiper@example.com", "p1", 5, 4));
    }

    @Test
    void submitRatingShouldFailWhenLookupIdIsInvalidUuid() {
        when(restTemplate.exchange(
                anyString(),
                eq(org.springframework.http.HttpMethod.GET),
                any(),
                org.mockito.ArgumentMatchers.<ParameterizedTypeReference<Map<String, Object>>>any()
        ))
                .thenReturn(ResponseEntity.ok(Map.of("data", Map.of("id", "invalid-uuid"))));

        assertThrows(IllegalStateException.class, () ->
                adapter.submitRating("o1", "1", "jastiper@example.com", "p1", 5, 4));
    }

    @Test
    void parseJastiperUuidPrivateMethodShouldRejectNullAndInvalid() throws Exception {
        Method method = ProfileRestAdapter.class.getDeclaredMethod("parseJastiperUuid", String.class);
        method.setAccessible(true);

        IllegalArgumentException nullEx = assertThrows(
                IllegalArgumentException.class,
                () -> invokeParseMethod(method, null)
        );
        assertTrue(nullEx.getMessage().contains("UUID"));

        IllegalArgumentException invalidEx = assertThrows(
                IllegalArgumentException.class,
                () -> invokeParseMethod(method, "invalid-uuid")
        );
        assertTrue(invalidEx.getMessage().contains("UUID"));

        UUID parsed = invokeParseMethod(method, "550e8400-e29b-41d4-a716-446655440000");
        assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), parsed);
    }

    private UUID invokeParseMethod(Method method, String value) {
        try {
            return (UUID) method.invoke(adapter, value);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}
