package id.ac.ui.cs.advprog.order.service.rating.adapter;

import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
import id.ac.ui.cs.advprog.order.service.common.AdapterConfigValidator;
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
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProfileRestAdapter implements ProfileGateway {
    private static final long SUCCESSFUL_TRANSACTION_DELTA = 1L;
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String ADAPTER_NAME = "Profile";
    private static final String MESSAGE_INVALID_JASTIPER_ID_UUID =
            "ID jastiper harus berformat UUID valid.";

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
        AdapterConfigValidator.validateRetryMaxAttempts(maxAttempts);
        String authorizationToken = validateAndGetInternalAuthorization();

        String statsUrl = UriComponentsBuilder.fromUriString(profileUrl)
                .pathSegment("admin", "jastiper", "stats")
                .toUriString();

        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.put(statsUrl, buildStatsRequest(jastiperId, authorizationToken));
                return;
        } catch (IllegalArgumentException ex) {
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
        UUID parsedJastiperId = parseJastiperUuid(jastiperId);
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
        return AdapterConfigValidator.validateAndNormalizeBearerToken(internalAuthorization, ADAPTER_NAME);
    }

    private UUID parseJastiperUuid(String jastiperId) {
        if (jastiperId == null || jastiperId.isBlank()) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID_UUID);
        }
        try {
            return UUID.fromString(jastiperId.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(MESSAGE_INVALID_JASTIPER_ID_UUID, ex);
        }
    }

}
