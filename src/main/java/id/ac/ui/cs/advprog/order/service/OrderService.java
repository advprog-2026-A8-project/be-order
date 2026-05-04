package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.model.Order;
import java.util.List;

public interface OrderService {
    Order createOrder(Order order);
    Order createOrder(Order order, String idempotencyKey);
    List<Order> findAllOrders();
    Order findOrderById(String id);
    Order updateOrderStatus(String id, String status);
    Order cancelOrderByJastiper(String id, String jastiperId);
    List<Order> findTitiperActiveOrders(String userId);
    List<Order> findTitiperOrderHistory(String userId);
    List<Order> findJastiperTodoOrders(String jastiperId);
    List<Order> findJastiperProcessingOrders(String jastiperId);
    List<Order> findJastiperCompletedOrders(String jastiperId);
    List<Order> findAdminActiveOrders();
    List<Order> findAdminOrdersByStatus(String status);
    AdminOrderSummaryResponse getAdminOrderSummary();
    Order submitOrderRating(String orderId, String userId, int jastiperRating, int productRating);
}
