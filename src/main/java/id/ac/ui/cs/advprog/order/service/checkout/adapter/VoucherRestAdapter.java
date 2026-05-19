package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.checkout.VoucherGateway;
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

@Component
@RequiredArgsConstructor
public class VoucherRestAdapter implements VoucherGateway {
    private static final String VALIDATE_PATH = "validate";
    private static final String USE_PATH = "use";
    private static final String KEY_CODE = "code";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_VALID = "valid";
    private static final String KEY_DISCOUNT_AMOUNT = "discountAmount";
    private static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    private static final String ERROR_RETRY_EXHAUSTED = "Gagal mengakses Voucher service.";
    private static final String ERROR_INVALID_VOUCHER = "Voucher tidak valid atau tidak dapat digunakan.";
    private static final String ERROR_USE_VOUCHER = "Gagal menggunakan voucher.";

    private final RestTemplate restTemplate;

    @Value("${order.voucher.url}")
    private String voucherUrl;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Override
    public double validateDiscount(String voucherCode, double amount) {
        Map<String, Object> response = callWithRetry(() ->
                restTemplate.postForObject(
                        buildVoucherUrl(VALIDATE_PATH),
                        buildJsonRequest(Map.of(KEY_CODE, voucherCode, KEY_AMOUNT, amount)),
                        Map.class
                )
        );

        if (response == null || !Boolean.TRUE.equals(response.get(KEY_VALID))) {
            throw new IllegalArgumentException(ERROR_INVALID_VOUCHER);
        }

        Object discountObject = response.get(KEY_DISCOUNT_AMOUNT);
        if (!(discountObject instanceof Number discountNumber)) {
            throw new IllegalStateException("Response discountAmount dari Voucher tidak valid.");
        }
        return Math.max(0.0, discountNumber.doubleValue());
    }

    @Override
    public void useVoucher(String voucherCode) {
        callWithRetry(() ->
                restTemplate.postForObject(
                        buildVoucherUrl(USE_PATH),
                        buildJsonRequest(Map.of(KEY_CODE, voucherCode)),
                        Map.class
                )
        );
    }

    @Override
    public void restoreVoucher(String voucherCode, String idempotencyKey) {
        String normalizedIdempotencyKey = validateIdempotencyKey(idempotencyKey);
        callWithRetry(() ->
                restTemplate.postForObject(
                        buildVoucherUrl("restore"),
                        buildJsonRequestWithIdempotency(
                                Map.of(KEY_CODE, voucherCode),
                                normalizedIdempotencyKey
                        ),
                        Map.class
                )
        );
    }

    private Map<String, Object> callWithRetry(ThrowingSupplier<Map<String, Object>> requestSupplier) {
        AdapterConfigValidator.validateRetryMaxAttempts(maxAttempts);
        ResourceAccessException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return requestSupplier.get();
            } catch (HttpClientErrorException ex) {
                throw mapClientError(ex);
            } catch (ResourceAccessException ex) {
                lastTransientError = ex;
            }
        }
        throw new IllegalStateException(ERROR_RETRY_EXHAUSTED, lastTransientError);
    }

    private RuntimeException mapClientError(HttpClientErrorException ex) {
        if (ex.getStatusCode().is4xxClientError()) {
            return new IllegalArgumentException(ERROR_INVALID_VOUCHER, ex);
        }
        return new IllegalStateException(ERROR_USE_VOUCHER, ex);
    }

    private HttpEntity<Map<String, Object>> buildJsonRequest(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(payload, headers);
    }

    private HttpEntity<Map<String, Object>> buildJsonRequestWithIdempotency(
            Map<String, Object> payload,
            String idempotencyKey
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HEADER_IDEMPOTENCY_KEY, idempotencyKey);
        return new HttpEntity<>(payload, headers);
    }

    private String validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalStateException("Idempotency-Key untuk restore voucher wajib diisi.");
        }
        return idempotencyKey.trim();
    }

    private String buildVoucherUrl(String pathSegment) {
        return UriComponentsBuilder.fromUriString(voucherUrl)
                .pathSegment(pathSegment)
                .toUriString();
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get();
    }
}
