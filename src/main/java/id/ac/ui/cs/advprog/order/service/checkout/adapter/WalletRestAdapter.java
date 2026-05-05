package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
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
import java.util.function.Function;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class WalletRestAdapter implements WalletGateway {
    private static final String DESCRIPTION_PAYMENT = "Order payment";
    private static final String DESCRIPTION_REFUND = "Order refund";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EMPTY = "";
    private static final String PAY_PATH = "pay";
    private static final String REFUND_PATH = "refund";

    private final RestTemplate restTemplate;

    @Value("${order.wallet.url}")
    private String walletUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${order.wallet.internal-authorization:internal-order-service}")
    private String internalAuthorization;

    @Override
    public void debit(String userId, double amount) {
        UUID walletUserId = parseWalletUserId(userId);
        String authorizationToken = validateAndGetInternalAuthorization();
        executeWalletMutation(
                buildWalletUrl(PAY_PATH),
                () -> buildWalletRequest(walletUserId, amount, DESCRIPTION_PAYMENT, authorizationToken),
                e -> new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!", e),
                "Gagal mengakses Wallet service saat debit."
        );
    }

    @Override
    public void refund(String userId, double amount) {
        UUID walletUserId = parseWalletUserId(userId);
        executeWalletMutation(
                buildWalletUrl(REFUND_PATH),
                () -> buildWalletRequest(walletUserId, amount, DESCRIPTION_REFUND, null),
                e -> new IllegalStateException("Gagal melakukan refund ke wallet.", e),
                "Gagal mengakses Wallet service saat refund."
        );
    }

    private HttpEntity<Map<String, Object>> buildWalletRequest(
            UUID userId,
            double amount,
            String description,
            String authorizationToken
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (authorizationToken != null) {
            headers.set(AUTHORIZATION_HEADER, authorizationToken);
        }

        Map<String, Object> payload = Map.of(
                "userId", userId,
                "amount", amount,
                "description", description
        );

        return new HttpEntity<>(payload, headers);
    }

    private UUID parseWalletUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("User ID wallet harus berformat UUID.", ex);
        }
    }

    private String buildWalletUrl(String pathSegment) {
        return UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment(pathSegment)
                .toUriString();
    }

    private void executeWalletMutation(
            String url,
            Supplier<HttpEntity<Map<String, Object>>> requestSupplier,
            Function<HttpClientErrorException, RuntimeException> httpExceptionMapper,
            String transientFailureMessage
    ) {
        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                restTemplate.postForEntity(url, requestSupplier.get(), Void.class);
                return;
            } catch (HttpClientErrorException e) {
                throw httpExceptionMapper.apply(e);
            } catch (ResourceAccessException e) {
                lastTransientError = e;
            }
        }
        throw new IllegalStateException(transientFailureMessage, lastTransientError);
    }

    private String validateAndGetInternalAuthorization() {
        String normalized = normalize(internalAuthorization);
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Token internal authorization untuk Wallet belum dikonfigurasi.");
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
}
