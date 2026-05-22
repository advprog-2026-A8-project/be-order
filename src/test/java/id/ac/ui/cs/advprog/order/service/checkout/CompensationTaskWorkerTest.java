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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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

    @Mock
    private TransactionTemplate transactionTemplate;

    private final Executor directExecutor = Runnable::run;

    @InjectMocks
    private CompensationTaskWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "compensationTaskExecutor", directExecutor);
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        ReflectionTestUtils.setField(worker, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(worker, "baseRetryDelayMs", 1000L);
        lenient().doAnswer(invocation -> {
            Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void processPendingTasksShouldMarkSucceededWhenExecutionSucceeds() {
        OrderCompensationTask task = createWalletRefundTask(CompensationTaskStatus.PENDING, 0);
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        when(orderCompensationTaskRepository.findById(1L)).thenReturn(Optional.of(task));

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
        when(orderCompensationTaskRepository.findById(1L)).thenReturn(Optional.of(task));
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
        task.setId(2L);
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        when(orderCompensationTaskRepository.findById(2L)).thenReturn(Optional.of(task));
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
        when(orderCompensationTaskRepository.findById(3L)).thenReturn(Optional.of(task));

        worker.processPendingTasks();

        verify(inventoryGateway).reserveStock("product-1", 2);
        verify(voucherGateway, never()).useVoucher(anyString());
    }

    @Test
    void processPendingTasksShouldExecuteInventoryReleaseAndVoucherTasks() {
        OrderCompensationTask releaseTask = createTask(10L, CompensationTaskType.INVENTORY_RELEASE);
        releaseTask.setProductId("product-2");
        releaseTask.setQuantity(7);
        OrderCompensationTask useTask = createTask(11L, CompensationTaskType.VOUCHER_USE);
        useTask.setVoucherCode("VCODE");
        OrderCompensationTask restoreTask = createTask(12L, CompensationTaskType.VOUCHER_RESTORE);
        restoreTask.setVoucherCode("VCODE");
        restoreTask.setIdempotencyKey("idem-v-1");

        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(releaseTask, useTask, restoreTask));
        when(orderCompensationTaskRepository.findById(10L)).thenReturn(Optional.of(releaseTask));
        when(orderCompensationTaskRepository.findById(11L)).thenReturn(Optional.of(useTask));
        when(orderCompensationTaskRepository.findById(12L)).thenReturn(Optional.of(restoreTask));

        worker.processPendingTasks();

        verify(inventoryGateway).releaseStock("product-2", 7);
        verify(voucherGateway).useVoucher("VCODE");
        verify(voucherGateway).restoreVoucher("VCODE", "idem-v-1");
    }

    @Test
    void processPendingTasksShouldExecuteWalletDebitTask() {
        OrderCompensationTask task = createTask(20L, CompensationTaskType.WALLET_DEBIT);
        task.setUserId("user-x");
        task.setOrderId("order-x");
        task.setAmount(5000.0);
        task.setIdempotencyKey("idem-debit-x");
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        when(orderCompensationTaskRepository.findById(20L)).thenReturn(Optional.of(task));

        worker.processPendingTasks();

        verify(walletGateway).debit("user-x", "order-x", 5000.0, "idem-debit-x");
    }

    @Test
    void processPendingTasksShouldSkipMissingTaskAndNotReadyTask() {
        OrderCompensationTask futureTask = createTask(30L, CompensationTaskType.WALLET_REFUND);
        futureTask.setNextRetryAt(LocalDateTime.now().plusMinutes(10));
        OrderCompensationTask doneTask = createTask(31L, CompensationTaskType.WALLET_REFUND);
        doneTask.setStatus(CompensationTaskStatus.SUCCEEDED);
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(futureTask, doneTask));
        when(orderCompensationTaskRepository.findById(30L)).thenReturn(Optional.of(futureTask));
        when(orderCompensationTaskRepository.findById(31L)).thenReturn(Optional.of(doneTask));

        worker.processPendingTasks();

        verify(orderCompensationTaskRepository, never()).save(futureTask);
        verify(orderCompensationTaskRepository, never()).save(doneTask);
    }

    @Test
    void processPendingTasksShouldRetryWhenTaskPayloadInvalid() {
        OrderCompensationTask task = createTask(40L, CompensationTaskType.WALLET_REFUND);
        task.setAmount(null);
        task.setIdempotencyKey("idem");
        when(orderCompensationTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        when(orderCompensationTaskRepository.findById(40L)).thenReturn(Optional.of(task));

        worker.processPendingTasks();

        assertEquals(CompensationTaskStatus.RETRY, task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertNotNull(task.getLastError());
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
        task.setId(1L);
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
        task.setId(3L);
        task.setTaskType(CompensationTaskType.INVENTORY_RESERVE);
        task.setOrderId("order-2");
        task.setProductId("product-1");
        task.setQuantity(2);
        task.setStatus(CompensationTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }

    private OrderCompensationTask createTask(Long id, CompensationTaskType type) {
        OrderCompensationTask task = new OrderCompensationTask();
        task.setId(id);
        task.setTaskType(type);
        task.setOrderId("order-generic");
        task.setUserId("user-generic");
        task.setProductId("product-generic");
        task.setQuantity(1);
        task.setAmount(1000.0);
        task.setVoucherCode("V1");
        task.setIdempotencyKey("idem-generic");
        task.setStatus(CompensationTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }
}
