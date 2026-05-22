package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CheckoutAuditTaskStatus;
import id.ac.ui.cs.advprog.order.model.CheckoutAuditTask;
import id.ac.ui.cs.advprog.order.repository.CheckoutAuditTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CheckoutAuditTaskDispatcherTest {
    @Mock
    private CheckoutAuditTaskRepository checkoutAuditTaskRepository;

    @InjectMocks
    private CheckoutAuditTaskDispatcher dispatcher;

    @Test
    void enqueueShouldPersistPendingAuditTask() {
        dispatcher.enqueue("CHECKOUT_STARTED", "productId=p1,userId=u1");

        ArgumentCaptor<CheckoutAuditTask> captor = ArgumentCaptor.forClass(CheckoutAuditTask.class);
        verify(checkoutAuditTaskRepository).save(captor.capture());
        CheckoutAuditTask saved = captor.getValue();
        assertEquals("CHECKOUT_STARTED", saved.getEventType());
        assertEquals("productId=p1,userId=u1", saved.getPayload());
        assertEquals(CheckoutAuditTaskStatus.PENDING, saved.getStatus());
        assertEquals(0, saved.getAttemptCount());
        assertNotNull(saved.getNextRetryAt());
    }

    @Test
    void enqueueShouldTrimLongPayloadAndHandleNullPayload() {
        String longPayload = "x".repeat(1700);

        dispatcher.enqueue("LONG_PAYLOAD", longPayload);
        dispatcher.enqueue("NULL_PAYLOAD", null);

        ArgumentCaptor<CheckoutAuditTask> captor = ArgumentCaptor.forClass(CheckoutAuditTask.class);
        verify(checkoutAuditTaskRepository, times(2)).save(captor.capture());
        CheckoutAuditTask first = captor.getAllValues().get(0);
        CheckoutAuditTask second = captor.getAllValues().get(1);

        assertEquals(1500, first.getPayload().length());
        assertEquals("", second.getPayload());
    }
}
