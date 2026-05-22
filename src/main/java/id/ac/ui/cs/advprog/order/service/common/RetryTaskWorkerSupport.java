package id.ac.ui.cs.advprog.order.service.common;

public final class RetryTaskWorkerSupport {
    private RetryTaskWorkerSupport() {
    }

    public static long computeBackoffMillis(long baseDelayMs, int attempt) {
        long safeBase = Math.clamp(baseDelayMs, 100L, Long.MAX_VALUE);
        int boundedAttempt = (int) Math.clamp((long) attempt - 1L, 0L, 6L);
        long multiplier = 1L << boundedAttempt;
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
