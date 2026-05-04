package id.ac.ui.cs.advprog.order.service.rating.adapter;

import id.ac.ui.cs.advprog.order.service.rating.ProfileGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProfileRestAdapter implements ProfileGateway {

    private final RestTemplate restTemplate;

    @Value("${order.profile.url}")
    private String profileUrl;

    @Override
    public void submitRating(String orderId,
                             String titiperId,
                             String jastiperId,
                             String productId,
                             int jastiperRating,
                             int productRating) {
        String ratingUrl = UriComponentsBuilder.fromUriString(profileUrl)
                .pathSegment("admin", "jastiper", "stats")
                .toUriString();

        Map<String, Object> payload = Map.of(
                "userId", Long.parseLong(jastiperId),
                "delta", 1L
        );

        try {
            restTemplate.put(ratingUrl, payload);
        } catch (HttpClientErrorException ex) {
            throw new IllegalStateException("Gagal mengirim rating ke Profile module", ex);
        }
    }
}
