package id.ac.ui.cs.advprog.order.service.rating;

import id.ac.ui.cs.advprog.order.enums.RatingSyncStatus;
import id.ac.ui.cs.advprog.order.model.Order;
import id.ac.ui.cs.advprog.order.model.RatingSyncTask;
import id.ac.ui.cs.advprog.order.repository.RatingSyncTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingSyncDispatcherTest {
    @Mock
    private RatingSyncTaskRepository ratingSyncTaskRepository;

    @InjectMocks
    private RatingSyncDispatcher dispatcher;

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
        order.setId("order-1");
        order.setUserId("user-1");
        order.setJastiperId("jastiper-1");
        order.setProductId("product-1");
        order.setJastiperRating(5);
        order.setProductRating(4);
    }

    @Test
    void enqueueShouldCreateNewPendingTask() {
        when(ratingSyncTaskRepository.findByOrderId("order-1")).thenReturn(Optional.empty());

        dispatcher.enqueue(order);

        ArgumentCaptor<RatingSyncTask> captor = ArgumentCaptor.forClass(RatingSyncTask.class);
        verify(ratingSyncTaskRepository).save(captor.capture());
        RatingSyncTask saved = captor.getValue();
        assertEquals("order-1", saved.getOrderId());
        assertEquals(RatingSyncStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttemptCount());
        assertNull(saved.getLastError());
    }

    @Test
    void enqueueShouldResetExistingTaskToPending() {
        RatingSyncTask existing = new RatingSyncTask();
        existing.setOrderId("order-1");
        existing.setStatus(RatingSyncStatus.FAILED);
        existing.setAttemptCount(7);
        existing.setLastError("old error");
        when(ratingSyncTaskRepository.findByOrderId("order-1")).thenReturn(Optional.of(existing));

        dispatcher.enqueue(order);

        verify(ratingSyncTaskRepository).save(existing);
        assertEquals(RatingSyncStatus.PENDING, existing.getStatus());
        assertEquals(0, existing.getAttemptCount());
        assertNull(existing.getLastError());
        assertEquals(5, existing.getJastiperRating());
        assertEquals(4, existing.getProductRating());
    }
}

