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
    private static final String INTERNAL_AUTHORIZATION = "internal-order-service";
    private static final String PAY_PATH = "pay";
    private static final String REFUND_PATH = "refund";

    private final RestTemplate restTemplate;

    @Value("${order.wallet.url}")
    private String walletUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Override
    public void debit(String userId, double amount) {
        UUID walletUserId = parseWalletUserId(userId);
        executeWalletMutation(
                buildWalletUrl(PAY_PATH),
                () -> buildWalletRequest(walletUserId, amount, DESCRIPTION_PAYMENT, true),
                e -> new IllegalArgumentException("Saldo Wallet tidak mencukupi atau User tidak ditemukan!", e),
                "Gagal mengakses Wallet service saat debit."
        );
    }

    @Override
    public void refund(String userId, double amount) {
        UUID walletUserId = parseWalletUserId(userId);
        executeWalletMutation(
                buildWalletUrl(REFUND_PATH),
                () -> buildWalletRequest(walletUserId, amount, DESCRIPTION_REFUND, false),
                e -> new IllegalStateException("Gagal melakukan refund ke wallet.", e),
                "Gagal mengakses Wallet service saat refund."
        );
    }

    private HttpEntity<Map<String, Object>> buildWalletRequest(
            UUID userId,
            double amount,
            String description,
            boolean withAuthorization
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (withAuthorization) {
            headers.set("Authorization", INTERNAL_AUTHORIZATION);
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
}
