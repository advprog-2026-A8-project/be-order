package id.ac.ui.cs.advprog.order.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class AdminOrderSummaryResponse {
    private long totalOrders;
    private long activeOrders;
    private long completedOrders;
    private long cancelledOrders;
    private Map<String, Long> statusCounts;
}
