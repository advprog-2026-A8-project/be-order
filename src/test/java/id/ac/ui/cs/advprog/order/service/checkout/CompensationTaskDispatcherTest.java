package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CompensationTaskStatus;
import id.ac.ui.cs.advprog.order.enums.CompensationTaskType;
import id.ac.ui.cs.advprog.order.model.OrderCompensationTask;
import id.ac.ui.cs.advprog.order.repository.OrderCompensationTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationTaskDispatcherTest {
    @Mock
    private OrderCompensationTaskRepository orderCompensationTaskRepository;

    @InjectMocks
    private CompensationTaskDispatcher dispatcher;

    @Test
    void enqueueWalletRefundShouldCreatePendingTask() {
        when(orderCompensationTaskRepository.findByTaskKey("WALLET_REFUND:order-1:idem-refund-1"))
                .thenReturn(Optional.empty());

        dispatcher.enqueueWalletRefund("order-1", "user-1", 15000.0, "idem-refund-1");

        ArgumentCaptor<OrderCompensationTask> captor = ArgumentCaptor.forClass(OrderCompensationTask.class);
        verify(orderCompensationTaskRepository).save(captor.capture());
        OrderCompensationTask saved = captor.getValue();
        assertEquals("WALLET_REFUND:order-1:idem-refund-1", saved.getTaskKey());
        assertEquals(CompensationTaskType.WALLET_REFUND, saved.getTaskType());
        assertEquals("order-1", saved.getOrderId());
        assertEquals("user-1", saved.getUserId());
        assertEquals(15000.0, saved.getAmount());
        assertEquals("idem-refund-1", saved.getIdempotencyKey());
        assertEquals(CompensationTaskStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttemptCount());
        assertNull(saved.getLastError());
        assertNotNull(saved.getNextRetryAt());
    }

    @Test
    void enqueueInventoryReleaseShouldResetExistingTask() {
        OrderCompensationTask existing = new OrderCompensationTask();
        existing.setTaskKey("INVENTORY_RELEASE:order-2:product-1:3");
        existing.setTaskType(CompensationTaskType.INVENTORY_RELEASE);
        existing.setStatus(CompensationTaskStatus.FAILED);
        existing.setAttemptCount(5);
        existing.setLastError("old error");

        when(orderCompensationTaskRepository.findByTaskKey("INVENTORY_RELEASE:order-2:product-1:3"))
                .thenReturn(Optional.of(existing));

        dispatcher.enqueueInventoryRelease("order-2", "product-1", 3);

        verify(orderCompensationTaskRepository).save(existing);
        assertEquals(CompensationTaskType.INVENTORY_RELEASE, existing.getTaskType());
        assertEquals("order-2", existing.getOrderId());
        assertEquals("product-1", existing.getProductId());
        assertEquals(3, existing.getQuantity());
        assertEquals(CompensationTaskStatus.PENDING, existing.getStatus());
        assertEquals(0, existing.getAttemptCount());
        assertNull(existing.getLastError());
        assertNotNull(existing.getNextRetryAt());
    }

    @Test
    void enqueueVoucherUseShouldHandleNullInputsInTaskKey() {
        when(orderCompensationTaskRepository.findByTaskKey("VOUCHER_USE:unknown-order:none"))
                .thenReturn(Optional.empty());

        dispatcher.enqueueVoucherUse(null, null);

        ArgumentCaptor<OrderCompensationTask> captor = ArgumentCaptor.forClass(OrderCompensationTask.class);
        verify(orderCompensationTaskRepository).save(captor.capture());
        OrderCompensationTask saved = captor.getValue();
        assertEquals("VOUCHER_USE:unknown-order:none", saved.getTaskKey());
        assertEquals(CompensationTaskType.VOUCHER_USE, saved.getTaskType());
    }

    @Test
    void enqueueWalletDebitInventoryReserveAndVoucherRestoreShouldSetTaskTypeProperly() {
        when(orderCompensationTaskRepository.findByTaskKey("WALLET_DEBIT:order-3:idem-debit")).thenReturn(Optional.empty());
        when(orderCompensationTaskRepository.findByTaskKey("INVENTORY_RESERVE:order-3:prod-9:4")).thenReturn(Optional.empty());
        when(orderCompensationTaskRepository.findByTaskKey("VOUCHER_RESTORE:order-3:idem-restore")).thenReturn(Optional.empty());

        dispatcher.enqueueWalletDebit("order-3", "user-9", 12000.0, "idem-debit");
        dispatcher.enqueueInventoryReserve("order-3", "prod-9", 4);
        dispatcher.enqueueVoucherRestore("order-3", "VOUCHER1", "idem-restore");

        verify(orderCompensationTaskRepository, times(3)).save(any(OrderCompensationTask.class));
    }

    @Test
    void createTaskKeyShouldTrimInputs() {
        String key = (String) ReflectionTestUtils.invokeMethod(
                dispatcher,
                "createTaskKey",
                CompensationTaskType.WALLET_REFUND,
                " order-9 ",
                " idem-9 "
        );
        assertEquals("WALLET_REFUND:order-9:idem-9", key);
    }
}
