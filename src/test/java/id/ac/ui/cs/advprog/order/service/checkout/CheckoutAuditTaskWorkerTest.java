package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CheckoutAuditTaskStatus;
import id.ac.ui.cs.advprog.order.model.CheckoutAuditTask;
import id.ac.ui.cs.advprog.order.repository.CheckoutAuditTaskRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutAuditTaskWorkerTest {
    @Mock
    private CheckoutAuditTaskRepository checkoutAuditTaskRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    private final Executor directExecutor = Runnable::run;

    @InjectMocks
    private CheckoutAuditTaskWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "checkoutAuditTaskExecutor", directExecutor);
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        ReflectionTestUtils.setField(worker, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(worker, "baseRetryDelayMs", 500L);
        lenient().doAnswer(invocation -> {
            Consumer<org.springframework.transaction.TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void processPendingTasksShouldMarkSucceeded() {
        CheckoutAuditTask task = createTask(1L, CheckoutAuditTaskStatus.PENDING, 0);
        when(checkoutAuditTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        when(checkoutAuditTaskRepository.findById(1L)).thenReturn(Optional.of(task));

        worker.processPendingTasks();

        assertEquals(CheckoutAuditTaskStatus.SUCCEEDED, task.getStatus());
        verify(checkoutAuditTaskRepository).save(task);
    }

    @Test
    void processPendingTasksShouldSkipWhenNoTaskReady() {
        when(checkoutAuditTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of());

        worker.processPendingTasks();

        verify(checkoutAuditTaskRepository, never()).findById(any());
    }

    @Test
    void processPendingTasksShouldIgnoreTaskWithoutId() {
        CheckoutAuditTask task = createTask(null, CheckoutAuditTaskStatus.PENDING, 0);
        when(checkoutAuditTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));

        worker.processPendingTasks();

        verify(checkoutAuditTaskRepository, never()).findById(any());
    }

    @Test
    void processPendingTasksShouldRetryWhenSaveFails() {
        CheckoutAuditTask task = createTask(2L, CheckoutAuditTaskStatus.PENDING, 0);
        when(checkoutAuditTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        when(checkoutAuditTaskRepository.findById(2L)).thenReturn(Optional.of(task));
        doThrow(new IllegalStateException("db down")).when(checkoutAuditTaskRepository).save(task);

        worker.processPendingTasks();

        assertEquals(CheckoutAuditTaskStatus.RETRY, task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertNotNull(task.getLastError());
    }

    @Test
    void processPendingTasksShouldMarkFailedWhenMaxRetryReached() {
        CheckoutAuditTask task = createTask(3L, CheckoutAuditTaskStatus.RETRY, 2);
        when(checkoutAuditTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        when(checkoutAuditTaskRepository.findById(3L)).thenReturn(Optional.of(task));
        doThrow(new IllegalStateException("db down")).when(checkoutAuditTaskRepository).save(task);

        worker.processPendingTasks();

        assertEquals(CheckoutAuditTaskStatus.FAILED, task.getStatus());
        assertEquals(3, task.getAttemptCount());
    }

    @Test
    void processPendingTasksShouldSkipNotReadyStatusAndFutureRetryAt() {
        CheckoutAuditTask alreadyDone = createTask(4L, CheckoutAuditTaskStatus.SUCCEEDED, 0);
        CheckoutAuditTask tooEarly = createTask(5L, CheckoutAuditTaskStatus.PENDING, 0);
        tooEarly.setNextRetryAt(LocalDateTime.now().plusMinutes(5));
        when(checkoutAuditTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(alreadyDone, tooEarly));
        when(checkoutAuditTaskRepository.findById(4L)).thenReturn(Optional.of(alreadyDone));
        when(checkoutAuditTaskRepository.findById(5L)).thenReturn(Optional.of(tooEarly));

        worker.processPendingTasks();

        verify(checkoutAuditTaskRepository, never()).save(alreadyDone);
        verify(checkoutAuditTaskRepository, never()).save(tooEarly);
    }

    private CheckoutAuditTask createTask(Long id, CheckoutAuditTaskStatus status, int attemptCount) {
        CheckoutAuditTask task = new CheckoutAuditTask();
        task.setId(id);
        task.setEventType("CHECKOUT_STARTED");
        task.setPayload("payload");
        task.setStatus(status);
        task.setAttemptCount(attemptCount);
        task.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }
}
