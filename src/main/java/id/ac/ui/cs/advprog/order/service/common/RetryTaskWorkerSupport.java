package id.ac.ui.cs.advprog.order.service.common;

public final class RetryTaskWorkerSupport {
    private RetryTaskWorkerSupport() {
    }

    public static long computeBackoffMillis(long baseDelayMs, int attempt) {
        long safeBase = Math.max(100L, baseDelayMs);
        long multiplier = 1L << Math.min(6, Math.max(0, attempt - 1));
        return safeBase * multiplier;
    }

    public static String compactErrorMessage(Throwable throwable, int maxLength) {
        String message = throwable.getClass().getSimpleName() + ": " + throwable.getMessage();
        if (message.length() <= maxLength) {
            return message;
        }
        return message.substring(0, maxLength);
    }
}
