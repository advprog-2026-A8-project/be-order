package id.ac.ui.cs.advprog.order.service.common;

public final class AdapterConfigValidator {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String EMPTY = "";

    private AdapterConfigValidator() {
        // Utility class
    }

    public static void validateRetryMaxAttempts(int maxAttempts) {
        if (maxAttempts <= 0) {
            throw new IllegalStateException("Konfigurasi order.http.retry.max-attempts harus lebih dari 0.");
        }
    }

    public static String validateAndNormalizeBearerToken(String token, String adapterName) {
        String normalized = normalize(token);
        if (normalized.isEmpty()) {
            throw new IllegalStateException(
                    "Token internal authorization untuk " + adapterName + " belum dikonfigurasi."
            );
        }
        if (!normalized.startsWith(BEARER_PREFIX) || normalized.length() <= BEARER_PREFIX.length()) {
            throw new IllegalStateException("Token internal authorization harus berformat 'Bearer <token>'.");
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return EMPTY;
        }
        return value.trim();
    }
}
