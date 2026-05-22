package id.ac.ui.cs.advprog.order.service.checkout;

import id.ac.ui.cs.advprog.order.enums.CheckoutAuditTaskStatus;
import id.ac.ui.cs.advprog.order.model.CheckoutAuditTask;
import id.ac.ui.cs.advprog.order.repository.CheckoutAuditTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CheckoutAuditTaskDispatcher {
    private static final int MAX_PAYLOAD_LENGTH = 1500;
    private final CheckoutAuditTaskRepository checkoutAuditTaskRepository;

    @Transactional
    public void enqueue(String eventType, String payload) {
        CheckoutAuditTask task = new CheckoutAuditTask();
        task.setEventType(eventType);
        task.setPayload(trimPayload(payload));
        task.setStatus(CheckoutAuditTaskStatus.PENDING);
        task.setAttemptCount(0);
        task.setLastError(null);
        task.setNextRetryAt(LocalDateTime.now());
        checkoutAuditTaskRepository.save(task);
    }

    private String trimPayload(String payload) {
        String value = payload == null ? "" : payload;
        if (value.length() <= MAX_PAYLOAD_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_PAYLOAD_LENGTH);
    }
}
