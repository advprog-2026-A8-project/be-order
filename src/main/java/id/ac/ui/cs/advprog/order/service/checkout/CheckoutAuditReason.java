package id.ac.ui.cs.advprog.order.service.checkout;

public final class CheckoutAuditReason {
    public static final String VALIDATION_ORDER_NULL = "order_null";
    public static final String VALIDATION_MISSING_PRODUCT_OR_USER = "missing_product_or_user";
    public static final String VALIDATION_INVALID_QUANTITY = "invalid_quantity";
    public static final String VALIDATION_INSUFFICIENT_STOCK = "insufficient_stock";
    public static final String VALIDATION_INVALID_PRICE = "invalid_price";
    public static final String VALIDATION_INVALID_VOUCHER = "invalid_voucher";
    public static final String VALIDATION_WALLET_DEBIT_FAILED = "wallet_debit_failed";
    public static final String VOUCHER_USE_FAILED_AFTER_CHECKOUT = "voucher_use_failed_after_checkout";
    public static final String REFUND_INVENTORY_REDUCE_FAILED = "inventory_reduce_failed";
    public static final String REFUND_COMPENSATION_FAILED = "refund_compensation_failed";

    private CheckoutAuditReason() {
    }
}
