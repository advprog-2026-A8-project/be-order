package id.ac.ui.cs.advprog.order.service.checkout;

public final class CheckoutAuditReason {
    public static final String VALIDATION_ORDER_NULL = "order_null";
    public static final String VALIDATION_MISSING_PRODUCT_OR_USER = "missing_product_or_user";
    public static final String VALIDATION_INVALID_QUANTITY = "invalid_quantity";
    public static final String REFUND_INVENTORY_REDUCE_FAILED = "inventory_reduce_failed";

    private CheckoutAuditReason() {
    }
}
