package id.ac.ui.cs.advprog.order.model;

import id.ac.ui.cs.advprog.order.enums.CheckoutAuditTaskStatus;
import id.ac.ui.cs.advprog.order.enums.CompensationTaskStatus;
import id.ac.ui.cs.advprog.order.enums.RatingSyncStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncTaskModelTest {

    @Test
    void ratingSyncTaskOnCreateShouldFillDefaultsAndTouchShouldUpdateTimestamp() {
        RatingSyncTask task = new RatingSyncTask();
        task.onCreate();

        assertEquals(RatingSyncStatus.PENDING, task.getStatus());
        assertEquals(0, task.getAttemptCount());
        assertNotNull(task.getNextRetryAt());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());

        LocalDateTime previousUpdatedAt = LocalDateTime.now().minusHours(1);
        task.setUpdatedAt(previousUpdatedAt);
        task.touch();

        assertTrue(task.getUpdatedAt().isAfter(previousUpdatedAt));
    }

    @Test
    void checkoutAuditTaskOnCreateAndOnUpdateShouldMaintainRetryLifecycle() {
        CheckoutAuditTask task = new CheckoutAuditTask();
        task.onCreate();

        assertEquals(CheckoutAuditTaskStatus.PENDING, task.getStatus());
        assertEquals(0, task.getAttemptCount());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());

        LocalDateTime previousUpdatedAt = task.getUpdatedAt();
        task.onUpdate();
        assertTrue(!task.getUpdatedAt().isBefore(previousUpdatedAt));
    }

    @Test
    void orderCompensationTaskOnCreateShouldKeepPresetValuesButInitializeMissingFields() {
        OrderCompensationTask task = new OrderCompensationTask();
        task.setStatus(CompensationTaskStatus.RETRY);
        task.setAttemptCount(3);
        LocalDateTime scheduled = LocalDateTime.now().plusMinutes(10);
        task.setNextRetryAt(scheduled);

        task.onCreate();

        assertEquals(CompensationTaskStatus.RETRY, task.getStatus());
        assertEquals(3, task.getAttemptCount());
        assertEquals(scheduled, task.getNextRetryAt());
        assertNotNull(task.getCreatedAt());
        assertNotNull(task.getUpdatedAt());
    }

    @Test
    void adminOrderSummarySnapshotTouchShouldAssignIdWhenMissing() {
        AdminOrderSummarySnapshot snapshot = new AdminOrderSummarySnapshot();
        snapshot.touch();

        assertEquals(1, snapshot.getId());
        assertNotNull(snapshot.getUpdatedAt());
    }

    @Test
    void adminOrderSummarySnapshotTouchShouldPreserveExistingId() {
        AdminOrderSummarySnapshot snapshot = new AdminOrderSummarySnapshot();
        snapshot.setId(9);
        snapshot.touch();

        assertEquals(9, snapshot.getId());
        assertNotNull(snapshot.getUpdatedAt());
    }
}
