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

    private final RestTemplate restTemplate;

    @Value("${order.profile.url}")
    private String profileUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${order.profile.internal-authorization:internal-order-service}")
    private String internalAuthorization;

    @Override
    public void submitRating(String orderId,
                             String titiperId,
                             String jastiperId,
                             String productId,
                             int jastiperRating,
                             int productRating) {
        String statsUrl = UriComponentsBuilder.fromUriString(profileUrl)
                .pathSegment("admin", "jastiper", "stats")
                .toUriString();

        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.put(statsUrl, buildStatsRequest(jastiperId));
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
        return Map.of(
                "userId", Long.parseLong(jastiperId),
                "delta", SUCCESSFUL_TRANSACTION_DELTA
        );
    }

    private HttpEntity<Map<String, Object>> buildStatsRequest(String jastiperId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", internalAuthorization);
        return new HttpEntity<>(buildStatsPayload(jastiperId), headers);
    }
}
