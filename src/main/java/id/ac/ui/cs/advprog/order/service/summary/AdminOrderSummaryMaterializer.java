package id.ac.ui.cs.advprog.order.service.summary;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.enums.OrderStatus;
import id.ac.ui.cs.advprog.order.model.AdminOrderStatusCount;
import id.ac.ui.cs.advprog.order.model.AdminOrderSummarySnapshot;
import id.ac.ui.cs.advprog.order.repository.AdminOrderStatusCountRepository;
import id.ac.ui.cs.advprog.order.repository.AdminOrderSummarySnapshotRepository;
import id.ac.ui.cs.advprog.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AdminOrderSummaryMaterializer {
    private static final int SNAPSHOT_ID = 1;

    private final OrderRepository orderRepository;
    private final AdminOrderSummarySnapshotRepository snapshotRepository;
    private final AdminOrderStatusCountRepository statusCountRepository;

    @Transactional
    public void refresh() {
        List<OrderRepository.OrderStatusCountProjection> grouped = orderRepository.countGroupedByStatus();
        Map<String, Long> statusCounts = new HashMap<>();
        for (OrderRepository.OrderStatusCountProjection row : grouped) {
            if (row.getStatus() != null) {
                statusCounts.put(row.getStatus(), row.getTotal() == null ? 0L : row.getTotal());
            }
        }

        long total = orderRepository.count();
        long active = statusCounts.getOrDefault(OrderStatus.PENDING.name(), 0L)
                + statusCounts.getOrDefault(OrderStatus.PAID.name(), 0L)
                + statusCounts.getOrDefault(OrderStatus.PURCHASED.name(), 0L)
                + statusCounts.getOrDefault(OrderStatus.SHIPPED.name(), 0L);
        long completed = statusCounts.getOrDefault(OrderStatus.COMPLETED.name(), 0L);
        long cancelled = statusCounts.getOrDefault(OrderStatus.CANCELLED.name(), 0L);

        AdminOrderSummarySnapshot snapshot = snapshotRepository.findById(SNAPSHOT_ID)
                .orElseGet(AdminOrderSummarySnapshot::new);
        snapshot.setId(SNAPSHOT_ID);
        snapshot.setTotalOrders(total);
        snapshot.setActiveOrders(active);
        snapshot.setCompletedOrders(completed);
        snapshot.setCancelledOrders(cancelled);
        snapshotRepository.save(snapshot);

        statusCountRepository.deleteAllInBatch();
        for (Map.Entry<String, Long> entry : statusCounts.entrySet()) {
            AdminOrderStatusCount count = new AdminOrderStatusCount();
            count.setStatus(entry.getKey());
            count.setTotal(entry.getValue());
            statusCountRepository.save(count);
        }
    }

    @Transactional(readOnly = true)
    public AdminOrderSummaryResponse read() {
        AdminOrderSummarySnapshot snapshot = snapshotRepository.findById(SNAPSHOT_ID)
                .orElseGet(() -> {
                    AdminOrderSummarySnapshot empty = new AdminOrderSummarySnapshot();
                    empty.setId(SNAPSHOT_ID);
                    empty.setTotalOrders(0L);
                    empty.setActiveOrders(0L);
                    empty.setCompletedOrders(0L);
                    empty.setCancelledOrders(0L);
                    return empty;
                });
        Map<String, Long> statusCounts = new HashMap<>();
        for (AdminOrderStatusCount row : statusCountRepository.findAll()) {
            statusCounts.put(row.getStatus(), row.getTotal());
        }

        return new AdminOrderSummaryResponse(
                snapshot.getTotalOrders(),
                snapshot.getActiveOrders(),
                snapshot.getCompletedOrders(),
                snapshot.getCancelledOrders(),
                statusCounts
        );
    }
}
