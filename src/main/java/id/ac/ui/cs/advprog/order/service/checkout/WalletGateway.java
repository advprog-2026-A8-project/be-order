package id.ac.ui.cs.advprog.order.service.checkout;

public interface WalletGateway {
    void debit(String userId, double amount);
    void refund(String userId, double amount);
}
