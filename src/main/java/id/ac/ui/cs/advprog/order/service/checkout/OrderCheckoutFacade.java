package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
@RequiredArgsConstructor
public class OrderCheckoutFacade {
    private final InventoryGateway inventoryGateway;
    private final WalletGateway walletGateway;
    private final OrderRepository orderRepository;
    private final CheckoutLockManager checkoutLockManager;

    public Order checkout(Order order) {
        if (order.getProductId() == null || order.getUserId() == null) {
            throw new IllegalArgumentException("Product ID dan User ID tidak boleh kosong");
        }

        ReentrantLock lock = checkoutLockManager.getLockForProduct(order.getProductId());
        lock.lock();
        try {
            InventoryResponse product = inventoryGateway.getProduct(order.getProductId());
            if (product == null || product.getProductQuantity() < order.getJumlah()) {
                throw new IllegalArgumentException("Stok barang tidak mencukupi!");
            }

            double totalPrice = product.getPrice() * order.getJumlah();
            walletGateway.debit(order.getUserId(), totalPrice);

            try {
                inventoryGateway.reduceStock(order.getProductId(), order.getJumlah());
            } catch (RuntimeException ex) {
                walletGateway.refund(order.getUserId(), totalPrice);
                throw new IllegalStateException("Gagal mengurangi stok inventory, padahal saldo sudah terpotong. Dana direfund.", ex);
            }

            order.setStatus(OrderStatus.PAID);
            order.setTotalAmount(totalPrice);
            return orderRepository.save(order);
        } finally {
            lock.unlock();
        }
    }
}
