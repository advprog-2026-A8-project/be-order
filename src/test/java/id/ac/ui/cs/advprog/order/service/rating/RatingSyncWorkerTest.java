package id.ac.ui.cs.advprog.order.service.rating;

import id.ac.ui.cs.advprog.order.enums.RatingSyncStatus;
import id.ac.ui.cs.advprog.order.model.RatingSyncTask;
import id.ac.ui.cs.advprog.order.repository.RatingSyncTaskRepository;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingSyncWorkerTest {
    @Mock
    private RatingSyncTaskRepository ratingSyncTaskRepository;

    @Mock
    private ProfileGateway profileGateway;

    @InjectMocks
    private RatingSyncWorker worker;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(worker, "batchSize", 20);
        ReflectionTestUtils.setField(worker, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(worker, "baseRetryDelayMs", 1000L);
    }

    @Test
    void processPendingTasksShouldMarkSucceeded() {
        RatingSyncTask task = createTask(RatingSyncStatus.PENDING, 0);
        when(ratingSyncTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));

        worker.processPendingTasks();

        verify(profileGateway).submitRating("order-1", "user-1", "jastiper-1", "product-1", 5, 4);
        assertEquals(RatingSyncStatus.SUCCEEDED, task.getStatus());
        assertEquals(0, task.getAttemptCount());
        verify(ratingSyncTaskRepository).save(task);
    }

    @Test
    void processPendingTasksShouldScheduleRetryWhenFailed() {
        RatingSyncTask task = createTask(RatingSyncStatus.PENDING, 0);
        when(ratingSyncTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        doThrow(new IllegalStateException("profile unavailable"))
                .when(profileGateway).submitRating(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());

        worker.processPendingTasks();

        assertEquals(RatingSyncStatus.RETRY, task.getStatus());
        assertEquals(1, task.getAttemptCount());
        assertNotNull(task.getLastError());
        assertTrue(task.getNextRetryAt().isAfter(LocalDateTime.now().minusSeconds(1)));
        verify(ratingSyncTaskRepository).save(task);
    }

    @Test
    void processPendingTasksShouldMarkFailedWhenMaxAttemptReached() {
        RatingSyncTask task = createTask(RatingSyncStatus.RETRY, 2);
        when(ratingSyncTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of(task));
        doThrow(new IllegalStateException("profile still unavailable"))
                .when(profileGateway).submitRating(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());

        worker.processPendingTasks();

        assertEquals(RatingSyncStatus.FAILED, task.getStatus());
        assertEquals(3, task.getAttemptCount());
        verify(ratingSyncTaskRepository).save(task);
    }

    @Test
    void processPendingTasksShouldSkipWhenNoTaskReady() {
        when(ratingSyncTaskRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                any(), any(LocalDateTime.class), any())).thenReturn(List.of());

        worker.processPendingTasks();

        verify(profileGateway, never()).submitRating(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
    }

    private RatingSyncTask createTask(RatingSyncStatus status, int attemptCount) {
        RatingSyncTask task = new RatingSyncTask();
        task.setOrderId("order-1");
        task.setUserId("user-1");
        task.setJastiperId("jastiper-1");
        task.setProductId("product-1");
        task.setJastiperRating(5);
        task.setProductRating(4);
        task.setStatus(status);
        task.setAttemptCount(attemptCount);
        task.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        return task;
    }
}
