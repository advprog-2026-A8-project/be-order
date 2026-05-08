package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
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
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class WalletRestAdapter implements WalletGateway {
    private static final String ADAPTER_NAME = "Wallet";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String CHECK_BALANCE_PATH = "check-balance";
    private static final String DEDUCT_PATH = "deduct";
    private static final String REFUND_PATH = "refund";
    private static final String ERROR_RETRY_EXHAUSTED = "Gagal mengakses Wallet contract service.";
    private static final String ERROR_WALLET_INSUFFICIENT = "Saldo Wallet tidak mencukupi atau User tidak ditemukan!";
    private static final String ERROR_WALLET_REFUND = "Gagal melakukan refund ke wallet.";
    private static final String PAYLOAD_USER_ID = "userId";
    private static final String PAYLOAD_AMOUNT = "amount";
    private static final String PAYLOAD_ORDER_ID = "orderId";
    private static final String PAYLOAD_IDEMPOTENCY_KEY = "idempotencyKey";

    private final RestTemplate restTemplate;

    @Value("${order.wallet.url}")
    private String walletUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Value("${order.wallet.internal-authorization:Bearer internal-order-service}")
    private String internalAuthorization;

    @Override
    public void ensureSufficientBalance(String userId, double amount) {
        UUID walletUserId = parseWalletUserId(userId);
        String authorizationToken = validateAndGetInternalAuthorization();
        WalletContractResult result = callWalletContractWithRetry(() ->
                restTemplate.postForObject(
                        buildWalletUrl(CHECK_BALANCE_PATH),
                        buildAuthorizedRequest(
                                authorizationToken,
                                Map.of(PAYLOAD_USER_ID, walletUserId, PAYLOAD_AMOUNT, amount)
                        ),
                        Map.class
                )
        );
        if (!result.success()) {
            throw new IllegalArgumentException(ERROR_WALLET_INSUFFICIENT);
        }
    }

    @Override
    public void debit(String userId, String orderId, double amount, String idempotencyKey) {
        UUID walletUserId = parseWalletUserId(userId);
        String authorizationToken = validateAndGetInternalAuthorization();
        WalletContractResult result = callWalletContractWithRetry(() ->
                restTemplate.postForObject(
                        buildWalletUrl(DEDUCT_PATH),
                        buildAuthorizedRequest(
                                authorizationToken,
                                Map.of(
                                        PAYLOAD_USER_ID, walletUserId,
                                        PAYLOAD_ORDER_ID, orderId,
                                        PAYLOAD_AMOUNT, amount,
                                        PAYLOAD_IDEMPOTENCY_KEY, idempotencyKey
                                )
                        ),
                        Map.class
                )
        );
        if (!result.success()) {
            throw new IllegalArgumentException(ERROR_WALLET_INSUFFICIENT);
        }
    }

    @Override
    public void refund(String userId, String orderId, double amount, String idempotencyKey) {
        UUID walletUserId = parseWalletUserId(userId);
        String authorizationToken = validateAndGetInternalAuthorization();
        WalletContractResult result = callWalletContractWithRetry(() ->
                restTemplate.postForObject(
                        buildWalletUrl(REFUND_PATH),
                        buildAuthorizedRequest(
                                authorizationToken,
                                Map.of(
                                        PAYLOAD_USER_ID, walletUserId,
                                        PAYLOAD_ORDER_ID, orderId,
                                        PAYLOAD_AMOUNT, amount,
                                        PAYLOAD_IDEMPOTENCY_KEY, idempotencyKey
                                )
                        ),
                        Map.class
                )
        );
        if (!result.success()) {
            throw new IllegalStateException(ERROR_WALLET_REFUND);
        }
    }

    private WalletContractResult callWalletContractWithRetry(
            Supplier<Map<String, Object>> requestSupplier
    ) {
        AdapterConfigValidator.validateRetryMaxAttempts(maxAttempts);
        ResourceAccessException lastTransientError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                Map<String, Object> responseBody = requestSupplier.get();
                WalletContractResult result = parseResult(responseBody);
                if (result == null) {
                    throw new IllegalStateException("Wallet contract response kosong.");
                }
                if (!result.success() && result.retryable()) {
                    continue;
                }
                return result;
            } catch (HttpClientErrorException ex) {
                throw new IllegalArgumentException("Wallet contract request tidak valid.", ex);
            } catch (ResourceAccessException ex) {
                lastTransientError = ex;
            }
        }
        throw new IllegalStateException(ERROR_RETRY_EXHAUSTED, lastTransientError);
    }

    private HttpEntity<Map<String, Object>> buildAuthorizedRequest(
            String authorizationToken,
            Map<String, Object> payload
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(AUTHORIZATION_HEADER, authorizationToken);
        return new HttpEntity<>(payload, headers);
    }

    private UUID parseWalletUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("User ID wallet harus berformat UUID.", ex);
        }
    }

    private String validateAndGetInternalAuthorization() {
        return AdapterConfigValidator.validateAndNormalizeBearerToken(internalAuthorization, ADAPTER_NAME);
    }

    private String buildWalletUrl(String pathSegment) {
        return UriComponentsBuilder.fromUriString(walletUrl)
                .pathSegment(pathSegment)
                .toUriString();
    }

    private record WalletContractResult(
            boolean success,
            Object updatedBalance,
            String errorCode,
            boolean retryable
    ) {
    }

    private WalletContractResult parseResult(Map<String, Object> responseBody) {
        if (responseBody == null) {
            return null;
        }
        boolean success = Boolean.TRUE.equals(responseBody.get("success"));
        boolean retryable = Boolean.TRUE.equals(responseBody.get("retryable"));
        Object updatedBalance = responseBody.get("updatedBalance");
        Object errorCodeObject = responseBody.get("errorCode");
        String errorCode = errorCodeObject == null ? null : errorCodeObject.toString();
        return new WalletContractResult(success, updatedBalance, errorCode, retryable);
    }
}
