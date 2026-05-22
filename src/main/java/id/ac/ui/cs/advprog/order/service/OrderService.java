package id.ac.ui.cs.advprog.order.service;

import id.ac.ui.cs.advprog.order.dto.AdminOrderSummaryResponse;
import id.ac.ui.cs.advprog.order.model.Order;
import org.springframework.data.domain.Page;

import java.util.List;

public interface OrderService {
    Order createOrder(Order order);
    Order createOrder(Order order, String idempotencyKey);
    Order createOrder(Order order, String idempotencyKey, String authorizationHeader);
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
    Page<Order> findAdminActiveOrdersPaged(int page, int size);
    Page<Order> findAdminActiveOrdersPaged(int page, int size, String sortBy, String direction);
    List<Order> findAdminOrdersByStatus(String status);
    Page<Order> findAdminOrdersByStatusPaged(String status, int page, int size);
    Page<Order> findAdminOrdersByStatusPaged(String status, int page, int size, String sortBy, String direction);
    AdminOrderSummaryResponse getAdminOrderSummary();
    Order submitOrderRating(String orderId, String userId, int jastiperRating, int productRating);
}
