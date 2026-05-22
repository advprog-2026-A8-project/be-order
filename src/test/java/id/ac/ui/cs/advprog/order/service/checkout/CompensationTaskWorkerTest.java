package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CompensationTaskStatus;
import id.ac.ui.cs.advprog.order.enums.CompensationTaskType;
import id.ac.ui.cs.advprog.order.model.OrderCompensationTask;
import id.ac.ui.cs.advprog.order.repository.OrderCompensationTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompensationTaskWorkerTest {
    @Mock
    private OrderCompensationTaskRepository orderCompensationTaskRepository;

    @Mock
    private WalletGateway walletGateway;

    @Mock
    private InventoryGateway inventoryGateway;

    @Mock
    private VoucherGateway voucherGateway;

    @InjectMocks
    private CompensationTaskWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        ReflectionTestUtils.setField(worker, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(worker, "baseRetryDelayMs", 1000L);
    }

    @Test
    void processPendingTasksShouldMarkSucceededWhenExecutionSucceeds() {
        OrderCompensationTask task = createWalletRefundTask(CompensationTaskStatus.PENDING, 0);
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));

        worker.processPendingTasks();

        verify(walletGateway).refund("user-1", "order-1", 10000.0, "idem-refund-1");
        assertEquals(CompensationTaskStatus.SUCCEEDED, task.getStatus());
        assertEquals(0, task.getAttemptCount());
        verify(orderCompensationTaskRepository).save(task);
    }

    @Test
    void processPendingTasksShouldRetryWhenExecutionFails() {
        OrderCompensationTask task = createWalletRefundTask(CompensationTaskStatus.PENDING, 0);
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        doThrow(new IllegalStateException("wallet unavailable"))
                .when(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());

        worker.processPendingTasks();

        assertEquals(CompensationTaskStatus.RETRY, task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertNotNull(task.getLastError());
        assertTrue(task.getNextRetryAt().isAfter(LocalDateTime.now().minusSeconds(1)));
        verify(orderCompensationTaskRepository).save(task);
    }

    @Test
    void processPendingTasksShouldMarkFailedWhenMaxAttemptReached() {
        OrderCompensationTask task = createWalletRefundTask(CompensationTaskStatus.RETRY, 2);
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        doThrow(new IllegalStateException("wallet still unavailable"))
                .when(walletGateway).refund(anyString(), anyString(), anyDouble(), anyString());

        worker.processPendingTasks();

        assertEquals(CompensationTaskStatus.FAILED, task.getStatus());
        assertEquals(3, task.getAttemptCount());
        verify(orderCompensationTaskRepository).save(task);
    }

    @Test
    void processPendingTasksShouldExecuteInventoryReserveTask() {
        OrderCompensationTask task = createInventoryReserveTask();
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));

        worker.processPendingTasks();

        verify(inventoryGateway).reserveStock("product-1", 2);
        verify(voucherGateway, never()).useVoucher(anyString());
    }

    @Test
    void processPendingTasksShouldSkipWhenNoTaskReady() {
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of());

        worker.processPendingTasks();

        verify(walletGateway, never()).refund(anyString(), anyString(), anyDouble(), anyString());
        verify(inventoryGateway, never()).reserveStock(anyString(), anyInt());
    }

    private OrderCompensationTask createWalletRefundTask(CompensationTaskStatus status, int attempts) {
        OrderCompensationTask task = new OrderCompensationTask();
        task.setTaskType(CompensationTaskType.WALLET_REFUND);
        task.setOrderId("order-1");
        task.setUserId("user-1");
        task.setAmount(10000.0);
        task.setIdempotencyKey("idem-refund-1");
        task.setStatus(status);
        task.setAttemptCount(attempts);
        task.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }

    private OrderCompensationTask createInventoryReserveTask() {
        OrderCompensationTask task = new OrderCompensationTask();
        task.setTaskType(CompensationTaskType.INVENTORY_RESERVE);
        task.setOrderId("order-2");
        task.setProductId("product-1");
        task.setQuantity(2);
        task.setStatus(CompensationTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }
}
