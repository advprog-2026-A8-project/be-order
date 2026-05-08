package id.ac.ui.cs.advprog.order.service.checkout;

public interface VoucherGateway {
    double validateDiscount(String voucherCode, double amount);
    void useVoucher(String voucherCode);
}
