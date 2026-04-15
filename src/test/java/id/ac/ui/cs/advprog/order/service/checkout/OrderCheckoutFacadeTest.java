package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.dto.InventoryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutFacadeTest {

    @Mock
    private InventoryGateway inventoryGateway;

    @Mock
    private WalletGateway walletGateway;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CheckoutLockManager checkoutLockManager;

    @InjectMocks
    private OrderCheckoutFacade checkoutFacade;

    private Order order;
    private InventoryResponse product;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setProductId("p1");
        order.setUserId("u1");
        order.setJumlah(2);

        product = new InventoryResponse();
        product.setProductId("p1");
        product.setProductQuantity(10);
        product.setPrice(5000.0);
    }

    @Test
    void checkoutSuccessShouldSetPaidAndPersist() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order result = checkoutFacade.checkout(order);

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(walletGateway).debit("u1", 10000.0);
        verify(inventoryGateway).reduceStock("p1", 2);
    }

    @Test
    void checkoutShouldRejectWhenProductIdOrUserIdMissing() {
        order.setProductId(null);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));

        order.setProductId("p1");
        order.setUserId(null);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
    }

    @Test
    void checkoutShouldRejectWhenInventoryProductMissingOrInsufficient() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));

        product.setProductQuantity(1);
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
    }

    @Test
    void checkoutShouldPropagateWalletFailureAndNotReduceStock() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalArgumentException("wallet error")).when(walletGateway).debit("u1", 10000.0);

        assertThrows(IllegalArgumentException.class, () -> checkoutFacade.checkout(order));
        verify(inventoryGateway, never()).reduceStock(any(), any(Integer.class));
    }

    @Test
    void checkoutShouldRefundWhenStockReductionFailsAfterDebit() {
        when(checkoutLockManager.getLockForProduct("p1")).thenReturn(new ReentrantLock());
        when(inventoryGateway.getProduct("p1")).thenReturn(product);
        doThrow(new IllegalStateException("inventory down")).when(inventoryGateway).reduceStock("p1", 2);

        assertThrows(IllegalStateException.class, () -> checkoutFacade.checkout(order));
        verify(walletGateway).refund("u1", 10000.0);
    }
}
