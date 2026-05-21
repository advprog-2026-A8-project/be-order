package id.ac.ui.cs.advprog.order.service.checkout;

public interface WalletGateway {
    void ensureSufficientBalance(String userId, double amount);
    void debit(String userId, String orderId, double amount, String idempotencyKey);
    void refund(String userId, String orderId, double amount, String idempotencyKey);
}
