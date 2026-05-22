package id.ac.ui.cs.advprog.order.service.summary;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.model.AdminOrderStatusCount;
import id.ac.ui.cs.advprog.order.model.AdminOrderSummarySnapshot;
import id.ac.ui.cs.advprog.order.repository.AdminOrderStatusCountRepository;
import id.ac.ui.cs.advprog.order.repository.AdminOrderSummarySnapshotRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrderSummaryMaterializerTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private AdminOrderSummarySnapshotRepository snapshotRepository;

    @Mock
    private AdminOrderStatusCountRepository statusCountRepository;

    @InjectMocks
    private AdminOrderSummaryMaterializer materializer;

    @Test
    void refreshShouldStoreSnapshotAndStatusCounts() {
        OrderRepository.OrderStatusCountProjection paid = projection("PAID", 3L);
        OrderRepository.OrderStatusCountProjection completed = projection("COMPLETED", 2L);
        when(orderRepository.countGroupedByStatus()).thenReturn(List.of(paid, completed));
        when(orderRepository.count()).thenReturn(5L);
        when(snapshotRepository.findById(1)).thenReturn(Optional.empty());

        materializer.refresh();

        verify(snapshotRepository).save(any(AdminOrderSummarySnapshot.class));
        verify(statusCountRepository).deleteAllInBatch();
        verify(statusCountRepository, times(2)).save(any(AdminOrderStatusCount.class));
    }

    @Test
    void readShouldReturnMaterializedSummary() {
        AdminOrderSummarySnapshot snapshot = new AdminOrderSummarySnapshot();
        snapshot.setId(1);
        snapshot.setTotalOrders(10L);
        snapshot.setActiveOrders(4L);
        snapshot.setCompletedOrders(3L);
        snapshot.setCancelledOrders(2L);
        AdminOrderStatusCount paid = new AdminOrderStatusCount();
        paid.setStatus("PAID");
        paid.setTotal(4L);
        when(snapshotRepository.findById(1)).thenReturn(Optional.of(snapshot));
        when(statusCountRepository.findAll()).thenReturn(List.of(paid));

        AdminOrderSummaryResponse response = materializer.read();

        assertEquals(10L, response.getTotalOrders());
        assertEquals(4L, response.getActiveOrders());
        assertEquals(3L, response.getCompletedOrders());
        assertEquals(2L, response.getCancelledOrders());
        assertEquals(4L, response.getStatusCounts().get("PAID"));
    }

    @Test
    void refreshShouldIgnoreRowsWithNullStatusAndNullTotal() {
        OrderRepository.OrderStatusCountProjection nullStatus = projection(null, 10L);
        OrderRepository.OrderStatusCountProjection nullTotal = projection("PENDING", null);
        when(orderRepository.countGroupedByStatus()).thenReturn(List.of(nullStatus, nullTotal));
        when(orderRepository.count()).thenReturn(1L);
        when(snapshotRepository.findById(1)).thenReturn(Optional.of(new AdminOrderSummarySnapshot()));

        materializer.refresh();

        verify(snapshotRepository).save(any(AdminOrderSummarySnapshot.class));
        verify(statusCountRepository, times(1)).save(any(AdminOrderStatusCount.class));
    }

    @Test
    void readShouldReturnEmptyWhenNoSnapshotExists() {
        when(snapshotRepository.findById(1)).thenReturn(Optional.empty());
        when(statusCountRepository.findAll()).thenReturn(List.of());

        AdminOrderSummaryResponse response = materializer.read();

        assertEquals(0L, response.getTotalOrders());
        assertEquals(0L, response.getActiveOrders());
        assertEquals(0L, response.getCompletedOrders());
        assertEquals(0L, response.getCancelledOrders());
        assertEquals(Map.of(), response.getStatusCounts());
    }

    private OrderRepository.OrderStatusCountProjection projection(String status, Long total) {
        return new OrderRepository.OrderStatusCountProjection() {
            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }
}
