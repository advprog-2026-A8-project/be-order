package id.ac.ui.cs.advprog.order.service.checkout.adapter;

import id.ac.ui.cs.advprog.order.service.checkout.WalletGateway;
import id.ac.ui.cs.advprog.order.service.common.AdapterConfigValidator;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.CheckBalanceRequest;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.CheckBalanceResponse;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.DeductBalanceRequest;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.RefundBalanceRequest;
import id.ac.ui.cs.advprog.bewallettransaksi.grpc.WalletMutationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WalletRestAdapter implements WalletGateway {
    private static final String ERROR_RETRY_EXHAUSTED = "Gagal mengakses Wallet gRPC contract service.";
    private static final String ERROR_WALLET_INSUFFICIENT = "Saldo Wallet tidak mencukupi atau User tidak ditemukan!";
    private static final String ERROR_WALLET_REFUND = "Gagal melakukan refund ke wallet.";
    private static final String ERROR_REQUEST_INVALID = "Wallet contract request tidak valid.";
    private static final String ERROR_EMPTY_RESPONSE = "Wallet contract response kosong.";

    private final WalletGrpcStubFactory walletGrpcStubFactory;

    @Value("${order.http.retry.max-attempts:2}")
    private int maxAttempts;

    @Override
    public void ensureSufficientBalance(String userId, double amount) {
        UUID walletUserId = parseWalletUserId(userId); 
        CheckBalanceResponse response = callGrpcWithRetry(() ->
                walletGrpcStubFactory.createStub().checkBalance(
                        CheckBalanceRequest.newBuilder()
                                .setUserId(walletUserId.toString())
                                .setAmount(toMoneyString(amount))
                                .build()
                )
        );
        if (!response.getSufficient()) {
            throw new IllegalArgumentException(ERROR_WALLET_INSUFFICIENT);
        }
    }

    @Override
    public void debit(String userId, String orderId, double amount, String idempotencyKey) {
        UUID walletUserId = parseWalletUserId(userId); 
        WalletMutationResponse result = callGrpcWithRetry(() ->
                walletGrpcStubFactory.createStub().deductBalance(
                        DeductBalanceRequest.newBuilder()
                                .setUserId(walletUserId.toString())
                                .setOrderId(orderId)
                                .setAmount(toMoneyString(amount))
                                .setIdempotencyKey(idempotencyKey)
                                .build()
                )
        );
        if (!result.getSuccess()) {
            throw new IllegalArgumentException(ERROR_WALLET_INSUFFICIENT);
        }
    }

    @Override
    public void refund(String userId, String orderId, double amount, String idempotencyKey) {
        UUID walletUserId = parseWalletUserId(userId); 
        WalletMutationResponse result = callGrpcWithRetry(() ->
                walletGrpcStubFactory.createStub().refundBalance(
                        RefundBalanceRequest.newBuilder()
                                .setUserId(walletUserId.toString())
                                .setOrderId(orderId)
                                .setAmount(toMoneyString(amount))
                                .setIdempotencyKey(idempotencyKey)
                                .build()
                )
        );
        if (!result.getSuccess()) {
            throw new IllegalStateException(ERROR_WALLET_REFUND);
        }
    }

    private <T> T callGrpcWithRetry(GrpcSupplier<T> requestSupplier) {
        AdapterConfigValidator.validateRetryMaxAttempts(maxAttempts);
        RuntimeException lastTransientError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                T result = requestSupplier.get();
                boolean shouldRetry = shouldRetryContractResponse(result, attempt);
                if (shouldRetry) {
                    continue;
                }
                return result;
            } catch (StatusRuntimeException ex) {
                if (isInvalidRequestStatus(ex.getStatus())) {
                    throw new IllegalArgumentException(ERROR_REQUEST_INVALID, ex);
                }
                if (!isRetryableStatus(ex.getStatus()) || attempt == maxAttempts) {
                    throw new IllegalStateException(ERROR_RETRY_EXHAUSTED, ex);
                }
                lastTransientError = ex;
            }
        }
        throw new IllegalStateException(ERROR_RETRY_EXHAUSTED, lastTransientError);
    }

    private boolean shouldRetryContractResponse(Object result, int attempt) {
        if (result == null) {
            throw new IllegalStateException(ERROR_EMPTY_RESPONSE);
        }
        if (!(result instanceof WalletMutationResponse mutationResponse)) {
            return false;
        }
        if (!mutationResponse.getSuccess() && mutationResponse.getRetryable()) {
            if (attempt == maxAttempts) {
                throw new IllegalStateException(ERROR_RETRY_EXHAUSTED);
            }
            return true;
        }
        return false;
    }

    private UUID parseWalletUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("User ID wallet harus berformat UUID.", ex);
        }
    }

    private String toMoneyString(double amount) {
        return BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private boolean isRetryableStatus(Status status) {
        Status.Code code = status.getCode();
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.INTERNAL
                || code == Status.Code.UNKNOWN;
    }

    private boolean isInvalidRequestStatus(Status status) {
        Status.Code code = status.getCode();
        return code == Status.Code.INVALID_ARGUMENT
                || code == Status.Code.UNAUTHENTICATED
                || code == Status.Code.PERMISSION_DENIED;
    }

    @FunctionalInterface
    private interface GrpcSupplier<T> {
        T get();
    }
}
