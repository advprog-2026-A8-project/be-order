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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
        doAnswer(invocation -> {
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
