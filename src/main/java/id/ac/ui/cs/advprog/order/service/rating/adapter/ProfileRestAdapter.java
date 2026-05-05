package id.ac.ui.cs.advprog.order.service.rating.adapter;

import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProfileRestAdapter implements ProfileGateway {
    private static final long SUCCESSFUL_TRANSACTION_DELTA = 1L;
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EMPTY = "";
    private static final String MESSAGE_INVALID_JASTIPER_ID_POSITIVE =
            "ID jastiper harus berupa angka positif.";

    private final RestTemplate restTemplate;

    @Value("${order.profile.url}")
    private String profileUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${order.profile.internal-authorization:Bearer internal-order-service}")
    private String internalAuthorization;

    @Override
    public void submitRating(String orderId,
                             String titiperId,
                             String jastiperId,
                             String productId,
                             int jastiperRating,
                             int productRating) {
        validateAdapterConfiguration();
        String authorizationToken = validateAndGetInternalAuthorization();

        String statsUrl = UriComponentsBuilder.fromUriString(profileUrl)
                .pathSegment("admin", "jastiper", "stats")
                .toUriString();

        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.put(statsUrl, buildStatsRequest(jastiperId, authorizationToken));
                return;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("ID jastiper tidak valid untuk update statistik.", ex);
            } catch (HttpClientErrorException ex) {
                throw new IllegalStateException("Gagal mengirim rating ke Profile module", ex);
            } catch (ResourceAccessException ex) {
                lastTransientError = ex;
            }
        }
        throw new IllegalStateException("Gagal mengakses Profile module untuk submit rating", lastTransientError);
    }

    private Map<String, Object> buildStatsPayload(String jastiperId) {
        long parsedJastiperId = parsePositiveJastiperId(jastiperId);
        return Map.of(
                "userId", parsedJastiperId,
                "delta", SUCCESSFUL_TRANSACTION_DELTA
        );
    }

    private HttpEntity<Map<String, Object>> buildStatsRequest(String jastiperId, String authorizationToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AUTHORIZATION_HEADER, authorizationToken);
        return new HttpEntity<>(buildStatsPayload(jastiperId), headers);
    }

    private String validateAndGetInternalAuthorization() {
        String normalized = normalize(internalAuthorization);
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Token internal authorization untuk Profile belum dikonfigurasi.");
        }
        if (!normalized.startsWith(BEARER_PREFIX) || normalized.length() <= BEARER_PREFIX.length()) {
            throw new IllegalStateException("Token internal authorization harus berformat 'Bearer <token>'.");
        }
        return normalized;
    }

    private String normalize(String value) {
        if (value == null) {
            return EMPTY;
        }
        return value.trim();
    }

    private long parsePositiveJastiperId(String jastiperId) {
        long parsed = Long.parseLong(jastiperId);
        if (parsed <= 0) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID_POSITIVE);
        }
        return parsed;
    }

    private void validateAdapterConfiguration() {
        if (maxAttempts <= 0) {
            throw new IllegalStateException("Konfigurasi order.http.retry.max-attempts harus lebih dari 0.");
        }
    }
}
